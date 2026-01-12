package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import com.rafaeldiaz.orquestador_gold_rush_2025.model.LatencyBreakdown;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * 📊 SERVICIO DE MÉTRICAS (In-Memory / Zero-Latency)
 * Centraliza la verdad operativa del bot.
 * Diseño "Lock-Free" REAL usando LongAdders y DoubleAdders.
 */
public class MetricsService {

    // Instancia Singleton
    private static final MetricsService INSTANCE = new MetricsService();

    // ⏱️ CONTEXTO TEMPORAL
    private final Instant sessionStart;

    // 🚦 MÉTRICAS DE TRÁFICO
    private final LongAdder throughputCounter = new LongAdder();
    private final Map<String, LongAdder> errorCounts = new ConcurrentHashMap<>();

    // ⚡ LATENCIA
    private final Map<String, LatencyStats> latencyStats = new ConcurrentHashMap<>();

    // 💰 NEGOCIO
    private final LongAdder totalTrades = new LongAdder();
    private final LongAdder winningTrades = new LongAdder();
    private final DoubleAdder estimatedPnL = new DoubleAdder();

    // 📜 HISTORIAL DE LATENCIA ( Mantiene las últimas 50)
    // Estructura para guardar las últimas 50 operaciones (Buffer Circular ligero)
    private static final int MAX_HISTORY_SIZE = 50;
    private final java.util.concurrent.ConcurrentLinkedDeque<LatencyBreakdown>
            latencyHistory = new java.util.concurrent.ConcurrentLinkedDeque<>();


    public MetricsService() {
        this.sessionStart = Instant.now();
    }

    public static MetricsService get() { return INSTANCE; }
    /**
     * Registra un "latido" u operación del sistema.
     * Usado por DeepMarketScanner para indicar "Ciclo Terminado".
     */
    public void recordOp() {
        throughputCounter.increment();
    }



    public void recordError(String exchange) {
        errorCounts.computeIfAbsent(exchange, k -> new LongAdder()).increment();
    }

    public void recordLatency(String exchange, long ms) {
        latencyStats.computeIfAbsent(exchange, k -> new LatencyStats()).record(ms);
    }

    /**
     * ✅ MÉTODO REPARADO (LOCK-FREE)
     * Registra el resultado financiero sin bloquear hilos.
     */
    public void recordTrade(boolean win, double pnl) {
        totalTrades.increment();       // Atómico
        if (win) winningTrades.increment(); // Atómico

        // 🚀 AQUÍ ESTÁ LA MAGIA:
        // En lugar de '+=', usamos el método nativo .add()
        // Esto permite que 50 hilos escriban a la vez sin esperar turno.
        estimatedPnL.add(pnl);
    }

    // --- 📖 API DE LECTURA ---

    public Instant getSessionStart() { return sessionStart; }

    public Duration getUptime() { return Duration.between(sessionStart, Instant.now()); }

    public long getThroughputTotal() { return throughputCounter.sum(); }

    /**
     * ✅ LECTURA ATÓMICA
     * Suma los valores de todas las celdas de memoria concurrentes.
     */
    public double getPnL() {
        return estimatedPnL.sum(); // Sin synchronized
    }

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

    // Clase interna auxiliar para latencia
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
    /**
     * 🟢 MÉTODO RESTAURADO: Registra el desglose de latencia de una operación.
     * Llamado desde el Fast Lane (debe ser muy rápido).
     */
    public void recordLatencyBreakdown(long netInUs, long logicUs, long netOutUs) {
        LatencyBreakdown breakdown = new LatencyBreakdown(
                System.currentTimeMillis(), // 1. timestamp
                netInUs,                    // 2. netInUs
                0,                          // 3. feeCalcUs (Default 0 para compatibilidad)
                logicUs,                    // 4. logicUs
                0,                          // 5. cfoUs (Default 0 para compatibilidad)
                netOutUs                    // 6. netOutUs
        );
        latencyHistory.add(breakdown);

        // Mantenimiento asíncrono del tamaño
        if (latencyHistory.size() > MAX_HISTORY_SIZE) {
            latencyHistory.pollFirst();
        }
    }
    /**
     * 🟢 MÉTODO RESTAURADO: Permite al Dashboard leer la historia reciente.
     */
    public java.util.Deque<LatencyBreakdown> getRecentLatencyHistory() {
        return latencyHistory; // Retorna la colección concurrente directa
    }
    // --- 📝 API DE REGISTRO ---



    /**
     * Sobrecarga opcional: Si alguna vez necesitas registrar detalles específicos.
     * (Mantiene compatibilidad con versiones anteriores si es necesario)
     */
    public void recordOp(String exchange, long latencyMs, boolean error) {
        throughputCounter.increment();
        if (error) {
            errorCounts.computeIfAbsent(exchange, k -> new LongAdder()).increment();
        }
        // Opcional: registrar latencia en el mapa de estadísticas
        // latencyStats.computeIfAbsent(exchange, k -> new LatencyStats()).record(latencyMs);
    }
}