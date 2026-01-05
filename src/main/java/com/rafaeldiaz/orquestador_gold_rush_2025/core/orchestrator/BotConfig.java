package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🧠 CEREBRO DE CONFIGURACIÓN GLOBAL
 * Centraliza los parámetros críticos de la misión.
 */
public class BotConfig {

    private static final Dotenv dotenv = Dotenv.load();

    // ==========================================
    // 1. CONTROL DE OPERACIONES (SEGURIDAD)
    // ==========================================
    public static final boolean DRY_RUN = Boolean.parseBoolean(dotenv.get("BOT_DRY_RUN", "true"));
    public static final double SEED_CAPITAL = Double.parseDouble(dotenv.get("CAPITAL_SEMILLA", "25.0").trim());

    // Lista de capitales para Stress Test
    public static final List<Double> TEST_CAPITALS = Arrays.stream(dotenv.get("TEST_CAPITALS", "10,50,100").split(","))
            .map(String::trim).map(Double::parseDouble).collect(Collectors.toList());

    // ==========================================
    // 2. UMBRALES DE ESTRATEGIA
    // ==========================================
    // Profit Neto Objetivo
    public static final double MIN_PROFIT_USDT = Double.parseDouble(dotenv.get("MIN_PROFIT_USDT", "0.05").trim());
    public static final double EMERGENCY_MIN_PROFIT = Double.parseDouble(dotenv.get("EMERGENCY_MIN_PROFIT", "0.05").trim());
    public static final double NORMAL_MIN_PROFIT = Double.parseDouble(dotenv.get("NORMAL_MIN_PROFIT", "0.15").trim());

    // Spread Mínimo para el Cerebro y Escáner
    public static final double ADVISOR_MIN_SPREAD = Double.parseDouble(dotenv.get("ADVISOR_MIN_SPREAD", "0.05").trim());
    public static final double MIN_SCAN_SPREAD = Double.parseDouble(dotenv.get("MIN_SCAN_SPREAD", "0.0005").trim());

    // ==========================================
    // 3. FÍSICA DE RED Y MERCADO
    // ==========================================
    public static final long MAX_LATENCY_MS = Long.parseLong(dotenv.get("MAX_LATENCY_MS", "1200").trim());
    public static final double MAX_SLIPPAGE = Double.parseDouble(dotenv.get("MAX_SLIPPAGE", "0.01").trim());
    public static final int BOOK_DEPTH = Integer.parseInt(dotenv.get("BOOK_DEPTH","20").trim());

    // Tiempos
    public static final int SCAN_INTERVAL_MS = Integer.parseInt(dotenv.get("SCAN_INTERVAL_MS", "1000").trim());
    public static final int SCAN_DURATION_MIN = Integer.parseInt(dotenv.get("SCAN_DURATION_MIN", "1440").trim());
    public static final int REPORT_INTERVAL_MIN = Integer.parseInt(dotenv.get("REPORT_INTERVAL_MIN", "5").trim());

    // ==========================================
    // 4. ARQUITECTURA DE MERCADO
    // ==========================================
    public static final List<String> BRIDGE_ASSETS = parseList(dotenv.get("BRIDGE_ASSETS", "BTC,ETH,BNB,USDC"));
    public static final List<String> HUNTING_GROUNDS_SEED = parseList(dotenv.get("HUNTING_GROUNDS_SEED", "SOL,XRP,DOGE,PEPE"));

    public static final String ADVISOR_REF_EXCHANGE = dotenv.get("ADVISOR_REF_EXCHANGE", "bybit").trim();
    public static final String STRATEGY_TYPE = dotenv.get("STRATEGY_TYPE", "SPATIAL").trim();

    // ==========================================
    // 5. TENDENCIA Y COORDINACIÓN
    // ==========================================
    public static final int TREND_EMA_PERIOD = Integer.parseInt(dotenv.get("TREND_EMA_PERIOD", "50").trim());
    public static final String TREND_TIMEFRAME = dotenv.get("TREND_TIMEFRAME", "5m").trim();

