package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * RADAR DE MERCADO DINÁMICO (MULTI-TARGET)
 * Escanea múltiples pares en paralelo.
 * Es capaz de recibir nuevas órdenes de objetivos en caliente desde el DynamicPairSelector.
 */
public class MarketListener {
    private final CrossTradeExecutor executor;
    private final ExchangeConnector connector;
    private final CrossArbitrageDetector detector;
    private final ScheduledExecutorService scheduler;

    // 🎯 LISTA DE OBJETIVOS MUTABLE (Variable de instancia, NO estática)
    // Se inicializa con objetivos "Economy Class" por defecto, pero cambiará dinámicamente.
    private List<String> targetPairs;

    // Índice para rotar el escaneo en cada tick
    private int currentPairIndex = 0;

    public MarketListener() {
        this.connector = new ExchangeConnector();
        this.executor = new CrossTradeExecutor();
        this.detector = new CrossArbitrageDetector(executor, connector);
        this.scheduler = Executors.newScheduledThreadPool(4);

        // Inicialización por defecto: Monedas rápidas y baratas
        this.targetPairs = new ArrayList<>(List.of("SOLUSDT", "AVAXUSDT", "PEPEUSDT"));
    }

    public void startScanning() {
        BotLogger.info("📡 INICIANDO ESCANEO DE MERCADO (Modo: Dinámico)...");
        // Polling cada 2 segundos
        scheduler.scheduleAtFixedRate(this::pollMarket, 0, 2, TimeUnit.SECONDS);
    }

    /**
     * MÉTODO DE INYECCIÓN (CEREBRO -> OJOS)
     * Permite al DynamicPairSelector actualizar la lista de vigilancia en tiempo real.
     */
    public void updateTargets(List<String> newTargets) {
        synchronized (this) {
            // Reemplazamos la lista de objetivos
            this.targetPairs = new ArrayList<>(newTargets);
            // Reiniciamos el índice para empezar a escanear la nueva lista desde el principio
            this.currentPairIndex = 0;
        }
        BotLogger.info("🎯 RASTREADOR RECONFIGURADO. Nuevos Objetivos: " + newTargets);
    }

    private void pollMarket() {
        String targetPair;

        // 1. SELECCIÓN DE OBJETIVO (Sincronizada para evitar conflictos de hilos)
        synchronized (this) {
            if (targetPairs.isEmpty()) return; // Seguridad

            targetPair = targetPairs.get(currentPairIndex);
            currentPairIndex = (currentPairIndex + 1) % targetPairs.size(); // Rotación circular
        }

        long startCycle = System.nanoTime();

        // 2. DISPARO PARALELO (Scatter)
        // Usamos variables finales efectivas para la lambda
        String finalTargetPair = targetPair;
        CompletableFuture<Double> fBinance = CompletableFuture.supplyAsync(() -> safeFetch("binance", finalTargetPair));
        CompletableFuture<Double> fMexc    = CompletableFuture.supplyAsync(() -> safeFetch("mexc", finalTargetPair));
        CompletableFuture<Double> fBybit   = CompletableFuture.supplyAsync(() -> safeFetch("bybit", finalTargetPair));
        CompletableFuture<Double> fKucoin  = CompletableFuture.supplyAsync(() -> safeFetch("kucoin", finalTargetPair));

        try {
            // 3. RECOLECCIÓN (Gather)
            CompletableFuture.allOf(fBinance, fMexc, fBybit, fKucoin).join();

            Map<String, Double> prices = new HashMap<>();
            if (fBinance.getNow(0.0) > 0) prices.put("binance", fBinance.get());
            if (fMexc.getNow(0.0) > 0)    prices.put("mexc", fMexc.get());
            if (fBybit.getNow(0.0) > 0)   prices.put("bybit", fBybit.get());
            if (fKucoin.getNow(0.0) > 0)  prices.put("kucoin", fKucoin.get());

            long endNetwork = System.nanoTime();
            double latencyMs = (endNetwork - startCycle) / 1_000_000.0;

            // Log de Escaneo
            BotLogger.info(String.format("📡 SCAN %s [%d ms]: Bin:%.4f | Mex:%.4f | Byb:%.4f | Kuc:%.4f",
                    finalTargetPair, (long)latencyMs,
                    prices.getOrDefault("binance", 0.0),
                    prices.getOrDefault("mexc", 0.0),
                    prices.getOrDefault("bybit", 0.0),
                    prices.getOrDefault("kucoin", 0.0)
            ));

            // 4. ALIMENTAR AL CEREBRO DE ARBITRAJE
            prices.forEach((exchange, price) -> detector.onPriceUpdate(exchange, finalTargetPair, price));

        } catch (Exception e) {
            BotLogger.error("Error escaneando " + targetPair + ": " + e.getMessage());
        }
    }

    private Double safeFetch(String exchange, String pair) {
        try {
            String targetExchange = exchange.equals("bybit") ? "bybit_sub1" : exchange;
            // Ajuste de formato para Kucoin (PEPE-USDT vs PEPEUSDT)
            String fetchPair = exchange.equals("kucoin") && !pair.contains("-") ? pair.replace("USDT", "-USDT") : pair;

            return connector.fetchPrice(targetExchange, fetchPair);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}