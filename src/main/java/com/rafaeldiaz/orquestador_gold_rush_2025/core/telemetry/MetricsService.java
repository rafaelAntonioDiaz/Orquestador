package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 📊 SERVICIO DE MÉTRICAS (In-Memory / Zero-Latency)
 * Centraliza la verdad operativa del bot.
 * Diseño "Lock-Free" usando LongAdders para máxima concurrencia en Java 25.
 */
public class MetricsService {

    private static final MetricsService INSTANCE = new MetricsService();

    // ⏱️ CONTEXTO TEMPORAL (CRÍTICO)
    private final Instant sessionStart;

    // 🚦 MÉTRICAS DE TRÁFICO
    private final LongAdder throughputCounter = new LongAdder(); // Ops totales
    private final Map<String, LongAdder> errorCounts = new ConcurrentHashMap<>();

    // ⚡ LATENCIA (Simplificada para velocidad: Avg y Max)
    // Guardamos (suma_tiempos, cantidad_muestras) por exchange
    private final Map<String, LatencyStats> latencyStats = new ConcurrentHashMap<>();

    // 💰 NEGOCIO
    private final LongAdder totalTrades = new LongAdder();
    private final LongAdder winningTrades = new LongAdder();
    // Usamos double acumulado protegido (convertido a bits para atomicidad si fuera estricto,
    // pero aquí usaremos un enfoque simple thread-safe para PnL visual)
    private double estimatedPnL = 0.0;
    private final Object pnlLock = new Object(); // Lock mínimo solo para PnL

    private MetricsService() {
        this.sessionStart = Instant.now();
    }

    public static MetricsService get() { return INSTANCE; }

    // --- 📝 API DE REGISTRO (Llamada por los hilos de trabajo) ---

    public void recordOp() {
        throughputCounter.increment();
    }

    public void recordError(String exchange) {
        errorCounts.computeIfAbsent(exchange, k -> new LongAdder()).increment();
    }

    public void recordLatency(String exchange, long ms) {
        latencyStats.computeIfAbsent(exchange, k -> new LatencyStats()).record(ms);
    }

    public void recordTrade(boolean win, double pnl) {
        totalTrades.increment();
        if (win) winningTrades.increment();
        synchronized (pnlLock) {
            estimatedPnL += pnl;
        }
    }

    // --- 📖 API DE LECTURA (Llamada por el DashboardRenderer) ---

    public Instant getSessionStart() { return sessionStart; }

    public Duration getUptime() { return Duration.between(sessionStart, Instant.now()); }

    public long getThroughputTotal() { return throughputCounter.sum(); }

    public double getPnL() { synchronized (pnlLock) { return estimatedPnL; } }

    public long getTradeCount() { return totalTrades.sum(); }

    public long getWinCount() { return winningTrades.sum(); }

    public Map<String, Long> getErrorsSnapshot() {
        Map<String, Long> snap = new ConcurrentHashMap<>();
        errorCounts.forEach((k, v) -> snap.put(k, v.sum()));
        return snap;
    }

    public Map<String, Double> getAvgLatencySnapshot() {
        Map<String, Double> snap = new ConcurrentHashMap<>();
        latencyStats.forEach((k, v) -> snap.put(k, v.getAvg()));
        return snap;
    }

    // Clase interna auxiliar para latencia (Thread-safe básica)
    private static class LatencyStats {
        private final LongAdder totalMs = new LongAdder();
        private final LongAdder count = new LongAdder();

        void record(long ms) {
            totalMs.add(ms);
            count.increment();
        }

        double getAvg() {
            long c = count.sum();
            return c == 0 ? 0.0 : (double) totalMs.sum() / c;
        }
    }
}