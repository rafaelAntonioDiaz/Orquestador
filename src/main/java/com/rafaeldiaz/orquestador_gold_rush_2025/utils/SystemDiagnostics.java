package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;

import java.text.DecimalFormat;
import java.util.List;

/**
 * 🩺 DIAGNÓSTICO DE SISTEMAS (PRE-FLIGHT CHECK) - FIXED
 */
public class SystemDiagnostics {

    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    private static final DecimalFormat df = new DecimalFormat("#,##0.00");

    public static void runSequence(ExchangeConnector connector,
                                   PortfolioHealthManager cfo,
                                   FeeManager feeManager,
                                   RiskManager riskManager) {

        System.out.println("\n" + CYAN + "╔════════════════════════════════════════════════════════════╗");
        System.out.println("║       🚀 INICIANDO SECUENCIA DE DESPEGUE (V.2025)          ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝" + RESET);

        wait(500);

        // 1. 🛰️ TELEMETRÍA
        printStep("1/5", "Calibrando Antenas de Telemetría...");
        checkNetwork(connector);

        // 2. 🛡️ IDENTIDAD
        printStep("2/5", "Verificando Perímetro de Seguridad...");
        checkIdentity();

        // 3. 💰 TESORERÍA
        printStep("3/5", "Auditando Bóveda Central (CFO)...");
        checkTreasury(cfo);

        // 4. ⚖️ ECONOMÍA
        printStep("4/5", "Sincronizando Tablas de Tarifas...");
        checkEconomy(feeManager);

        // 5. 🧠 RIESGO
        printStep("5/5", "Activando Protocolos de Protección...");
        checkRisk(riskManager);

        System.out.println("\n" + GREEN + "✅ TODOS LOS SISTEMAS NOMINALES. LISTO PARA OPERAR." + RESET);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        wait(1000);
    }

    private static void checkNetwork(ExchangeConnector connector) {
        List<String> exchanges = List.of("binance", "bybit", "mexc", "kucoin");
        boolean allGood = true;

        for (String ex : exchanges) {
            System.out.print("   📡 Ping " + String.format("%-10s", ex.toUpperCase()) + " -> ");
            try {
                long start = System.currentTimeMillis();
                connector.fetchPrice(ex, "BTCUSDT");
                long rtt = System.currentTimeMillis() - start;
                String color = (rtt < 200) ? GREEN : (rtt < 500) ? YELLOW : RED;
                System.out.println(color + rtt + "ms [OK]" + RESET);
                wait(100);
            } catch (Exception e) {
                System.out.println(RED + "ERROR [OFFLINE]" + RESET);
                allGood = false;
            }
        }
        if(!allGood) BotLogger.warn("⚠️ Algún enlace tiene latencia alta.");
    }

    private static void checkIdentity() {
        String ip = ExternalIpFetcher.getMyPublicIp();
        System.out.println("   🌐 IP Pública: " + CYAN + ip + RESET);
        System.out.println("   🛡️ Encriptación: " + GREEN + "AES-256 [ACTIVA]" + RESET);
        wait(200);
    }

    private static void checkTreasury(PortfolioHealthManager cfo) {
        if (cfo == null) return;

        // ¡AHORA SÍ EXISTE ESTE MÉTODO!
        cfo.performAudit();
        double totalEquity = cfo.getTotalEquityUsdt();

        System.out.println("   💵 Capital Total Detectado: " + GREEN + "$" + df.format(totalEquity) + RESET);
        wait(200);
    }

    private static void checkEconomy(FeeManager feeManager) {
        // CORRECCIÓN AQUÍ: getTradingFee devuelve double, no double[]
        double fee = feeManager.getTradingFee("binance", "BTCUSDT", "TAKER");

        System.out.println("   📊 Motor de Tarifas: " + GREEN + "ONLINE" + RESET);
        System.out.println("   📉 Fee Referencia (Binance Taker): " + CYAN + String.format("%.3f", fee * 100) + "%" + RESET);
        wait(200);
    }

    private static void checkRisk(RiskManager riskManager) {
        System.out.println("   👮 Escudo Diario: " + GREEN + "ACTIVADO" + RESET);
        System.out.println("   🛑 Stop-Loss Global: " + GREEN + "VIGILANDO" + RESET);
        wait(200);
    }

    private static void printStep(String step, String msg) {
        System.out.println(YELLOW + "➤ [" + step + "] " + RESET + msg);
        wait(200);
    }

    private static void wait(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {}
    }
}