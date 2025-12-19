package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementación REAL de streaming mediante Polling de Alta Frecuencia.
 * Cumple todo el contrato de MarketStreamer.
 */
public class BybitLiveStreamer extends MarketStreamer {

    private final ExchangeConnector connector;
    private final ScheduledExecutorService scheduler;

    // Usamos un Set thread-safe para gestionar las suscripciones activas
    private final Set<String> subscribedPairs = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;

    public BybitLiveStreamer(ExchangeConnector connector) {
        this.connector = connector;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = true;

        BotLogger.info("🔌 BybitLiveStreamer INICIADO (Modo: High-Freq Polling)");
        startPollingLoop();
    }

    @Override
    public void subscribe(String pair) {
        if (subscribedPairs.add(pair)) {
            BotLogger.info("📡 [Streamer] Suscrito a: " + pair);
        }
    }

    @Override
    public void unsubscribe(String pair) {
        if (subscribedPairs.remove(pair)) {
            BotLogger.info("🔕 [Streamer] Desuscrito de: " + pair);
        }
    }

    @Override
    public void stop() {
        running = false;
        scheduler.shutdownNow();
        BotLogger.info("🔌 BybitLiveStreamer DETENIDO.");
    }

    @Override
    public boolean isActive() {
        return running && !scheduler.isTerminated();
    }

    private void startPollingLoop() {
        // Ciclo infinito cada 500ms
        scheduler.scheduleAtFixedRate(() -> {
            if (!running || subscribedPairs.isEmpty()) return;

            for (String pair : subscribedPairs) {
                try {
                    // Fetch real usando el conector maestro
                    // 'bybit_sub1' es tu cuenta principal de trading
                    double price = connector.fetchPrice("bybit_sub1", pair);

                    if (price > 0) {
                        notifyListeners("bybit", pair, price, System.currentTimeMillis());
                    }
                } catch (Exception e) {
                    // Log level debug para no saturar si hay un fallo de red momentáneo
                    // System.err.println("Error polling " + pair + ": " + e.getMessage());
                }
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }
}