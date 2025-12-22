package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import io.github.cdimascio.dotenv.Dotenv;

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

}