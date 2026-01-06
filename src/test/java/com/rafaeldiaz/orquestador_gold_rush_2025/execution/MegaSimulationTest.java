package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.AdaptiveSpatialStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.SpatialArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.TriangularArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.DecisionAuditor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🌪️ PRUEBA DE ESTRÉS DE ALTA DENSIDAD
 * Simula un "Flash Crash" con 50 activos en 8 exchanges para saturar
 * los sensores de trazabilidad y verificar la robustez del CSV.
 */
public class MegaSimulationTest {

    private static final String FILE_NAME = "decision_trace.csv";
    private static final int NUM_ASSETS = 50;
    private static final int NUM_EXCHANGES = 8;
    private static final List<String> ASSETS = generateAssets();
    private static final List<String> EXCHANGES = generateExchanges();

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("🌪️ INICIANDO MEGA-SIMULACIÓN (MODO EXIGENTE)...");

        // 1. LIMPIEZA
        new File(FILE_NAME).delete();
        Thread.sleep(100);

        // 2. GENERACIÓN DE MERCADO CAÓTICO
        System.out.println("📉 Generando mercado sintético (" + (NUM_ASSETS * NUM_EXCHANGES) + " pares)...");
        Map<String, Map<String, Double>> market = generateChaosMarket();

        // 3. PREPARACIÓN DE ESTRATEGIAS
        // CFO es null porque estamos en test de lógica pura
        SpatialArbitrageStrategy spatial = new SpatialArbitrageStrategy(0.005, null); // 0.5% umbral
        AdaptiveSpatialStrategy adaptive = new AdaptiveSpatialStrategy(null); // 0.05% umbral
        TriangularArbitrageStrategy triangular = new TriangularArbitrageStrategy(List.of("BTC", "ETH"), 0.003);

        // 4. EJECUCIÓN PARALELA MASSIVA (Simulando el Scanner en Tokio)
        System.out.println("🔥 DISPARANDO MOTORES (Virtual Threads)...");
        long start = System.currentTimeMillis();

        AtomicInteger opportunitiesDetected = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Lanzamos un hilo por cada activo para que analice el mercado
            for (String asset : ASSETS) {
                executor.submit(() -> {
                    // Estrategia 1: Espacial
                    var op1 = spatial.findOpportunities(asset, market);
                    opportunitiesDetected.addAndGet(op1.size());

                    // Estrategia 2: Adaptativa
                    var op2 = adaptive.findOpportunities(asset, market);
                    opportunitiesDetected.addAndGet(op2.size());

                    // Estrategia 3: Triangular (Solo para algunos activos base)
                    if (List.of("LTC", "XRP", "SOL").contains(asset)) {
                        var op3 = triangular.findOpportunities(asset, market);
                        opportunitiesDetected.addAndGet(op3.size());
                    }
                });
            }
        } // Espera a que terminen todos los cálculos

        long processingTime = System.currentTimeMillis() - start;
        System.out.println("⚡ Análisis terminado en " + processingTime + "ms");

        // Esperamos a que el Auditor (hilo de fondo) termine de escribir en disco
        System.out.println("💾 Esperando persistencia en disco (Drain I/O)...");
        Thread.sleep(2000);

        // 5. AUTOPSIA DEL CSV
        verifyStressResults();
    }

    private static void verifyStressResults() throws IOException {
        Path path = Path.of(FILE_NAME);
        if (!Files.exists(path)) {
            System.err.println("❌ EL CSV NO SE GENERÓ.");
            return;
        }

        List<String> lines = Files.readAllLines(path);
        long countRejected = lines.stream().filter(l -> l.contains("RECHAZADO")).count();
        long countCandidates = lines.stream().filter(l -> l.contains("CANDIDATO")).count();
        long countAdaptive = lines.stream().filter(l -> l.contains("Adaptive")).count();
        long countSpatial = lines.stream().filter(l -> l.contains("SPATIAL_SIMPLE")).count();

        System.out.println("\n📊 REPORTE DE ESTRÉS:");
        System.out.println("======================");
        System.out.println("   Total Registros CSV: " + (lines.size() - 1)); // -1 header
        System.out.println("   -------------------");
        System.out.println("   ❌ Rechazados:       " + countRejected);
        System.out.println("   ✅ Candidatos:       " + countCandidates);
        System.out.println("   -------------------");
        System.out.println("   🤖 Adaptive Logs:    " + countAdaptive);
        System.out.println("   📏 Spatial Logs:     " + countSpatial);

        if (lines.size() > 100) {
            System.out.println("\n🏆 RESULTADO: PRUEBA SUPERADA.");
            System.out.println("   El sistema generó y registró volumen masivo de decisiones.");
            System.out.println("   El cuello de botella NO ES el registro.");
        } else {
            System.err.println("\n⚠️ ALERTA: Pocos registros generados. ¿El mercado sintético fue muy plano?");
        }
    }

    // --- GENERADORES DE DATOS SINTÉTICOS ---

    private static Map<String, Map<String, Double>> generateChaosMarket() {
        Map<String, Map<String, Double>> market = new HashMap<>();
        Random rand = new Random();

        // Inicializar exchanges
        for (String ex : EXCHANGES) market.put(ex, new HashMap<>());

        // Para cada activo, generar precios dispersos
        for (String asset : ASSETS) {
            double basePrice = 10.0 + (rand.nextDouble() * 1000.0); // Precio base entre $10 y $1010

            for (String ex : EXCHANGES) {
                // Volatilidad del 1% arriba o abajo
                double variation = (rand.nextDouble() - 0.5) * 0.02;
                double price = basePrice * (1.0 + variation);

                // Ocasionalmente, crear una oportunidad de oro (Spread 3%)
                if (rand.nextDouble() < 0.05) { // 5% de probabilidad
                    price = basePrice * 1.03;
                }

                market.get(ex).put(asset + "USDT", price);

                // Pares para triangular
                if (asset.equals("LTC") || asset.equals("XRP")) {
                    market.get(ex).put(asset + "BTC", price / 50000.0);
                    market.get(ex).put("BTCUSDT", 50000.0);
                }
            }
        }
        return market;
    }

    private static List<String> generateAssets() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < NUM_ASSETS; i++) list.add("COIN" + i);
        list.add("LTC"); list.add("XRP"); list.add("SOL"); // Reales para triangular
        return list;
    }

    private static List<String> generateExchanges() {
        return List.of("binance", "mexc", "kucoin", "okx", "bybit", "gateio", "huobi", "kraken");
    }
}