package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import okhttp3.Request;
import okio.Buffer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

/**
 * 🛫 PRE-FLIGHT CHECK SYSTEM
 * Autoridad final de despegue. Ejecuta validaciones críticas de integridad,
 * riesgo y protocolo antes de permitir la conexión a los mercados.
 */
public class PreFlightCheck {

    public static void runSequence(ExchangeConnector connector, RiskManager riskManager) {
        BotLogger.info("🛫 INICIANDO SECUENCIA DE PRE-VUELO (PRE-FLIGHT CHECK)...");

        try {
            // 1. CHEQUEO DE HARDWARE & JVM
            checkSystemSpecs();

            // 2. CHEQUEO DE INTEGRIDAD DE PROTOCOLO (FOK)
            checkFokProtocolIntegrity(connector);

            // 3. CHEQUEO MATEMÁTICO (Monte Carlo)
            checkRiskModels(riskManager);

            // 4. CHEQUEO DE LATENCIA DE RED (Ping rápido)
            checkNetworkHealth(connector);

            BotLogger.info(BotLogger.GREEN + "✅ TODOS LOS SISTEMAS NOMINALES. AUTORIZADO PARA DESPEGUE." + BotLogger.RESET);

        } catch (Exception e) {
            BotLogger.error(BotLogger.RED + "🛑 ABORTANDO INICIO: " + e.getMessage() + BotLogger.RESET);
            BotLogger.error("⚠️ Corrija el fallo crítico antes de reiniciar.");
            System.exit(1); // Muerte súbita del proceso (Safety First)
        }
    }

    // -------------------------------------------------------------------------
    // 1. 🖥️ SYSTEM SPECS (Java 25 Ready?)
    // -------------------------------------------------------------------------
    private static void checkSystemSpecs() {
        String javaVersion = System.getProperty("java.version");
        int cores = Runtime.getRuntime().availableProcessors();
        long memory = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        BotLogger.info(String.format("🖥️ [SYS] Java: %s | Cores: %d | Heap: %d MB", javaVersion, cores, memory));

        // Validación estricta para Virtual Threads
        if (!javaVersion.startsWith("21") && !javaVersion.startsWith("22") && !javaVersion.startsWith("23") && !javaVersion.startsWith("24") && !javaVersion.startsWith("25")) {
            // Permitimos 21+ pero advertimos si no es 25
            if (Integer.parseInt(javaVersion.split("\\.")[0]) < 21) {
                throw new RuntimeException("Requisito de Sistema no cumplido: Se requiere Java 21+ para Virtual Threads.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // 2. 🔐 FOK PROTOCOL INTEGRITY (El que diseñamos en el Test)
    // -------------------------------------------------------------------------
    private static void checkFokProtocolIntegrity(ExchangeConnector connector) throws IOException {
        BotLogger.info("🔐 [PROTO] Verificando construcción de órdenes Fill-or-Kill...");

        // A. Simulación BYBIT
        Request reqBybit = connector.buildOrderRequest("bybit", "BTC-USDT", "BUY", "LIMIT_FOK", 1.0, 50000.0);
        String bodyBybit = bodyToString(reqBybit);
        if (!bodyBybit.contains("\"timeInForce\": \"FOK\"")) {
            throw new RuntimeException("FALLO CRÍTICO FOK: Bybit Connector no está inyectando el flag FOK en el JSON.");
        }

        // B. Simulación BINANCE
        Request reqBinance = connector.buildOrderRequest("binance", "BTC-USDT", "BUY", "LIMIT_FOK", 1.0, 50000.0);
        String urlBinance = reqBinance.url().toString();
        if (!urlBinance.contains("timeInForce=FOK")) {
            throw new RuntimeException("FALLO CRÍTICO FOK: Binance Connector no está inyectando el flag FOK en la URL.");
        }

        BotLogger.info("✅ [PROTO] Integridad FOK confirmada en constructores.");
    }

    // -------------------------------------------------------------------------
    // 3. 🎲 RISK MODEL (Monte Carlo en el arranque)
    // -------------------------------------------------------------------------
// -------------------------------------------------------------------------
    // 3. 🎲 RISK MODEL (Monte Carlo en el arranque)
    // -------------------------------------------------------------------------
    private static void checkRiskModels(RiskManager riskManager) {
        BotLogger.info("🎲 [RISK] Ejecutando Simulación de Monte Carlo (Perfil Configurado)...");
        BotLogger.info(String.format("   -> Target WinRate: %.0f%% | AvgWin: $%.2f | AvgLoss: $%.2f",
                BotConfig.RISK_SIM_WIN_RATE * 100,
                BotConfig.RISK_SIM_AVG_WIN,
                BotConfig.RISK_SIM_AVG_LOSS));

        // ✅ CORRECCIÓN: Usando variables de entorno, no números mágicos
        boolean passed = riskManager.runMonteCarloSimulation(
                BotConfig.RISK_SIM_WIN_RATE,
                BotConfig.RISK_SIM_AVG_WIN,
                BotConfig.RISK_SIM_AVG_LOSS
        );

        if (!passed) {
            throw new RuntimeException("RIESGO INACEPTABLE: Monte Carlo predice ruina > " + (BotConfig.RISK_MC_RUIN_THRESHOLD * 100) + "%.");
        }
        BotLogger.info("✅ [RISK] Modelos matemáticos estables.");
    }
    // -------------------------------------------------------------------------
    // 4. 🌐 NETWORK LATENCY
    // -------------------------------------------------------------------------
    private static void checkNetworkHealth(ExchangeConnector connector) {
        long start = System.currentTimeMillis();
        try {
            // Ping simple a Binance (suele ser la referencia)
            connector.fetchPrice("binance", "BTC-USDT");
            long rtt = System.currentTimeMillis() - start;

            BotLogger.info("🌐 [NET] Latencia de arranque: " + rtt + "ms");

            if (rtt > 1000) {
                BotLogger.warn("⚠️ [NET] Latencia alta detectada en arranque (>1000ms). Operación riesgosa.");
                // Opcional: throw new RuntimeException("Latencia inaceptable para HFT");
            }
        } catch (Exception e) {
            throw new RuntimeException("SIN CONEXIÓN: No se pudo contactar con los exchanges.");
        }
    }

    // Helper para leer body sin consumir memoria excesiva
    private static String bodyToString(Request request) throws IOException {
        if (request.body() == null) return "";
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readString(StandardCharsets.UTF_8);
    }
}