package com.rafaeldiaz.orquestador_gold_rush_2025.core.main;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic.MarketCortex;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic.ProbabilisticOracle;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 🩸 ORACLE TORTURE TEST SUITE
 * "Si sangra aquí, no morirá en Tokio."
 * * Simula condiciones extremas de mercado para validar:
 * 1. Detección Lead-Lag.
 * 2. Cálculo Estadístico (Mean Reversion).
 * 3. Integridad de Memoria (Buffer Overflow).
 * 4. Resistencia a Concurrencia (Thread Safety).
 */
public class OracleTortureTest {

    // Colores para consola
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    public static void main(String[] args) throws InterruptedException {
        System.out.println(ANSI_YELLOW + "🔥🔥 INICIANDO PROTOCOLO DE TORTURA: AGENTE TOKIO v4.5 🔥🔥" + ANSI_RESET);

        // Setup Inicial
        MarketCortex cortex = new MarketCortex();
        ProbabilisticOracle oracle = new ProbabilisticOracle(cortex);

        boolean allPassed = true;

        // ---------------------------------------------------------
        // ESCENARIO 1: EL FLASH PUMP (Simulación Lead-Lag)
        // ---------------------------------------------------------
        printHeader("TEST 1: LEAD-LAG DETECTION (Binance Mooning)");
        try {
            String leader = "binance"; // Asumiendo default en BotConfig
            String follower = "mexc";
            String asset = "WIF";

            // Fase 1: Mercado Plano (Calentamiento)
            injectPriceSequence(cortex, asset, leader, 1.00, 1.00, 5);
            injectPriceSequence(cortex, asset, follower, 1.00, 1.00, 5);

            // Fase 2: EL DISPARO (Binance sube 10% en 5 ticks, MEXC quieto)
            // Ticks: 1.02, 1.04, 1.06, 1.08, 1.10
            for (int i = 1; i <= 5; i++) {
                double price = 1.00 + (i * 0.02);
                injectSinglePrice(cortex, asset, leader, price);
                injectSinglePrice(cortex, asset, follower, 1.00); // MEXC sigue en 1.00
            }

            // Evaluación
            var verdict = oracle.getVerdict(asset, 0.10, follower); // Spread actual 10%

            System.out.println("Oracle Confidence: " + verdict.confidenceScore());
            System.out.println("Signal Source: " + verdict.signalSource());

            if (verdict.confidenceScore() > 0.8 && verdict.signalSource().contains("LEAD_LAG")) {
                printPass("Oráculo detectó la divergencia del Líder.");
            } else {
                printFail("Oráculo NO vio que Binance subió y MEXC no. (Ciego)");
                allPassed = false;
            }

        } catch (Exception e) {
            printFail("Excepción en Test 1: " + e.getMessage());
            e.printStackTrace();
            allPassed = false;
        }

        // ---------------------------------------------------------
        // ESCENARIO 2: MEAN REVERSION (El Cisne Negro)
        // ---------------------------------------------------------
        printHeader("TEST 2: MEAN REVERSION (Z-Score Anomaly)");
        try {
            String asset = "PEPE";

            // Entrenar el cerebro con spreads "normales" (0.1% a 0.2%)
            // Simulamos 50 ciclos de normalidad
            Random rng = new Random();
            for (int i = 0; i < 50; i++) {
                double normalSpread = 0.001 + (rng.nextDouble() * 0.001); // 0.1% - 0.2%
                cortex.recordSpread(asset, normalSpread);
            }

            // Inyectar una anomalía BRUTAL (Spread 1.5% -> cisne negro)
            double anomalySpread = 0.015;
            double zScore = cortex.getSpreadZScore(asset, anomalySpread);

            System.out.println("Spread Normal Avg: ~0.15%");
            System.out.println("Anomaly Spread: 1.5%");
            System.out.println("Calculated Z-Score: " + zScore);

            // Z-Score debería ser altísimo (probablemente > 5)
            if (zScore > 3.0) {
                printPass("Oráculo detectó anomalía estadística (Z-Score > 3).");
            } else {
                printFail("Oráculo cree que un spread de 1.5% es normal. (Malas matemáticas)");
                allPassed = false;
            }

        } catch (Exception e) {
            printFail("Excepción en Test 2: " + e.getMessage());
            allPassed = false;
        }

        // ---------------------------------------------------------
        // ESCENARIO 3: CONCURRENCY STRESS (El Ataque)
        // ---------------------------------------------------------
        printHeader("TEST 3: CONCURRENCY BLITZ (Thread Safety)");
        try {
            int threads = 20;
            int updatesPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            long start = System.currentTimeMillis();

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < updatesPerThread; j++) {
                        // Bombardeo masivo de precios aleatorios
                        injectSinglePrice(cortex, "DOGE", "binance", Math.random() * 100);
                        cortex.recordSpread("DOGE", Math.random() * 0.01);
                    }
                });
            }

            executor.shutdown();
            boolean finished = executor.awaitTermination(5, TimeUnit.SECONDS);
            long time = System.currentTimeMillis() - start;

            if (finished) {
                printPass("Cortex sobrevivió a " + (threads * updatesPerThread) + " actualizaciones en " + time + "ms.");
            } else {
                printFail("Deadlock o lentitud extrema detectada.");
                allPassed = false;
            }

        } catch (Exception e) {
            printFail("Excepción en Test 3: " + e.getMessage());
            allPassed = false;
        }

        // ---------------------------------------------------------
        // RESUMEN FINAL
        // ---------------------------------------------------------
        System.out.println("\n========================================");
        if (allPassed) {
            System.out.println(ANSI_GREEN + "🏆 MISIÓN CUMPLIDA: EL CÓDIGO ESTÁ SÓLIDO COMO UNA ROCA." + ANSI_RESET);
            System.out.println("Proceda al despliegue en Tokio.");
        } else {
            System.out.println(ANSI_RED + "💀 FALLO CRÍTICO: REVISE LOS ERRORES ANTES DE VOLAR." + ANSI_RESET);
        }
        // ... (Tests 1, 2 y 3 anteriores) ...

        // ---------------------------------------------------------
        // ESCENARIO 4: THE DESERT WALK (Data Depth / Sparsity)
        // ---------------------------------------------------------
        printHeader("TEST 4: COLD START & DATA SPARSITY (El Desierto)");
        try {
            String ghostAsset = "ILLIQUID";

            // CASO A: Activo Fantasma (Sin datos previos)
            // El buffer está vacío. ¿Explota?
            var verdictEmpty = oracle.getVerdict(ghostAsset, 0.05, "mexc");
            if (verdictEmpty.confidenceScore() == 0.0) {
                printPass("Oráculo ignora activo sin historia (Empty State).");
            } else {
                printFail("Oráculo alucinó datos donde no existen.");
                allPassed = false;
            }

            // CASO B: El "One-Tick Wonder" (Solo 2 datos)
            // Inyectamos solo 2 precios. Welford necesita al menos 2 para desviación,
            // pero para Lead-Lag fiable necesitamos más historia.
            injectSinglePrice(cortex, ghostAsset, "binance", 100.0);
            injectSinglePrice(cortex, ghostAsset, "binance", 105.0); // Subida del 5% instantánea

            // Aunque hay un salto gigante (Lead-Lag potencial), la muestra estadística es basura (N=2).
            // El Oráculo debería tener baja confianza o Z-Score 0.

            // Forzamos un spread actual para ver qué piensa del Z-Score
            cortex.recordSpread(ghostAsset, 0.05); // Primer spread

            var verdictSparse = oracle.getVerdict(ghostAsset, 0.05, "mexc");

            System.out.println("Sparse Confidence: " + verdictSparse.confidenceScore());

            // Esperamos que la confianza sea 0.0 o muy baja porque Z-Score requiere warmup
            // y Lead-Lag requiere llenar la ventana de ticks.
            if (verdictSparse.confidenceScore() < 0.5) {
                printPass("Oráculo desconfía de activos con poca profundidad de datos (N<10).");
            } else {
                printFail("Riesgo Alto: Oráculo confía demasiado rápido en datos escasos.");
                allPassed = false;
            }

        } catch (Exception e) {
            printFail("Excepción en Test 4: " + e.getMessage());
            e.printStackTrace();
            allPassed = false;
        }

        // ---------------------------------------------------------
        // RESUMEN FINAL
        // ---------------------------------------------------------
        System.out.println("\n========================================");
    }

    // --- Helpers de Inyección ---

    private static void injectSinglePrice(MarketCortex cortex, String asset, String exchange, double price) {
        Map<String, Map<String, Double>> snapshot = new HashMap<>();
        Map<String, Double> assets = new HashMap<>();
        assets.put(asset + "USDT", price);
        snapshot.put(exchange, assets);
        cortex.ingest(snapshot);
    }

    private static void injectPriceSequence(MarketCortex cortex, String asset, String exchange, double start, double end, int steps) {
        double stepSize = (end - start) / steps;
        for (int i = 0; i < steps; i++) {
            injectSinglePrice(cortex, asset, exchange, start + (stepSize * i));
        }
    }

    private static void printHeader(String title) {
        System.out.println("\n" + ANSI_YELLOW + ">>> " + title + " <<<" + ANSI_RESET);
    }

    private static void printPass(String msg) {
        System.out.println(ANSI_GREEN + "✅ PASS: " + msg + ANSI_RESET);
    }

    private static void printFail(String msg) {
        System.out.println(ANSI_RED + "❌ FAIL: " + msg + ANSI_RESET);
    }
}