package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🧠 CEREBRO DE CONFIGURACIÓN GLOBAL (TOKYO BARE METAL EDITION)
 * Centraliza los parámetros críticos de la misión.
 * FUENTE DE VERDAD: Archivo .env
 */
public class BotConfig {

    private static final Dotenv dotenv = Dotenv.load();

    // ==========================================
    // 1. CONTROL DE OPERACIONES (SEGURIDAD)
    // ==========================================
    // 🛑 false = FUEGO REAL
    public static final boolean DRY_RUN = Boolean.parseBoolean(dotenv.get("BOT_DRY_RUN", "false"));
    public static final double SEED_CAPITAL = Double.parseDouble(dotenv.get("CAPITAL_SEMILLA", "15.0").trim());

    // Lista de capitales para Stress Test (Waterfall Descendente: 20 -> 1)
    public static final List<Double> TEST_CAPITALS = Arrays.stream(dotenv.get("TEST_CAPITALS", "20.0,15.0,13.0,10.0,8.0,6.0,3.0,1.0").split(","))
            .map(String::trim).map(Double::parseDouble).collect(Collectors.toList());

    // ==========================================
    // 2. UMBRALES DE ESTRATEGIA
    // ==========================================
    // Profit Neto Objetivo
    public static final double MIN_PROFIT_USDT = Double.parseDouble(dotenv.get("MIN_PROFIT_USDT", "0.01").trim());
    public static final double EMERGENCY_MIN_PROFIT = Double.parseDouble(dotenv.get("EMERGENCY_MIN_PROFIT", "-0.01").trim());
    public static final double NORMAL_MIN_PROFIT = Double.parseDouble(dotenv.get("NORMAL_MIN_PROFIT", "0.01").trim());

    // Spread Mínimo para el Cerebro y Escáner (Bajado a 0.13% para capturar WIF/PEPE)
    public static final double MIN_SCAN_SPREAD = Double.parseDouble(dotenv.get("MIN_SCAN_SPREAD", "0.0013").trim());

    // ==========================================
    // 3. FÍSICA DE RED Y MERCADO
    // ==========================================
    public static final long MAX_LATENCY_MS = Long.parseLong(dotenv.get("MAX_LATENCY_MS", "75").trim());
    public static final double MAX_SLIPPAGE = Double.parseDouble(dotenv.get("MAX_SLIPPAGE", "0.0025").trim());
    public static final int BOOK_DEPTH = Integer.parseInt(dotenv.get("BOOK_DEPTH","15").trim());

    // Tiempos (Modo HFT: 30ms)
    public static final int SCAN_INTERVAL_MS = Integer.parseInt(dotenv.get("SCAN_INTERVAL_MS", "30").trim());
    public static final int SCAN_DURATION_MIN = Integer.parseInt(dotenv.get("SCAN_DURATION_MIN", "120").trim());
    public static final int REPORT_INTERVAL_MIN = Integer.parseInt(dotenv.get("REPORT_INTERVAL_MIN", "15").trim());

    // ==========================================
    // 4. ARQUITECTURA DE MERCADO
    // ==========================================
    // ⚠️ CAMBIO CRÍTICO: Leemos la lista del .env en lugar de hardcode (binance,mexc,kucoin...)
    public static final List<String> ACTIVE_EXCHANGES = parseList(dotenv.get("ACTIVE_EXCHANGES", "binance,mexc,kucoin,okx,bybit_sub1,bybit_sub2,bybit_sub3"));

    public static final List<String> BRIDGE_ASSETS = parseList(dotenv.get("BRIDGE_ASSETS", "USDT"));
    public static final List<String> HUNTING_GROUNDS_SEED = parseList(dotenv.get("HUNTING_GROUNDS_SEED", "WIF,PEPE,BONK,FLOKI,SHIB,DOGE,IMX"));

    public static final String ADVISOR_REF_EXCHANGE = dotenv.get("ADVISOR_REF_EXCHANGE", "binance").trim();
    public static final String STRATEGY_TYPE = dotenv.get("STRATEGY_TYPE", "SPATIAL").trim();

    // ==========================================
    // 5. TENDENCIA Y COORDINACIÓN
    // ==========================================
    public static final int TREND_EMA_PERIOD = Integer.parseInt(dotenv.get("TREND_EMA_PERIOD", "50").trim());
    public static final String TREND_TIMEFRAME = dotenv.get("TREND_TIMEFRAME", "5m").trim();

    public static final long EXECUTION_LOCK_TIMEOUT_MS = Long.parseLong(dotenv.get("EXECUTION_LOCK_TIMEOUT_MS", "1000").trim());
    public static final int  CB_MAX_CONSECUTIVE_FAILURES = Integer.parseInt(dotenv.get("CB_MAX_CONSECUTIVE_FAILURES", "3").trim());
    public static final long CB_QUARANTINE_DURATION_MS = Long.parseLong(dotenv.get("CB_QUARANTINE_DURATION_MS", "5000").trim());