    public static final long EXECUTION_LOCK_TIMEOUT_MS = Long.parseLong(dotenv.get("EXECUTION_LOCK_TIMEOUT_MS", "20000").trim());
    public static final int  CB_MAX_CONSECUTIVE_FAILURES = Integer.parseInt(dotenv.get("CB_MAX_CONSECUTIVE_FAILURES", "3").trim());
    public static final long CB_QUARANTINE_DURATION_MS = Long.parseLong(dotenv.get("CB_QUARANTINE_DURATION_MS", "300000").trim());

    // ==========================================
    // 6. AUTONOMÍA (CFO)
    // ==========================================
    public static final List<String> ACTIVE_EXCHANGES = List.of(
            "binance",
            "mexc",
            "kucoin",
            "okx",
            "bybit_sub1",
            "bybit_sub2",
            "bybit_sub3"
    );
    public static final List<String> SPATIAL_ACCOUNTS = ACTIVE_EXCHANGES;

    public static final List<String> TRIANGULAR_ACCOUNTS = parseList(dotenv.get("TRIANGULAR_ACCOUNTS", "bybit_sub2,bybit_sub3"));

    public static final boolean AUTO_DISCOVERY = Boolean.parseBoolean(dotenv.get("AUTO_DISCOVERY", "true").trim());
    public static final double MIN_ASSET_VALUE_USDT = Double.parseDouble(dotenv.get("MIN_ASSET_VALUE_USDT", "5.0").trim());

    public static final List<String> FIXED_ASSETS = parseList(dotenv.get("FIXED_ASSETS", "WIF,PEPE"));
    public static final List<String> TRIANGULAR_ASSETS = parseList(dotenv.get("TRIANGULAR_ASSETS", "SOL,XRP,DOGE"));

    public static final double TRADE_SIZE_PERCENT = Double.parseDouble(dotenv.get("TRADE_SIZE_PERCENT", "0.95").trim());
    public static final double IMBALANCE_TOLERANCE = Double.parseDouble(dotenv.get("IMBALANCE_TOLERANCE", "0.20").trim());
    public static final int HEALTH_CHECK_INTERVAL = Integer.parseInt(dotenv.get("HEALTH_CHECK_INTERVAL", "10").trim());
    // ==========================================
    // 7. GESTIÓN DE RIESGO FINANCIERO (CFO)
    // ==========================================
    public static final double RISK_MAX_DAILY_LOSS = Double.parseDouble(dotenv.get("RISK_MAX_DAILY_LOSS", "0.02").trim()); // 2%
    public static final double RISK_MAX_DRAWDOWN = Double.parseDouble(dotenv.get("RISK_MAX_DRAWDOWN", "0.08").trim());     // 8%

    public static final int RISK_MAX_CONSECUTIVE_LOSSES = Integer.parseInt(dotenv.get("RISK_MAX_CONSECUTIVE_LOSSES", "5").trim());
    public static final long RISK_STREAK_PAUSE_MS = Long.parseLong(dotenv.get("RISK_STREAK_PAUSE_MS", "3600000").trim()); // 1 hora

    // 🎲 PARÁMETROS DE SIMULACIÓN (MONTE CARLO)
    public static final double RISK_MC_RUIN_THRESHOLD = 0.05; // 5% Probabilidad máxima de ruina aceptada

    public static final double RISK_SIM_WIN_RATE = Double.parseDouble(dotenv.get("RISK_SIM_WIN_RATE", "0.80"));
    public static final double RISK_SIM_AVG_WIN = Double.parseDouble(dotenv.get("RISK_SIM_AVG_WIN", "2.0"));
    public static final double RISK_SIM_AVG_LOSS = Double.parseDouble(dotenv.get("RISK_SIM_AVG_LOSS", "2.0"));

    //  -------------------------------------------------------------------------
    // 🎲 RISK MODEL (Monte Carlo en el arranque)
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
    // ==========================================
    // 8. ORÁCULO PROBABILÍSTICO (CORTEX & ORACLE)
    // ==========================================
    // Memoria: Cuántos ticks del pasado recordamos (ej: 50 ticks * 1 seg = 50 segs)
    public static final int ORACLE_HISTORY_SIZE = Integer.parseInt(dotenv.get("ORACLE_HISTORY_SIZE", "50").trim());

    // Ventana de Tiempo para Lead-Lag (ej: 5 ticks = 5 segundos atrás para comparar velocidad)
    public static final int ORACLE_LEAD_LAG_TICKS = Integer.parseInt(dotenv.get("ORACLE_LEAD_LAG_TICKS", "5").trim());

