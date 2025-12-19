package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.concurrent.*;
import java.util.Map;
import java.util.HashMap;

/**
 * Task 4.4: Radar de Mercado en Tiempo Real.
 * Consulta precios de TODOS los exchanges en PARALELO para minimizar latencia.
 */
public class MarketListener {
    private final CrossTradeExecutor executor;
    private final ExchangeConnector connector;
    private final CrossArbitrageDetector detector;
    private final ScheduledExecutorService scheduler;

    // Pares que vamos a vigilar (Por ahora solo BTC, luego agregaremos lista dinámica)
    private static final String TARGET_PAIR = "BTCUSDT";

    public MarketListener() {
        this.connector = new ExchangeConnector();
        // 1. Creamos las Manos
        this.executor = new CrossTradeExecutor();
// 2. Conectamos Manos al Cerebro (Inyección de Dependencia)
        this.detector = new CrossArbitrageDetector(executor, connector);
        this.scheduler = Executors.newScheduledThreadPool(4); // 4 hilos para escuchar a los 4 grandes
    }

    public void startScanning() {
        BotLogger.info("📡 INICIANDO ESCANEO DE MERCADO EN TIEMPO REAL...");

        // Ejecutar cada 3 segundos (Para no saturar APIs ni ser baneados por Rate Limit)
        scheduler.scheduleAtFixedRate(this::pollMarket, 0, 3, TimeUnit.SECONDS);
    }

    private void pollMarket() {
        long startCycle = System.nanoTime();

        // 1. LANZAMIENTO PARALELO DE PETICIONES (Scatter)
        // Preguntamos a los 4 al mismo tiempo.
        CompletableFuture<Double> fBinance = CompletableFuture.supplyAsync(() -> safeFetch("binance"));
        CompletableFuture<Double> fMexc    = CompletableFuture.supplyAsync(() -> safeFetch("mexc"));
        CompletableFuture<Double> fBybit   = CompletableFuture.supplyAsync(() -> safeFetch("bybit"));
        CompletableFuture<Double> fKucoin  = CompletableFuture.supplyAsync(() -> safeFetch("kucoin"));

        try {
            // 2. RECOLECCIÓN DE DATOS (Gather)
            // Esperamos a que todos respondan (o fallen)
            CompletableFuture.allOf(fBinance, fMexc, fBybit, fKucoin).join();

            // Obtenemos resultados (getNow devuelve null si falló)
            Map<String, Double> prices = new HashMap<>();
            if (fBinance.getNow(0.0) > 0) prices.put("binance", fBinance.get());
            if (fMexc.getNow(0.0) > 0)    prices.put("mexc", fMexc.get());
            if (fBybit.getNow(0.0) > 0)   prices.put("bybit", fBybit.get());
            if (fKucoin.getNow(0.0) > 0)  prices.put("kucoin", fKucoin.get());

            // 3. MEDICIÓN DE LATENCIA DE RED TOTAL
            long endNetwork = System.nanoTime();
            double latencyMs = (endNetwork - startCycle) / 1_000_000.0;

            BotLogger.info(String.format("📡 SCAN [%d ms]: Bin:%.2f | Mex:%.2f | Byb:%.2f | Kuc:%.2f",
                    (long)latencyMs,
                    prices.getOrDefault("binance", 0.0),
                    prices.getOrDefault("mexc", 0.0),
                    prices.getOrDefault("bybit", 0.0),
                    prices.getOrDefault("kucoin", 0.0)
            ));

            // 4. ANÁLISIS (Alimentar al Cerebro)
            prices.forEach((exchange, price) -> detector.onPriceUpdate(exchange, TARGET_PAIR, price));

        } catch (Exception e) {
            BotLogger.error("Error en ciclo de escaneo: " + e.getMessage());
        }
    }

    private Double safeFetch(String exchange) {
        try {
            // Mapeo de nombres si es necesario
            String targetExchange = exchange;
            if (exchange.equals("bybit")) targetExchange = "bybit_sub1"; // <--- FIX

            String pair = exchange.equals("kucoin") ? "BTC-USDT" : "BTCUSDT";
            return connector.fetchPrice(targetExchange, pair);
        } catch (Exception e) {
            System.err.println("❌ ERROR " + exchange + ": " + e.getMessage());
            return 0.0;
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}