    // ==========================================
    // 6. AUTONOMÍA (CFO)
    // ==========================================
    public static final List<String> SPATIAL_ACCOUNTS = parseList(dotenv.get("SPATIAL_ACCOUNTS", "binance,mexc,kucoin,okx,bybit_sub1,bybit_sub2,bybit_sub3"));
    public static final List<String> TRIANGULAR_ACCOUNTS = parseList(dotenv.get("TRIANGULAR_ACCOUNTS", "binance"));

    public static final boolean AUTO_DISCOVERY = Boolean.parseBoolean(dotenv.get("AUTO_DISCOVERY", "true").trim());
    public static final double MIN_ASSET_VALUE_USDT = Double.parseDouble(dotenv.get("MIN_ASSET_VALUE_USDT", "5.0").trim());

    public static final List<String> FIXED_ASSETS = parseList(dotenv.get("FIXED_ASSETS", "WIF,PEPE,IMX"));
    // TRIANGULAR_ASSETS removido del .env nuevo, mantenemos fallback por seguridad o eliminamos si no se usa.
    public static final List<String> TRIANGULAR_ASSETS = parseList(dotenv.get("TRIANGULAR_ASSETS", "SOL,XRP,DOGE"));

    public static final double TRADE_SIZE_PERCENT = Double.parseDouble(dotenv.get("TRADE_SIZE_PERCENT", "0.95").trim());
    public static final double IMBALANCE_TOLERANCE = Double.parseDouble(dotenv.get("IMBALANCE_TOLERANCE", "0.40").trim());
    public static final int HEALTH_CHECK_INTERVAL = Integer.parseInt(dotenv.get("HEALTH_CHECK_INTERVAL", "10").trim());

    // ==========================================
    // 7. GESTIÓN DE RIESGO FINANCIERO (CFO)
    // ==========================================
    public static final double RISK_MAX_DAILY_LOSS = Double.parseDouble(dotenv.get("RISK_MAX_DAILY_LOSS", "0.05").trim());
    public static final double RISK_MAX_DRAWDOWN = Double.parseDouble(dotenv.get("RISK_MAX_DRAWDOWN", "0.10").trim());

    public static final int RISK_MAX_CONSECUTIVE_LOSSES = Integer.parseInt(dotenv.get("RISK_MAX_CONSECUTIVE_LOSSES", "3").trim());
    public static final long RISK_STREAK_PAUSE_MS = Long.parseLong(dotenv.get("RISK_STREAK_PAUSE_MS", "600000").trim());

    // 🎲 PARÁMETROS DE SIMULACIÓN (MONTE CARLO)
    public static final double RISK_MC_RUIN_THRESHOLD = Double.parseDouble(dotenv.get("RISK_MC_RUIN_THRESHOLD", "0.05").trim());

    public static final double RISK_SIM_WIN_RATE = Double.parseDouble(dotenv.get("RISK_SIM_WIN_RATE", "0.70"));
    public static final double RISK_SIM_AVG_WIN = Double.parseDouble(dotenv.get("RISK_SIM_AVG_WIN", "0.60"));
    public static final double RISK_SIM_AVG_LOSS = Double.parseDouble(dotenv.get("RISK_SIM_AVG_LOSS", "0.30"));