    // Umbral de Z-Score para Mean Reversion (ej: 3.0 sigmas)
    public static final double ORACLE_Z_SCORE_THRESHOLD = Double.parseDouble(dotenv.get("ORACLE_Z_SCORE_THRESHOLD", "3.0").trim());

    // Confianza mínima para activar el modo agresivo (0.0 - 1.0)
    public static final double ORACLE_MIN_CONFIDENCE = Double.parseDouble(dotenv.get("ORACLE_MIN_CONFIDENCE", "0.80").trim());

    // Spread Agresivo: Si el oráculo aprueba, bajamos la exigencia a esto (ej: 0.10%)
    public static final double ORACLE_AGGRESSIVE_SPREAD = Double.parseDouble(dotenv.get("ORACLE_AGGRESSIVE_SPREAD", "0.001").trim());

    // =========================================================================
    // 🛠️ HERRAMIENTAS Y PARSERS
    // =========================================================================

    private static List<String> parseList(String csv) {
        if (csv == null || csv.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(csv.split(",")) // Split simple por coma
                .map(String::trim)
                .collect(Collectors.toList());
    }

    public static boolean isSpatialStrategy() {
        return "SPATIAL".equalsIgnoreCase(STRATEGY_TYPE);
    }

    /**
     * 📸 RÓTULO DE AUDITORÍA COMPLETA (FULL ENVIRONMENT STATUS)
     * Genera un String masivo con CADA variable del sistema para el CSV.
     */
    public static String getFullEnvironmentStatus() {
        return new StringBuilder()
                .append("[SEGURIDAD] DRY_RUN=").append(DRY_RUN)
                .append("; CAP=").append(SEED_CAPITAL)
                .append("; TEST_CAPS=").append(TEST_CAPITALS)

                .append(" | [UMBRALES] MIN_USD=").append(MIN_PROFIT_USDT)
                .append("; PROF_NORM=").append(NORMAL_MIN_PROFIT)
                .append("; PROF_EMERG=").append(EMERGENCY_MIN_PROFIT)

                .append(" | [FISICA] LAT_MAX=").append(MAX_LATENCY_MS)
                .append("; SLIP_MAX=").append(MAX_SLIPPAGE)
                .append("; DEPTH=").append(BOOK_DEPTH)
                .append("; INTERVAL=").append(SCAN_INTERVAL_MS)

                .append(" | [ARQ] EXCH=").append(ACTIVE_EXCHANGES)
                .append("; BRIDGE=").append(BRIDGE_ASSETS)
                .append("; HUNTING=").append(HUNTING_GROUNDS_SEED)
                .append("; ADVISOR=").append(ADVISOR_REF_EXCHANGE)
                .append("; STRAT=").append(STRATEGY_TYPE)

                .append(" | [CFO] AUTO=").append(AUTO_DISCOVERY)
                .append("; FIXED=").append(FIXED_ASSETS)
                .append("; IMBALANCE=").append(IMBALANCE_TOLERANCE)
                .append("; SIZE_PCT=").append(TRADE_SIZE_PERCENT)
                .toString();
    }

    /** ---      Eliminar en produccion y reemplazar por los parámetros del .env
     * Son meétodos para poder probar las clases aislándolas del.env
     **/

    // Puente para Tests
    public static List<String> getActiveExchanges() {
        return ACTIVE_EXCHANGES;
    }

    //   Para el spread en Tests
    public static double getMinScanSpread() {
        return MIN_SCAN_SPREAD;
    }
    // Para simular los activos puente en Test
    public static List<String> getBridgeAssets() {
        return BRIDGE_ASSETS;
    }
    // --- AGREGAR AL FINAL DE BotConfig.java ---

    public static double getRiskMaxDailyLoss() { return RISK_MAX_DAILY_LOSS; }
    public static double getRiskMaxDrawdown() { return RISK_MAX_DRAWDOWN; }
    public static int getRiskMaxConsecutiveLosses() { return RISK_MAX_CONSECUTIVE_LOSSES; }
    public static long getRiskStreakPauseMs() { return RISK_STREAK_PAUSE_MS; }
    public static double getRiskMcRuinThreshold() { return RISK_MC_RUIN_THRESHOLD; }

}