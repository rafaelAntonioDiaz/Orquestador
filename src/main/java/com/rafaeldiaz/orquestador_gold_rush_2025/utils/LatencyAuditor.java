package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class LatencyAuditor {
    private final ExchangeConnector connector;
    private final Map<String, Long> latencies = new ConcurrentHashMap<>();

    public LatencyAuditor(ExchangeConnector connector) {
        this.connector = connector;
    }

    public void runFullAudit() {
        // 🚀 Java 25 Virtual Threads: Máxima eficiencia, cero interferencia
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            String[] exchanges = {"bybit", "binance", "mexc", "kucoin"};

            for (String ex : exchanges) {
                executor.submit(() -> {
                    long start = System.currentTimeMillis();
                    connector.fetchPrice(ex, "BTCUSDT"); // Ping funcional
                    long rtt = System.currentTimeMillis() - start;
                    latencies.put(ex, rtt);
                    BotLogger.info("📡 Latencia " + ex.toUpperCase() + ": " + rtt + "ms");
                });
            }
        }
    }

    public Map<String, Long> getLatencies() { return latencies; }
}