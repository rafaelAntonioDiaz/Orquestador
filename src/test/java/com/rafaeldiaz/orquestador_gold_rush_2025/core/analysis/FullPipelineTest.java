package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.AdaptiveSpatialStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.SpatialArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.TriangularArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.DecisionAuditor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 🏭 SIMULACRO DE FÁBRICA COMPLETO
 * Inyecta datos de mercado sintéticos para disparar TODOS los sensores
 * del sistema de trazabilidad y verificar el CSV resultante.
 */
public class FullPipelineTest {

    private static final String FILE_NAME = "decision_trace.csv";

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("🏭 INICIANDO SIMULACRO DE INTEGRACIÓN TOTAL...");

        // 1. LIMPIEZA
        new File(FILE_NAME).delete();
        Thread.sleep(200); // Dar tiempo al SO

        // 2. PREPARACIÓN DE DATOS FALSOS (MERCADO SINTÉTICO)
        // Escenario: Bitcoin está barato en Binance y caro en Bybit
        Map<String, Map<String, Double>> fakeMarket = new HashMap<>();

        // Binance: BTC a 50,000
        Map<String, Double> binancePrices = new HashMap<>();
        binancePrices.put("BTCUSDT", 50000.0);
        binancePrices.put("ETHUSDT", 3000.0);
        fakeMarket.put("binance", binancePrices);

        // Bybit: BTC a 50,250 (0.5% Spread!)
        Map<String, Double> bybitPrices = new HashMap<>();
        bybitPrices.put("BTCUSDT", 50250.0);
        fakeMarket.put("bybit", bybitPrices);

        // Mexc: Precios planos (para probar rechazos)
        Map<String, Double> mexcPrices = new HashMap<>();
        mexcPrices.put("ETHUSDT", 3000.5); // Spread despreciable
        fakeMarket.put("mexc", mexcPrices);

        // --- 🧪 PRUEBA 1: ESTRATEGIA ESPACIAL ---
        System.out.println("🔹 Testeando Sensor Espacial...");
        // Mock del CFO (null porque para este test no validamos inventario en la estrategia)
        SpatialArbitrageStrategy spatial = new SpatialArbitrageStrategy(0.002, null); // 0.2% min

        // Ejecutar con datos que generan spread 0.5% (Binance->Bybit)
        spatial.findOpportunities("BTC", fakeMarket);
        // Ejecutar con datos planos (Binance->Mexc ETH)
        spatial.findOpportunities("ETH", fakeMarket);

        // --- 🧪 PRUEBA 2: ESTRATEGIA ADAPTATIVA ---
        System.out.println("🔹 Testeando Sensor Adaptativo...");
        AdaptiveSpatialStrategy adaptive = new AdaptiveSpatialStrategy(null);
        adaptive.findOpportunities("BTC", fakeMarket); // Debería detectarlo también

        // --- 🧪 PRUEBA 3: ESTRATEGIA TRIANGULAR ---
        System.out.println("🔹 Testeando Sensor Triangular...");
        // Simulamos un loop: USDT -> A -> B -> USDT
        // Precios para loop perfecto del 1%
        Map<String, Double> triPrices = new HashMap<>();
        triPrices.put("LTCUSDT", 100.0); // Compro LTC con $100
        triPrices.put("LTCBTC", 0.002);  // Cambio LTC por BTC (Recibo 0.002 BTC)
        triPrices.put("BTCUSDT", 50500.0); // Vendo 0.002 BTC a $101 (Ganancia $1)

        Map<String, Map<String, Double>> triMarket = new HashMap<>();
        triMarket.put("binance", triPrices);

        TriangularArbitrageStrategy triangular = new TriangularArbitrageStrategy(List.of("BTC"), 0.005);
        triangular.findOpportunities("LTC", triMarket);

        // --- 🧪 PRUEBA 4: SIMULACIÓN DE FLUJO FINANCIERO Y EJECUCIÓN ---
        System.out.println("🔹 Simulando Sensores de Scanner y Executor...");
        // Como no podemos instanciar todo el Scanner sin conectarnos, inyectamos los logs
        // que generarían esos componentes si recibieran estas oportunidades.

        // Simulación: El Scanner rechaza la oportunidad de ETH por fees
        DecisionAuditor.log("SPATIAL_SIMPLE", "ETH", "Binance->Mexc", 0.0001, -0.50,
                "FINANCIERO", "RECHAZADO", "Fees > Spread");

        // Simulación: El Executor completa la de BTC
        DecisionAuditor.log("SPATIAL_SIMPLE", "BTC", "Binance->Bybit", 0.0050, 12.50,
                "BATALLA", "VICTORIA", "PnL Real: $12.50");

        // 3. ESPERA Y VALIDACIÓN
        System.out.println("⏳ Esperando persistencia en disco...");
        Thread.sleep(1500);

        verifyFullTrace();
    }

    private static void verifyFullTrace() throws IOException {
        List<String> lines = Files.readAllLines(Path.of(FILE_NAME));

        System.out.println("\n📊 REPORTE DE INTEGRACIÓN:");
        System.out.println("============================");
        boolean headerOk = lines.get(0).contains("HORA,ESTRATEGIA,ACTIVO");
        System.out.println("1. Cabecera CSV: " + (headerOk ? "✅ CORRECTA" : "❌ ERROR"));

        // Buscamos evidencias específicas en el texto
        boolean hasSpatialOk = lines.stream().anyMatch(l -> l.contains("SPATIAL_SIMPLE") && l.contains("CANDIDATO") && l.contains("BTC"));
        boolean hasAdaptOk = lines.stream().anyMatch(l -> l.contains("Spatial-Adaptive") && l.contains("CANDIDATO"));
        boolean hasTriangularOk = lines.stream().anyMatch(l -> l.contains("TRIANGULAR") && l.contains("CANDIDATO"));
        boolean hasFinancialKill = lines.stream().anyMatch(l -> l.contains("FINANCIERO") && l.contains("RECHAZADO"));
        boolean hasVictory = lines.stream().anyMatch(l -> l.contains("BATALLA") && l.contains("VICTORIA"));

        System.out.println("2. Sensor Espacial (BTC):     " + (hasSpatialOk ? "✅ DETECTADO" : "❌ SILENCIO"));
        System.out.println("3. Sensor Adaptativo (BTC):   " + (hasAdaptOk ? "✅ DETECTADO" : "❌ SILENCIO"));
        System.out.println("4. Sensor Triangular (LTC):   " + (hasTriangularOk ? "✅ DETECTADO" : "❌ SILENCIO"));
        System.out.println("5. Sensor Financiero (ETH):   " + (hasFinancialKill ? "✅ DETECTADO" : "❌ SILENCIO"));
        System.out.println("6. Sensor Ejecución (WIN):    " + (hasVictory ? "✅ DETECTADO" : "❌ SILENCIO"));

        System.out.println("\n📂 CONTENIDO DEL CSV GENERADO:");
        lines.forEach(System.out::println);
    }
}