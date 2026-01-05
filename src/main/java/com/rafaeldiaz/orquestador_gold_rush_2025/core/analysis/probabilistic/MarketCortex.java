package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 🧠 MARKET CORTEX
 * Estructura de datos pura. Mantiene la memoria de corto plazo y estadísticas
 * en tiempo real. GC-Free (Sin recolección de basura en el loop crítico).
 */
public class MarketCortex {

    // Estructura: Activo -> Exchange -> Buffer
    private final Map<String, Map<String, CircularDoubleBuffer>> priceHistory = new ConcurrentHashMap<>();

    // Estadísticas de Spread (Welford)
    private final Map<String, WelfordAccumulator> spreadStats = new ConcurrentHashMap<>();

    private final AtomicLong pulseCount = new AtomicLong(0);

    public MarketCortex() {
        BotLogger.info("🧠 MARKET CORTEX: Memoria asignada (" + BotConfig.ORACLE_HISTORY_SIZE + " ticks)");
    }

    /**
     * Ingesta asíncrona de precios (Sidecar).
     */
    public void ingest(Map<String, Map<String, Double>> marketSnapshot) {
        pulseCount.incrementAndGet();
        marketSnapshot.forEach((exchange, assets) -> {
            assets.forEach((assetRaw, price) -> {
                String asset = assetRaw.replace("USDT", "");
                priceHistory.computeIfAbsent(asset, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(exchange, k -> new CircularDoubleBuffer(BotConfig.ORACLE_HISTORY_SIZE))
                        .add(price);
            });
        });
    }

    /**
     * 📊 Obtener Velocidad de Precio (Blindado contra Data Sparsity)
     */
    public double getPriceVelocity(String asset, String exchange, int lookbackTicks) {
        CircularDoubleBuffer buffer = getBuffer(asset, exchange);

        // 🛡️ PARCHE TEST 4: "THE DESERT WALK"
        // Si el buffer no tiene suficientes datos para cubrir la ventana solicitada,
        // devolvemos 0.0. No adivinamos tendencias con datos insuficientes.
        // Necesitamos al menos (lookback + 1) datos para comparar t(now) vs t(past).
        if (buffer == null || buffer.size() <= lookbackTicks) return 0.0;

        double currentPrice = buffer.getLatest();

        // Ahora es seguro obtener el dato pasado porque validamos el tamaño arriba
        double pastPrice = buffer.get(lookbackTicks);

        if (pastPrice == 0) return 0.0;
        return (currentPrice - pastPrice) / pastPrice;
    }

    public void recordSpread(String asset, double spreadPct) {
        spreadStats.computeIfAbsent(asset, k -> new WelfordAccumulator()).update(spreadPct);
    }

    public double getSpreadZScore(String asset, double currentSpread) {
        WelfordAccumulator stats = spreadStats.get(asset);
        // 🛡️ GUARDA DE PROFUNDIDAD:
        // Si tenemos menos de 10 muestras de spread, devolvemos 0.
        // Esto evita que el primer spread (que siempre será media=spread, dev=0)
        // genere infinitos o falsos positivos.
        if (stats == null || stats.count() < 10) return 0.0; // Warmup

        double stdDev = stats.stdDev();
        if (stdDev == 0) return 0.0;

        return (currentSpread - stats.mean()) / stdDev;
    }

    private CircularDoubleBuffer getBuffer(String asset, String exchange) {
        Map<String, CircularDoubleBuffer> exchangeMap = priceHistory.get(asset);
        return (exchangeMap == null) ? null : exchangeMap.get(exchange);
    }

    // --- Estructuras Internas (Optimizadas) ---

    public static class CircularDoubleBuffer {
        private final double[] buffer;
        private int head = 0;
        private int size = 0;
        private final int capacity;

        public CircularDoubleBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new double[capacity];
        }

        public synchronized void add(double value) {
            head = (head + 1) % capacity;
            buffer[head] = value;
            if (size < capacity) size++;
        }

        public synchronized double getLatest() {
            return size == 0 ? 0.0 : buffer[head];
        }

        public synchronized double get(int stepsBack) {
            if (size == 0 || stepsBack >= size) return 0.0;
            // Aritmética modular para ir hacia atrás en el círculo
            int index = (head - stepsBack + capacity) % capacity;
            return buffer[index];
        }

        public synchronized int size() { return size; }
    }

    public static class WelfordAccumulator {
        private long n = 0;
        private double mean = 0.0;
        private double M2 = 0.0;

        public synchronized void update(double x) {
            n++;
            double delta = x - mean;
            mean += delta / n;
            double delta2 = x - mean;
            M2 += delta * delta2;
        }

        public synchronized double mean() { return mean; }
        public synchronized double stdDev() { return (n < 2) ? 0.0 : Math.sqrt(M2 / (n - 1)); }
        public synchronized long count() { return n; }
    }
}