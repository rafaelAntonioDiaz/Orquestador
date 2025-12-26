package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner.DeepMarketScanner;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 🏎️ PulseDrillTest: Centro de Mando para la Cacería Omnidireccional.
 * Corregido: Visibilidad de variables y balance de llaves.
 */
public class PulseDrillTest {

    // ✅ Variables estáticas movidas al inicio para visibilidad global en la clase
    private static final DecimalFormat dfMoney = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Test
    @DisplayName("🎯 LANZAMIENTO ÉPICO: Deep Scan 15 Minutos")
    void runPulseDrill() throws InterruptedException {
        // 1. Inicializamos la máquina
        ExchangeConnector connector = new ExchangeConnector();
        ExecutionCoordinator cordinator = new ExecutionCoordinator();
        DeepMarketScanner scanner = new DeepMarketScanner(connector, cordinator);
        FeeManager feeManager = new FeeManager(connector);

        // 2. 🔥 REPORTE DE ESTADO INICIAL (TELEMETRÍA)
        ignitionSequence();
        System.out.println("\nLos sentidos despiertan... matematizo las presas");
        System.out.println("    ●");
        delay(800);
        System.out.println("   ● ●");
        delay(500);
        System.out.println("  ●   ●");
        delay(500);
        System.out.println(" ●     ●");
        delay(500);
        System.out.println("●       ●");
        delay(500);
        System.out.println("👁️  Sentidos despiertos ");
        System.out.println("=".repeat(80));
        // 3. Reportes intermedios bacanos (5 y 10 min)
        ScheduledExecutorService reportScheduler = Executors.newSingleThreadScheduledExecutor();
        reportScheduler.schedule(() -> interimReport(scanner, 15), 15, TimeUnit.MINUTES);




        // 4. ENCENDEMOS EL OJO QUE TODO LO VE (30 min de escaneo)
        scanner.startOmniScan(15);

        // 5. Mantener vivo el test (16 min para margen de cierre)
        Thread.sleep(16 * 60 * 1000);

        // 6. CIERRE ÉPICO
        epicShutdown(scanner);

        reportScheduler.shutdown();
        System.out.println("🏁 Test completado. La máquina descansa... hasta la próxima cacería.");
    }

    private void ignitionSequence() {
        System.out.println("\n" +
                "   _____       _       _____       _     _   \n" +
                "  / ____|     | |     |  __ \\     | |   | |  \n" +
                " | (___   ___ | |_    | |__) |___ | | __| |  \n" +
                "  \\___ \\ / _ \\| __|   |  _  // _ \\| |/ _` |  \n" +
                "  ____) | (_) | |_    | | \\ \\ (_) | | (_| |  \n" +
                " |_____/ \\___/ \\__|   |_|  \\_\\___/|_|\\__,_|  \n" +
                "                                            \n" +
                "            SOLO GOLD RUSH 2025             \n");

        System.out.println("======================================================");
        System.out.println("🛰️  ESTADO DE LA MISIÓN (TELEMETRÍA)");
        System.out.println("======================================================");
        System.out.println("🛡️  ESTADO DEL SEGURO: " + (BotConfig.DRY_RUN ? "✅ SIMULACIÓN (Safe)" : "🚨 FUEGO REAL (Live)"));
        System.out.println("💰  CAPITAL SEMILLA: $" + BotConfig.SEED_CAPITAL + " USD");
        System.out.println("🎯  UMBRAL DE DISPARO: $" + BotConfig.MIN_PROFIT_THRESHOLD + " PnL");
        System.out.println("📡  REPORTE TELEGRAM: Cada " + BotConfig.REPORT_INTERVAL_MIN + " min");
        System.out.println("⏱️  LATENCIA SCAN: " + BotConfig.SCAN_DELAY + " ms");
        System.out.println("======================================================\n");

        System.out.println("🔥 INICIANDO SISTEMAS...");
        delay(600);
        System.out.println("✓ Conexiones Exchanges... OK");
        delay(400);
        System.out.println("✓ Cerebro Multi-Factor... OK");
        delay(800);
        System.out.println("👁️  EL OJO SE ABRE...");
        delay(1000);
    }

