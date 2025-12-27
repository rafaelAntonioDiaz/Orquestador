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

    public static final int SCAN_DURATION_MIN = 1440;
    private static final Dotenv dotenv = Dotenv.load();

    // Estado del Seguro
    public static final boolean DRY_RUN = Boolean.parseBoolean(dotenv.get("BOT_DRY_RUN", "true"));

    // Capital Maestro
    public static final double SEED_CAPITAL = Double.parseDouble(dotenv.get("CAPITAL_SEMILLA", "15.0").trim());
    public static final List<Double> TEST_CAPITALS = Arrays.stream(dotenv.get("TEST_CAPITALS", "10,100,200,400,800,1600,2800").split(","))
            .map(String::trim)              // Quitamos espacios
            .map(Double::parseDouble)       // Convertimos a Double
            .collect(Collectors.toList());
    // Gatillo de Rentabilidad
    public static final double MIN_PROFIT_THRESHOLD = Double.parseDouble(dotenv.get("MIN_PROFIT_USDT", "-0.30").trim());
    public static final double MIN_PROFIT_USDT = Double.parseDouble(dotenv.get("MIN_PROFIT_USDT", "0.05").trim());
    // Latencia de Escaneo
    public static final int SCAN_DELAY = Integer.parseInt(dotenv.get("SCAN_INTERVAL_MS", "3000").trim());

    //  Frecuencia del reporte
    public static final int REPORT_INTERVAL_MIN = Integer.parseInt(dotenv.get("REPORT_INTERVAL_MIN", "5").trim());

    // ✅ NUEVOS CONTROLES CENTRALIZADOS
    // Por defecto 500ms si no está en el .env (Bastante permisivo)
    public static final long MAX_LATENCY_MS = Long.parseLong(dotenv.get("MAX_LATENCY_MS", "500").trim());

    // Por defecto 1% (0.01) si no está en el .env
    public static final double MAX_SLIPPAGE = Double.parseDouble(dotenv.get("MAX_SLIPPAGE", "0.01").trim());


    // 1. Exchanges Activos
    public static final List<String> ACTIVE_EXCHANGES = parseList(
            dotenv.get("ACTIVE_EXCHANGES", "binance,bybit,mexc,kucoin")
    );
    // 2. Puentes de Triangulación
    public static final List<String> BRIDGE_ASSETS = parseList(
            dotenv.get("BRIDGE_ASSETS", "BTC,ETH,BNB,USDC")
    );
    // 3. Semilla de Caza
    public static final List<String> HUNTING_GROUNDS_SEED = parseList(
            dotenv.get("HUNTING_GROUNDS_SEED", "SOL,XRP,DOGE,PEPE")
    );
    // 🛠️ Helper para limpiar listas CSV
    private static List<String> parseList(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }
    public static final String ADVISOR_REF_EXCHANGE =
            dotenv.get("ADVISOR_REF_EXCHANGE", "bybit").trim();

    // Para el CEREBRO (Selector): ¿Qué monedas están "vivas"?
    // Usamos "ADVISOR_MIN_SPREAD" del .env
    public static final double ADVISOR_MIN_SPREAD =
            Double.parseDouble(dotenv.get("ADVISOR_MIN_SPREAD", "0.05").trim());
    // Para el ESCÁNER (Ejecutor): ¿Qué triangulación da dinero?
    //  Usamos "MIN_SCAN_SPREAD" del .env
    public static final double MIN_SCAN_SPREAD = Double.parseDouble(dotenv.get("MIN_SCAN_SPREAD", "0.0005").trim());
    // ✅ CONFIGURACIÓN DE ESTRATEGIA
    public static final String STRATEGY_TYPE = dotenv.get("STRATEGY_TYPE", "SPATIAL").trim();

    // ✅ CONFIGURACIÓN DEL FILTRO DE TENDENCIA
    public static final int TREND_EMA_PERIOD = Integer.parseInt(dotenv.get("TREND_EMA_PERIOD", "50").trim());
    public static final String TREND_TIMEFRAME = dotenv.get("TREND_TIMEFRAME", "15m").trim();

    // Método auxiliar para saber fácil si es Espacial
    public static boolean isSpatialStrategy() {
        return "SPATIAL".equalsIgnoreCase(STRATEGY_TYPE);
    }
    public static final int BOOK_DEPTH = Integer.parseInt(dotenv.get("BOOK_DEPTH","20").trim());
    // ==========================================
    // 🆕 NUEVAS CONFIGURACIONES DE AUTONOMÍA
    // ==========================================

    public static final List<String> SPATIAL_ACCOUNTS = parseList("SPATIAL_ACCOUNTS", "binance,bybit_sub1");
    public static final List<String> TRIANGULAR_ACCOUNTS = parseList("TRIANGULAR_ACCOUNTS", "bybit_sub2,bybit_sub3");

    public static final boolean AUTO_DISCOVERY = Boolean.parseBoolean(dotenv.get("AUTO_DISCOVERY", "true").trim());
    public static final double MIN_ASSET_VALUE_USDT = Double.parseDouble(dotenv.get("MIN_ASSET_VALUE_USDT", "5.0").trim());

    public static final List<String> FIXED_ASSETS = parseList("FIXED_ASSETS", "WIF,PEPE");
    public static final List<String> TRIANGULAR_ASSETS = parseList("TRIANGULAR_ASSETS", "SOL,XRP,DOGE");

    // Parámetros del CFO
    public static final double TRADE_SIZE_PERCENT = Double.parseDouble(dotenv.get("TRADE_SIZE_PERCENT", "0.95").trim());
    public static final double IMBALANCE_TOLERANCE = Double.parseDouble(dotenv.get("IMBALANCE_TOLERANCE", "0.20").trim());
    public static final double EMERGENCY_MIN_PROFIT = Double.parseDouble(dotenv.get("EMERGENCY_MIN_PROFIT", "0.05").trim());
    public static final double NORMAL_MIN_PROFIT = Double.parseDouble(dotenv.get("NORMAL_MIN_PROFIT", "0.40").trim());
    public static final int HEALTH_CHECK_INTERVAL = Integer.parseInt(dotenv.get("HEALTH_CHECK_INTERVAL", "10").trim());

    // Helper para evitar errores de null en split
    private static List<String> parseList(String key, String defaultVal) {
        String val = dotenv.get(key, defaultVal);
        if (val == null || val.trim().isEmpty()) return Collections.emptyList();
        // Regex "\\s*,\\s*" elimina espacios alrededor de la coma
        return Arrays.asList(val.split("\\s*,\\s*"));
    }
    // Carga de Coordinación
    public static final long EXECUTION_LOCK_TIMEOUT_MS = Long.parseLong(dotenv.get("EXECUTION_LOCK_TIMEOUT_MS", "20000").trim());
    public static final int  CB_MAX_CONSECUTIVE_FAILURES = Integer.parseInt(dotenv.get("CB_MAX_CONSECUTIVE_FAILURES", "3").trim());
    public static final long CB_QUARANTINE_DURATION_MS = Long.parseLong(dotenv.get("CB_QUARANTINE_DURATION_MS", "300000").trim());
}