package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import okhttp3.Request;
import okio.Buffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 🛫 PRE-FLIGHT CHECK SYSTEM (SILENT MODE - JAVA 25 CERTIFIED)
 * Autoridad final de despegue.
 * Política: Silencio total a menos que exista un riesgo operativo crítico.
 */
public class PreFlightCheck {

    public static void runSequence(ExchangeConnector connector, RiskManager riskManager) {
        // Único aviso de actividad
        BotLogger.info("🛡️ SYSTEM CHECK: Validando integridad, FOK y modelos de riesgo...");

        try {
            // 1. CHEQUEO DE VERSIÓN JAVA (Crítico para Virtual Threads)
            checkSystemSpecs();

            // 2. CHEQUEO DE PROTOCOLO FOK (Crítico para evitar órdenes limitadas colgadas)
            checkFokProtocolIntegrity(connector);

            // 3. CHEQUEO MATEMÁTICO MONTE CARLO (Crítico para preservación de capital)
            checkRiskModels(riskManager);

            // 4. CHEQUEO DE CONECTIVIDAD Y LATENCIA
            checkNetworkHealth(connector);

            // Si el código llega aquí, todos los sistemas están verdes.
            BotLogger.info("✅ PRE-FLIGHT: PASSED. Sistemas Nominales.");

        } catch (Exception e) {
            BotLogger.error("🔥 FALLO DE PRE-VUELO: " + e.getMessage());
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // 1. 🖥️ SYSTEM SPECS CHECK (JAVA 25 ENFORCED)
    // -------------------------------------------------------------------------
    private static void checkSystemSpecs() {
        String javaVersion = System.getProperty("java.version");

        // LISTA BLANCA EXPLÍCITA:
        // Incluimos "25" explícitamente para soportar "25-ea" o "25-beta"
        // sin que fallen en el parseo numérico.
        boolean isSupported = javaVersion.startsWith("21") ||
                javaVersion.startsWith("22") ||
                javaVersion.startsWith("23") ||
                javaVersion.startsWith("24") ||
                javaVersion.startsWith("25"); // <--- ¡AQUÍ ESTÁ!

        if (!isSupported) {
            // Fallback para versiones futuras (ej. Java 26) o formatos numéricos puros
            try {
                // Usamos split regex para manejar "26.0.1" (dot) o "26-ea" (dash) si fuera necesario
                String majorStr = javaVersion.split("[.-]")[0];
                int major = Integer.parseInt(majorStr);

                if (major < 21) {
                    throw new RuntimeException("Requisito fallido: Java 21+ requerido. Detectado: " + javaVersion);
                }
            } catch (NumberFormatException e) {
                // Si no empieza por 21-25 y no es un número parseable, es un riesgo.
                throw new RuntimeException("Versión de Java no reconocida/soportada: " + javaVersion);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 2. 🔐 FOK PROTOCOL INTEGRITY CHECK
    // -------------------------------------------------------------------------
    private static void checkFokProtocolIntegrity(ExchangeConnector connector) throws IOException {
        // A. Validación Bybit (FOK en JSON body)
        Request reqBybit = connector.buildOrderRequest("bybit", "BTC-USDT", "BUY", "LIMIT_FOK", 1.0, 50000.0);
        if (!bodyToString(reqBybit).contains("\"timeInForce\": \"FOK\"")) {
            throw new RuntimeException("CRITICAL: El conector de Bybit no está inyectando el flag 'FOK'.");
        }

        // B. Validación Binance (FOK en URL Params)
        Request reqBinance = connector.buildOrderRequest("binance", "BTC-USDT", "BUY", "LIMIT_FOK", 1.0, 50000.0);
        if (!reqBinance.url().toString().contains("timeInForce=FOK")) {
            throw new RuntimeException("CRITICAL: El conector de Binance no está inyectando el flag 'FOK'.");
        }
    }

    // -------------------------------------------------------------------------
    // 3. 🎲 RISK MODEL CHECK (Monte Carlo)
    // -------------------------------------------------------------------------
    private static void checkRiskModels(RiskManager riskManager) {
        boolean passed = riskManager.runMonteCarloSimulation(
                BotConfig.RISK_SIM_WIN_RATE,
                BotConfig.RISK_SIM_AVG_WIN,
                BotConfig.RISK_SIM_AVG_LOSS
        );

        if (!passed) {
            throw new RuntimeException("RIESGO ALTO: La simulación de Monte Carlo predice ruina > " +
                    (BotConfig.RISK_MC_RUIN_THRESHOLD * 100) + "%. Ajuste parámetros.");
        }
    }

    // -------------------------------------------------------------------------
    // 4. 🌐 NETWORK LATENCY CHECK
    // -------------------------------------------------------------------------
    private static void checkNetworkHealth(ExchangeConnector connector) {
        long start = System.currentTimeMillis();
        try {
            connector.fetchPrice("binance", "BTC-USDT");
            long rtt = System.currentTimeMillis() - start;

            if (rtt > 1000) {
                BotLogger.warn("⚠️ NETWORK LAG: Latencia de arranque alta (" + rtt + "ms). Riesgo de slippage.");
            }
        } catch (Exception e) {
            throw new RuntimeException("NETWORK DOWN: No se pudo conectar a los exchanges.");
        }
    }

    private static String bodyToString(Request request) throws IOException {
        if (request.body() == null) return "";
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readString(StandardCharsets.UTF_8);
    }
}