    //  -------------------------------------------------------------------------
    // 🎲 RISK MODEL CHECKER
    // -------------------------------------------------------------------------
    private static void checkRiskModels(RiskManager riskManager) {
        BotLogger.info("🎲 [RISK] Ejecutando Simulación de Monte Carlo (Perfil TOKYO)...");
        BotLogger.info(String.format("   -> Target WinRate: %.0f%% | AvgWin: $%.2f | AvgLoss: $%.2f",
                BotConfig.RISK_SIM_WIN_RATE * 100,
                BotConfig.RISK_SIM_AVG_WIN,
                BotConfig.RISK_SIM_AVG_LOSS));

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
    // 8. ORÁCULO PROBABILÍSTICO (AGRESIVO)
    // ==========================================
    public static final int ORACLE_HISTORY_SIZE = Integer.parseInt(dotenv.get("ORACLE_HISTORY_SIZE", "50").trim());
    public static final int ORACLE_LEAD_LAG_TICKS = Integer.parseInt(dotenv.get("ORACLE_LEAD_LAG_TICKS", "2").trim());

    // Sensibilidad: Bajamos a 1.2 sigmas
    public static final double ORACLE_Z_SCORE_THRESHOLD = Double.parseDouble(dotenv.get("ORACLE_Z_SCORE_THRESHOLD", "1.2").trim());
    // Confianza: Relajada al 55%
    public static final double ORACLE_MIN_CONFIDENCE = Double.parseDouble(dotenv.get("ORACLE_MIN_CONFIDENCE", "0.55").trim());
    // Spread Agresivo
    public static final double ORACLE_AGGRESSIVE_SPREAD = Double.parseDouble(dotenv.get("ORACLE_AGGRESSIVE_SPREAD", "0.0008").trim());

    // ==========================================
    // 9. LOGÍSTICA DE FONDOS Y FEES
    // ==========================================
    public static final String PREFERRED_TRANSFER_NETWORK = dotenv.get("PREFERRED_TRANSFER_NETWORK", "BEP20").trim();
    public static final boolean MEXC_ZERO_FEE_OVERRIDE = Boolean.parseBoolean(dotenv.get("MEXC_ZERO_FEE_OVERRIDE", "true"));

    // 🔥 NUEVO: VARIABLES ZERO-FEE ZONE DESDE .ENV
    public static final String ZERO_FEE_EXCHANGE = dotenv.get("ZERO_FEE_EXCHANGE", "mexc").trim();
    public static final List<String> ZERO_FEE_ASSETS = parseList(dotenv.get("ZERO_FEE_ASSETS", "WIF,PEPE,BONK,FLOKI,SHIB,DOGE,IMX"));


    // =========================================================================
    // 🛠️ HERRAMIENTAS Y PARSERS
    // =========================================================================

    private static List<String> parseList(String csv) {
        if (csv == null || csv.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    public static boolean isSpatialStrategy() {
        return "SPATIAL".equalsIgnoreCase(STRATEGY_TYPE);
    }

    /**
     * 📸 RÓTULO DE AUDITORÍA COMPLETA
     */
    public static String getFullEnvironmentStatus() {
        return new StringBuilder()
                .append("[SEGURIDAD] DRY_RUN=").append(DRY_RUN)
                .append("; CAP=").append(SEED_CAPITAL)
                .append("; TEST_CAPS=").append(TEST_CAPITALS)

                .append(" | [UMBRALES] MIN_USD=").append(MIN_PROFIT_USDT)
                .append("; SPREAD=").append(MIN_SCAN_SPREAD)

                .append(" | [FISICA] LAT_MAX=").append(MAX_LATENCY_MS)
                .append("; SLIP_MAX=").append(MAX_SLIPPAGE)
                .append("; INTERVAL=").append(SCAN_INTERVAL_MS)

                .append(" | [ARQ] EXCH=").append(ACTIVE_EXCHANGES)
                .append("; HUNTING=").append(HUNTING_GROUNDS_SEED)

                .append(" | [ORACLE] Z=").append(ORACLE_Z_SCORE_THRESHOLD)
                .append("; CONF=").append(ORACLE_MIN_CONFIDENCE)

                .append(" | [LOGISTICA] 0_FEE_ZONE=").append(ZERO_FEE_EXCHANGE)
                .toString();
    }

    // ==========================================
    // GETTERS PARA TESTS (BRIDGE)
    // ==========================================
    public static List<String> getActiveExchanges() { return ACTIVE_EXCHANGES; }
    public static double getMinScanSpread() { return MIN_SCAN_SPREAD; }
    public static List<String> getBridgeAssets() { return BRIDGE_ASSETS; }
    public static double getRiskMaxDailyLoss() { return RISK_MAX_DAILY_LOSS; }
    public static double getMaxLatencyMs(){return MAX_LATENCY_MS;}
    public static double getRiskMaxDrawdown() { return RISK_MAX_DRAWDOWN; }
    public static int getRiskMaxConsecutiveLosses() { return RISK_MAX_CONSECUTIVE_LOSSES; }
    public static long getRiskStreakPauseMs() { return RISK_STREAK_PAUSE_MS; }
    public static double getRiskMcRuinThreshold() { return RISK_MC_RUIN_THRESHOLD; }
    public static int getOracleHistorySize() { return ORACLE_HISTORY_SIZE; }
    public static int getOracleLeadLagTicks() { return ORACLE_LEAD_LAG_TICKS; }
    public static double getOracleZScoreThreshold() { return ORACLE_Z_SCORE_THRESHOLD; }
    public static double getOracleMinConfidence() { return ORACLE_MIN_CONFIDENCE; }
    public static double getOracleAggressiveSpread() { return ORACLE_AGGRESSIVE_SPREAD; }
    public static String getAdvisorRefExchange() { return ADVISOR_REF_EXCHANGE; }
    public static double getMinAssetValueUsdt() {return MIN_ASSET_VALUE_USDT;}
}