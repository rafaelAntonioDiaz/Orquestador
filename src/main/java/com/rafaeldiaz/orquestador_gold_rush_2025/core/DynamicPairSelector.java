package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 🧠 CEREBRO "ADRENALINA" (VOLATILITY HUNTER)
 * Estrategia: El arbitraje vive en el caos.
 * Buscamos las monedas que más se están moviendo AHORA MISMO y enfocamos el escáner ahí.
 */
public class DynamicPairSelector {

    private final ExchangeConnector connector;
    private final MarketListener marketListener; // Referencia para actualizar objetivos
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 💰 UMBRAL DE CAPITAL
    private static final double MIN_CAPITAL_FOR_KINGS = 1000.0;

    // 🌌 UNIVERSO DE CAZA (Monedas con potencial de arbitraje y redes baratas/medias)
    // No incluimos BTC/ETH aquí si somos "pobres", pero sí todo lo demás.
    private static final List<String> HUNTING_GROUNDS = List.of(
            "SOLUSDT", "AVAXUSDT", "XRPUSDT", "PEPEUSDT", "DOGEUSDT",
            "ADAUSDT", "MATICUSDT", "LINKUSDT", "LTCUSDT", "DOTUSDT",
            "SHIBUSDT", "TRXUSDT", "ATOMUSDT", "NEARUSDT", "SUIUSDT"
    );

    // Memoria de precios anteriores para calcular "Aceleración" (Cambio en corto plazo)
    private final Map<String, Double> lastPrices = new HashMap<>();

    public DynamicPairSelector(ExchangeConnector connector, MarketListener marketListener) {
        this.connector = connector;
        this.marketListener = marketListener;
    }

    public void start() {
        BotLogger.info("🩺 INICIANDO MONITOR DE ADRENALINA (PULSO DEL MERCADO)...");
        // Evaluamos cada 60 segundos. El mercado cambia rápido.
        scheduler.scheduleAtFixedRate(this::detectAdrenaline, 0, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void detectAdrenaline() {
        try {
            // 1. CHEQUEO DE CAPITAL (Safety First)
            double totalCapital = connector.fetchBalance("bybit_sub1", "USDT");
            if (totalCapital < 10.0) totalCapital = 300.0; // Simulación

            BotLogger.info("🩺 TOMANDO PULSO DEL MERCADO... (Cap: $" + totalCapital + ")");

            List<AdrenalineScore> opportunities = new ArrayList<>();

            for (String pair : HUNTING_GROUNDS) {
                try {
                    // Obtenemos precio actual
                    double currentPrice = connector.fetchPrice("bybit_sub1", pair);
                    if (currentPrice <= 0) continue;

                    // 2. CÁLCULO DE ACELERACIÓN (Cambio vs hace 60 seg)
                    double prevPrice = lastPrices.getOrDefault(pair, currentPrice);
                    double changePercent = Math.abs((currentPrice - prevPrice) / prevPrice) * 100.0;

                    // Actualizamos memoria
                    lastPrices.put(pair, currentPrice);

                    // 3. SCORE DE "CALLE" (Eficiencia de Capital)
                    // Preferimos monedas baratas (mayor volatilidad nominal y fees bajos)
                    double streetScore = 1.0;
                    if (pair.contains("PEPE") || pair.contains("SHIB")) streetScore = 2.0; // Memes se mueven más
                    if (pair.contains("SOL") || pair.contains("SUI")) streetScore = 1.5;   // L1 rápidas

                    // 4. SCORE TOTAL (Adrenalina)
                    // Adrenalina = Cuánto se movió * Qué tan buena es la moneda
                    // Si se movió 0%, score es 0. Si se movió 1% en 1 min, es ENORME.
                    double adrenaline = changePercent * streetScore;

                    // Loguear solo si hay movimiento relevante (>0.1% en 1 min)
                    if (changePercent > 0.1) {
                        BotLogger.info("⚡ MOVIMIENTO DETECTADO en " + pair + ": " + String.format("%.2f%%", changePercent));
                    }

                    opportunities.add(new AdrenalineScore(pair, adrenaline));

                } catch (Exception e) {
                    // Ignorar fallos puntuales
                }
            }

            // 5. SELECCIÓN DE LOS "TOP MOVERS"
            // Ordenamos por adrenalina pura
            opportunities.sort(Comparator.comparingDouble(AdrenalineScore::score).reversed());

            // Tomamos los Top 3 (Los 3 activos más calientes del minuto)
            List<String> topTargets = opportunities.stream()
                    .limit(3)
                    .map(AdrenalineScore::pair)
                    .collect(Collectors.toList());

            // Si el mercado está muerto (nadie se mueve), usamos un default seguro
            if (topTargets.isEmpty() || opportunities.get(0).score < 0.05) {
                BotLogger.info("😴 Mercado dormido. Manteniendo guardia estándar: [SOLUSDT, PEPEUSDT, AVAXUSDT]");
                marketListener.updateTargets(List.of("SOLUSDT", "PEPEUSDT", "AVAXUSDT"));
            } else {
                BotLogger.info("🔥 ADRENALINA ALTA EN: " + topTargets + ". ¡CAMBIANDO OBJETIVOS!");
                // INYECCIÓN DE OBJETIVOS AL LISTENER
                marketListener.updateTargets(topTargets);
            }

        } catch (Exception e) {
            BotLogger.error("Error en Monitor Adrenalina: " + e.getMessage());
        }
    }

    private record AdrenalineScore(String pair, double score) {}
}