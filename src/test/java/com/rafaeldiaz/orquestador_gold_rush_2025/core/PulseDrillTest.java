package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 🏎️ PUESTA A PUNTO: Test de Perforación OMNIDIRECCIONAL.
 * Ignición épica, reportes bacanos y cierre espectacular.
 */
public class PulseDrillTest {

    private static final DecimalFormat dfMoney = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Test
    @DisplayName("🎯 LANZAMIENTO ÉPICO: Deep Scan 15 Minutos")
    void runPulseDrill() throws InterruptedException {
        // 1. Inicializamos la máquina
        ExchangeConnector connector = new ExchangeConnector();
        DeepMarketScanner scanner = new DeepMarketScanner(connector);

        // 2. SECUENCIA DE IGNICIÓN ÉPICA
        ignitionSequence();

        // 3. Reportes intermedios bacanos (5 y 10 min)
        ScheduledExecutorService reportScheduler = Executors.newSingleThreadScheduledExecutor();
        reportScheduler.schedule(() -> interimReport(scanner, 5), 5, TimeUnit.MINUTES);
        reportScheduler.schedule(() -> interimReport(scanner, 10), 10, TimeUnit.MINUTES);

        // 4. ENCENDEMOS EL OJO QUE TODO LO VE
        scanner.startOmniScan(15);

        System.out.println("🛰️ Escaneando el multiverso cripto... Observa la consola y siente la caza.\n");

        // 5. Mantener vivo el test (16 min margen)
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

        System.out.println("🔥 INICIANDO SISTEMAS...\n");
        delay(800);
        System.out.println("✓ Conexión Bybit Subs... OK");
        delay(400);
        System.out.println("✓ Conexión MEXC (0% maker)... OK");
        delay(400);
        System.out.println("✓ Conexión Binance... OK");
        delay(400);
        System.out.println("✓ Conexión KuCoin... OK");
        delay(600);
        System.out.println("🧠 Cerebro Multi-Factor cargado");
        delay(800);
        System.out.println("👁️  EL OJO SE ABRE...");
        delay(1000);
        System.out.println("    ●");
        delay(500);
        System.out.println("   ● ●");
        delay(500);
        System.out.println("  ●   ●");
        delay(500);
        System.out.println(" ●     ●");
        delay(500);
        System.out.println("●       ●");
        delay(800);
        System.out.println("\n¡MÁQUINA DESPIERTA! COMENZANDO CAZA OMNIDIRECCIONAL...\n");
    }

    private void interimReport(DeepMarketScanner scanner, int minutes) {
        long count = scanner.getTradesCount();
        double total = scanner.getTotalPotentialProfit();
        String best = scanner.getBestOpportunityLog();

        String fire = total > 10 ? "EN FUEGO 🔥🔥🔥" : total > 0 ? "Calentando motores 🚀" : "Mercado dormido 😴";

        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 REPORTE BACANO A LOS " + minutes + " MINUTOS");
        System.out.println("=".repeat(70));
        System.out.println("Oportunidades analizadas: " + count);
        System.out.println("Potencial acumulado: +$" + dfMoney.format(total));
        System.out.println("Mejor presa hasta ahora: " + best);
        System.out.println("Estado de la máquina: " + fire);
        System.out.println("Sigo cazando en el multiverso...");
        System.out.println("=".repeat(70) + "\n");
    }

    private void epicShutdown(DeepMarketScanner scanner) {
        long count = scanner.getTradesCount();
        double total = scanner.getTotalPotentialProfit();
        String best = scanner.getBestOpportunityLog();

        String finalEmoji = total > 20 ? "💎💎💎" : total > 5 ? "🔥🔥" : "🟢";

        System.out.println("\n" + "=".repeat(80));
        System.out.println(finalEmoji + " CIERRE DE SESIÓN - " + LocalTime.now().format(timeFmt) + " " + finalEmoji);
        System.out.println("=".repeat(80));
        System.out.println("Tiempo de caza: 15 minutos");
        System.out.println("Oportunidades analizadas: " + count);
        System.out.println("Potencial total identificado: +$" + dfMoney.format(total));
        System.out.println("Mejor presa del día:");
        System.out.println(best);
        System.out.println("\n" + (total > 10 ? "¡DÍA ÉPICO! La máquina rugió fuerte hoy 🦁" :
                total > 0 ? "Buena caza, hay terreno fértil 🌱" :
                        "Mercado dormido... mañana será mejor 😴"));
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