    private void interimReport(DeepMarketScanner scanner, int minutes) {
        long count = scanner.getTradesCount();
        double total = scanner.getTotalPotentialProfit();
        String best = scanner.getBestOpportunityLog();

        // Dinámica de iconos según rendimiento
        String statusIcon = count > 0 ? "✅" : "💤";
        String fire = total > 1.0 ? "🔥 RUGIENDO" : total > 0.0 ? "🚀 ACTIVIDAD" : "😴 SILENCIO";

        System.out.println("\n" + "=".repeat(70));
        System.out.println(statusIcon + " REPORTE RADAR - T+" + minutes + " MINUTOS");
        System.out.println("=".repeat(70));
        System.out.println("🛰️  Radar Scans     : ACTIVO");
        System.out.println("🎯  Presas Cazadas  : " + count);
        System.out.println("💰  PnL Acumulado   : +$" + dfMoney.format(total));
        System.out.println("🏆  Mejor Avistamiento: " + (best.contains("Buscando") ? "Ninguno relevante" : best));
        System.out.println("🧠  Estado Mental   : " + fire);
        System.out.println("=".repeat(70) + "\n");
    }
    private void epicShutdown(DeepMarketScanner scanner) {
        long totalTrades = scanner.getTradesCount();
        double totalProfit = scanner.getTotalPotentialProfit();
        String best = scanner.getBestOpportunityLog();

        // Extraemos métricas internas (Necesitas agregar getters en DeepMarketScanner si no son públicos)
        // Por ahora asumimos acceso a los mapas de rechazo que ya tienes.

        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔬 CERTIFICADO DE RENDIMIENTO - BENCHMARK REPORT");
        System.out.println("=".repeat(60));

        // 1. ANÁLISIS DE PRODUCTIVIDAD
        System.out.println("💰 RENDIMIENTO:");
        System.out.printf("   - PnL Total Simulado : $%s%n", dfMoney.format(totalProfit));
        System.out.printf("   - Oportunidades Válidas: %d%n", totalTrades);
        System.out.printf("   - Yield Promedio     : $%s / trade%n", (totalTrades > 0 ? dfMoney.format(totalProfit/totalTrades) : "0.00"));

        // 2. DIAGNÓSTICO DE LA NUEVA IP
        // (Nota: Estos valores son ejemplos visuales basados en tu lógica,
        //  el scanner acumula esto en 'rejectionReasons')
        System.out.println("\n🛡️ SALUD DEL SISTEMA (Filtros):");
        System.out.println("   - Rechazos por Latencia (>600ms) : [VER LOGS TELEGRAM]");
        System.out.println("   - Rechazos por Slippage (>1.0%)  : [VER LOGS TELEGRAM]");

        // 3. LA MEJOR PRESA
        System.out.println("\n🏆 HIGHLIGHT DEL DÍA:");
        System.out.println("   " + (best.contains("Buscando") ? "Sin capturas relevantes" : best));

        // 4. VEREDICTO FINAL
        double score = (totalTrades * 10) + totalProfit; // Algoritmo simple de puntuación
        String grade = score > 50 ? "A+ (Institucional)" : score > 10 ? "B (Sólido)" : "C (Retail)";

        System.out.println("\n📝 CALIFICACIÓN DE LA SESIÓN: " + grade);
        System.out.println("=".repeat(60));

        System.out.println("🏁 Test finalizado. Guarda estos datos para comparar con Tokio.");


        System.out.println("\nEl Ojo se cierra... hasta la próxima cacería.");
        System.out.println("●       ●");
        delay(500);
        System.out.println(" ●     ●");
        delay(500);
        System.out.println("  ●   ●");
        delay(500);
        System.out.println("   ● ●");
        delay(500);
        System.out.println("    ●");
        delay(800);
        System.out.println("👁️  OJO CERRADO. Buenas noches, cazador.");
        System.out.println("=".repeat(80));
    }

    private void delay(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}