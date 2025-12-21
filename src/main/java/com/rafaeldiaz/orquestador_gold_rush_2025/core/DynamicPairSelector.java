package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 🧠 CEREBRO "VOLATILITY HUNTER" (ATR BASED)
 * Estrategia: Detecta la turbulencia real del mercado usando Velas Japonesas.
 * No espera cambios de precio; mide la amplitud del movimiento (High-Low).
 */
public class DynamicPairSelector {

    private final ExchangeConnector connector;
    private final MarketListener marketListener;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 🌌 UNIVERSO DE CAZA
    private static final List<String> HUNTING_GROUNDS = List.of(
            "SOLUSDT", "AVAXUSDT", "XRPUSDT", "PEPEUSDT", "DOGEUSDT",
            "ADAUSDT", "MATICUSDT", "LINKUSDT", "LTCUSDT", "DOTUSDT",
            "SHIBUSDT", "TRXUSDT", "ATOMUSDT", "NEARUSDT", "SUIUSDT"
    );

    public DynamicPairSelector(ExchangeConnector connector, MarketListener marketListener) {
        this.connector = connector;
        this.marketListener = marketListener;
    }

    public void start() {
        BotLogger.info("🩺 INICIANDO RADAR DE VOLATILIDAD (ATR)...");
        // Escaneamos cada 60 segundos para re-calibrar los objetivos
        scheduler.scheduleAtFixedRate(this::scanVolatility, 0, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void scanVolatility() {
        try {
            BotLogger.info("⚡ ESCANEANDO VOLATILIDAD DEL MERCADO (ATR 1m)...");
            List<VolatilityScore> opportunities = new ArrayList<>();

            for (String pair : HUNTING_GROUNDS) {
                try {
                    // 1. OBTENER VELAS (Últimos 5 minutos)
                    // Pedimos velas de 1 minuto (intervalo "1")
                    List<double[]> candles = connector.fetchCandles("bybit_sub1", pair, "1", 5);

                    if (candles == null || candles.isEmpty()) continue;

                    // 2. CALCULAR ATR (Average True Range) SIMPLIFICADO
                    // Promediamos el tamaño del cuerpo de la vela (High - Low)
                    double totalRange = 0.0;
                    double lastClose = 0.0;

                    for (double[] candle : candles) {
                        // candle[0]=High, candle[1]=Low, candle[2]=Close
                        double high = candle[0];
                        double low = candle[1];
                        totalRange += (high - low);
                        lastClose = candle[2];
                    }

                    double averageRange = totalRange / candles.size();

                    // 3. NORMALIZAR VOLATILIDAD (% del precio)
                    // Esto permite comparar PEPE (0.00001) con SOL (150.0) justamente.
                    if (lastClose == 0) continue;
                    double volatilityPercent = (averageRange / lastClose) * 100.0;

                    // 4. FACTOR "CALLE" (Street Score)
                    // Le damos un empujoncito a nuestras favoritas si hay empate técnico
                    double streetScore = 1.0;
                    if (pair.contains("PEPE") || pair.contains("SHIB")) streetScore = 1.2;
                    if (pair.contains("SOL") || pair.contains("SUI")) streetScore = 1.1;

                    double finalScore = volatilityPercent * streetScore;

                    // Logueamos solo si se mueve decentemente (>0.05% por minuto es ruido)
                    if (volatilityPercent > 0.05) {
                        opportunities.add(new VolatilityScore(pair, finalScore, volatilityPercent));
                    }

                } catch (Exception e) {
                    // Ignorar error puntual en un par
                }
            }

            // 5. SELECCIÓN DE CAMPEONES
            opportunities.sort(Comparator.comparingDouble(VolatilityScore::score).reversed());

            if (opportunities.isEmpty()) {
                BotLogger.info("😴 Mercado plano (ATR Bajo). Manteniendo guardia estándar.");
                marketListener.updateTargets(List.of("SOLUSDT", "AVAXUSDT", "PEPEUSDT"));
            } else {
                // Tomamos Top 3
                List<String> topTargets = opportunities.stream()
                        .limit(3)
                        .map(VolatilityScore::pair)
                        .collect(Collectors.toList());

                // Mostramos el ranking para que sepas quién manda
                StringBuilder ranking = new StringBuilder("🔥 TOP VOLATILIDAD: ");
                for (int i = 0; i < Math.min(3, opportunities.size()); i++) {
                    VolatilityScore op = opportunities.get(i);
                    ranking.append(String.format("%s(%.2f%%) ", op.pair, op.rawVol));
                }
                BotLogger.info(ranking.toString());

                // INYECCIÓN
                marketListener.updateTargets(topTargets);
            }

        } catch (Exception e) {
            BotLogger.error("Error en Radar ATR: " + e.getMessage());
        }
    }

    private record VolatilityScore(String pair, double score, double rawVol) {}
}