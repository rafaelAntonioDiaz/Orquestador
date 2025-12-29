package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

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
    public static final List<String> ACTIVE_EXCHANGES = parseList(dotenv.get("ACTIVE_EXCHANGES", "binance,bybit,mexc"));
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
    public static final List<String> SPATIAL_ACCOUNTS = parseList(dotenv.get("SPATIAL_ACCOUNTS", "binance,bybit_sub1"));
    public static final List<String> TRIANGULAR_ACCOUNTS = parseList(dotenv.get("TRIANGULAR_ACCOUNTS", "bybit_sub2,bybit_sub3"));

    public static final boolean AUTO_DISCOVERY = Boolean.parseBoolean(dotenv.get("AUTO_DISCOVERY", "true").trim());
    public static final double MIN_ASSET_VALUE_USDT = Double.parseDouble(dotenv.get("MIN_ASSET_VALUE_USDT", "5.0").trim());

    public static final List<String> FIXED_ASSETS = parseList(dotenv.get("FIXED_ASSETS", "WIF,PEPE"));
    public static final List<String> TRIANGULAR_ASSETS = parseList(dotenv.get("TRIANGULAR_ASSETS", "SOL,XRP,DOGE"));

    public static final double TRADE_SIZE_PERCENT = Double.parseDouble(dotenv.get("TRADE_SIZE_PERCENT", "0.95").trim());
    public static final double IMBALANCE_TOLERANCE = Double.parseDouble(dotenv.get("IMBALANCE_TOLERANCE", "0.20").trim());
    public static final int HEALTH_CHECK_INTERVAL = Integer.parseInt(dotenv.get("HEALTH_CHECK_INTERVAL", "10").trim());


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
}