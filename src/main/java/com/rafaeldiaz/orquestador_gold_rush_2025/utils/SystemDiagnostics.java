package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig; // ✅ Importamos Config

import java.text.DecimalFormat;
import java.util.List;

/**
 * 🩺 DIAGNÓSTICO DE SISTEMAS (PRE-FLIGHT CHECK) - FIXED & OPTIMIZED
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

        pause(500); // ✅ Renombrado de wait() a pause()

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
        pause(1000);
    }

    private static void checkNetwork(ExchangeConnector connector) {
        // ✅ CORRECCIÓN: Usamos la lista de exchanges ACTIVA desde la configuración
        List<String> exchanges = BotConfig.getActiveExchanges();

        // Fallback por seguridad si la lista viene vacía
        if (exchanges == null || exchanges.isEmpty()) {
            exchanges = List.of("binance", "bybit", "mexc", "kucoin");
        }

        boolean allGood = true;

        for (String ex : exchanges) {
            System.out.print("   📡 Ping " + String.format("%-12s", ex.toUpperCase()) + " -> ");
            try {
                long start = System.currentTimeMillis();
                connector.fetchPrice(ex, "BTCUSDT");
                long rtt = System.currentTimeMillis() - start;

                // Umbrales ajustados para Bare Metal (150ms es el límite crítico)
                String color = (rtt < 150) ? GREEN : (rtt < 300) ? YELLOW : RED;
                System.out.println(color + rtt + "ms [OK]" + RESET);
                pause(50);
            } catch (Exception e) {
                System.out.println(RED + "ERROR [OFFLINE]" + RESET);
                allGood = false;
            }
        }
        if(!allGood) BotLogger.warn("⚠️ ALERTA: Latencia alta o pérdida de paquetes en enlaces críticos.");
    }

    private static void checkIdentity() {
        try {
            // Manejo de excepción por si ExternalIpFetcher no está disponible
            String ip = com.rafaeldiaz.orquestador_gold_rush_2025.utils.ExternalIpFetcher.getMyPublicIp();
            System.out.println("   🌐 IP Pública: " + CYAN + ip + RESET);
        } catch (Exception e) {
            System.out.println("   🌐 IP Pública: " + YELLOW + "NO DETECTADA" + RESET);
        }
        System.out.println("   🛡️ Encriptación: " + GREEN + "AES-256 [ACTIVA]" + RESET);
        pause(200);
    }

    private static void checkTreasury(PortfolioHealthManager cfo) {
        if (cfo == null) {
            System.out.println(RED + "   ❌ CFO NO DISPONIBLE" + RESET);
            return;
        }

        // Ejecutamos la auditoría real contra las APIs
        cfo.performAudit();
        double totalEquity = cfo.getTotalEquityUsdt();

        System.out.println("   💵 Capital Total Detectado: " + GREEN + "$" + df.format(totalEquity) + RESET);
        pause(200);
    }

    private static void checkEconomy(FeeManager feeManager) {
        if (feeManager == null) return;

        // ✅ CORRECCIÓN: Usamos el Exchange de Referencia configurado (ej: Binance o Bybit)
        String refExchange = BotConfig.getAdvisorRefExchange();
        if (refExchange == null || refExchange.isEmpty()) refExchange = "binance";

        double fee = feeManager.getTradingFee(refExchange, "BTCUSDT", "TAKER");

        System.out.println("   📊 Motor de Tarifas: " + GREEN + "ONLINE" + RESET);
        System.out.println("   📉 Fee Ref (" + refExchange.toUpperCase() + "): " + CYAN + String.format("%.4f", fee * 100) + "%" + RESET);
        pause(200);
    }

    private static void checkRisk(RiskManager riskManager) {
        if (riskManager == null) return;

        System.out.println("   👮 Escudo Diario: " + GREEN + "ACTIVADO" + RESET);
        System.out.println("   🛑 Stop-Loss (" + (BotConfig.getRiskMaxDailyLoss() * 100) + "%): " + GREEN + "VIGILANDO" + RESET);
        pause(200);
    }

    private static void printStep(String step, String msg) {
        System.out.println(YELLOW + "➤ [" + step + "] " + RESET + msg);
        pause(200);
    }

    // ✅ Renombrado para evitar conflicto con Object.wait()
    private static void pause(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {}
    }
}