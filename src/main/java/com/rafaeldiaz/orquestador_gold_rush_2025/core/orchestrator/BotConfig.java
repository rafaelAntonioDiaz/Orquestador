package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 🧠 CEREBRO DE CONFIGURACIÓN GLOBAL
 * Centraliza los parámetros críticos de la misión.
 */
public class BotConfig {
    private static final Dotenv dotenv = Dotenv.load();

    // Estado del Seguro
    public static final boolean DRY_RUN = Boolean.parseBoolean(dotenv.get("BOT_DRY_RUN", "true"));

    // Capital Maestro
    public static final double SEED_CAPITAL = Double.parseDouble(dotenv.get("CAPITAL_SEMILLA", "15.0"));

    // Gatillo de Rentabilidad
    public static final double MIN_PROFIT_THRESHOLD = Double.parseDouble(dotenv.get("MIN_PROFIT_USDT", "-0.30"));

    // Latencia de Escaneo
    public static final int SCAN_DELAY = Integer.parseInt(dotenv.get("SCAN_INTERVAL_MS", "3000"));

    //  Frecuencia del reporte
    public static final int REPORT_INTERVAL_MIN = Integer.parseInt(dotenv.get("REPORT_INTERVAL_MIN", "5"));

    // ✅ NUEVOS CONTROLES CENTRALIZADOS
    // Por defecto 500ms si no está en el .env (Bastante permisivo)
    public static final long MAX_LATENCY_MS = Long.parseLong(dotenv.get("MAX_LATENCY_MS", "500"));

    // Por defecto 1% (0.01) si no está en el .env
    public static final double MAX_SLIPPAGE = Double.parseDouble(dotenv.get("MAX_SLIPPAGE", "0.01"));

    public static final List<Double> TEST_CAPITALS = Arrays.stream(dotenv.get("TEST_CAPITALS", "10,100,200,400,800,1600,2800").split(","))
            .map(String::trim)              // Quitamos espacios
            .map(Double::parseDouble)       // Convertimos a Double
            .collect(Collectors.toList());
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
            dotenv.get("ADVISOR_REF_EXCHANGE", "bybit");

    // Para el CEREBRO (Selector): ¿Qué monedas están "vivas"?
    // Usamos "ADVISOR_MIN_SPREAD" del .env
    public static final double ADVISOR_MIN_SPREAD =
            Double.parseDouble(dotenv.get("ADVISOR_MIN_SPREAD", "0.05"));
    // Para el ESCÁNER (Ejecutor): ¿Qué triangulación da dinero?
    //  Usamos "MIN_SCAN_SPREAD" del .env
    public static final double MIN_SCAN_SPREAD = Double.parseDouble(dotenv.get("MIN_SCAN_SPREAD", "0.0005"));
}