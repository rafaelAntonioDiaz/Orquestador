package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.connect.MarketStreamer;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Epic 3: Selección Dinámica de Pares.
 * Escanea el mercado y suscribe al bot a los pares con mejor ATR (Volatilidad).
 */
public class DynamicPairSelector {

    private final ExchangeConnector connector;
    private final MarketStreamer streamer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Candidatos definidos en Task 3.1.1
    private static final List<String> CANDIDATES = List.of(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "AVAXUSDT",
            "DOGEUSDT", "LINKUSDT", "PEPEUSDT" // PEPE para alta volatilidad
    );

    // Mantenemos registro de los activos actuales para no re-suscribir
    private List<String> currentActivePairs = new ArrayList<>();

    public DynamicPairSelector(ExchangeConnector connector, MarketStreamer streamer) {
        this.connector = connector;
        this.streamer = streamer;
    }

    public void start() {
        BotLogger.info("📡 Iniciando Selector Dinámico de Pares...");
        // Evaluar cada 10 minutos (Task 3.1.1)
        scheduler.scheduleAtFixedRate(this::evaluatePairs, 0, 10, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void evaluatePairs() {
        try {
            BotLogger.info("🔎 Escaneando mercado para encontrar mejores oportunidades...");
            List<PairScore> scores = new ArrayList<>();

            // Task 3.1.2: Calcular Score = ATR
            for (String pair : CANDIDATES) {
                try {
                    // Pedimos 14 velas de 1 hora ("60")
                    // Necesitamos exponer fetchCandles en Connector primero
                    double[][] candles = connector.fetchCandles("bybit_sub1", pair, "60", 14);

                    if (candles.length < 14) continue;

                    double atr = calculateATR(candles);
                    double price = connector.fetchPrice("bybit_sub1", pair);

                    // ATR % relativo al precio (para comparar manzanas con peras)
                    double volatilityPercent = (atr / price) * 100;

                    scores.add(new PairScore(pair, volatilityPercent));

                } catch (Exception e) {
                    BotLogger.warn("No se pudo evaluar " + pair + ": " + e.getMessage());
                }
            }

            // Ordenar por volatilidad (Mayor a menor)
            scores.sort(Comparator.comparingDouble(PairScore::score).reversed());

            // Seleccionar Top 3 (Task 3.1.2 dice Top 6, empecemos conservadores con 3)
            List<String> topPicks = new ArrayList<>();
            for (int i = 0; i < Math.min(3, scores.size()); i++) {
                topPicks.add(scores.get(i).pair);
            }

            updateSubscriptions(topPicks);

        } catch (Exception e) {
            BotLogger.error("Error en Selector Dinámico: " + e.getMessage());
        }
    }

    private void updateSubscriptions(List<String> newPicks) {
        // Lógica simple: Si no está suscrito, suscribir.
        // (En una versión avanzada, desuscribiríamos los viejos fríos)

        for (String pair : newPicks) {
            if (!currentActivePairs.contains(pair)) {
                BotLogger.info("🔥 HOT PAIR DETECTADO: " + pair + ". Suscribiendo...");
                streamer.subscribe(pair);

                // Suscribir también a la pata cripto (ej. SOLBTC) si aplica
                // Esto requiere lógica extra de mapeo, por ahora simplificado a USDT
                currentActivePairs.add(pair);
            }
        }

        BotLogger.info("📊 Pares Activos: " + currentActivePairs);
    }

    /**
     * Cálculo simplificado de ATR (Average True Range)
     */
    private double calculateATR(double[][] candles) {
        double sumTr = 0.0;
        // candles[i] = [High, Low, Close]
        // TR = Max(High-Low, Abs(High-ClosePrev), Abs(Low-ClosePrev))
        // Simplificación: Usamos High - Low promedio para este MVP
        for (double[] candle : candles) {
            sumTr += (candle[0] - candle[1]);
        }
        return sumTr / candles.length;
    }

    private record PairScore(String pair, double score) {}
}