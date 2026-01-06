package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import java.util.HashMap;

public class DashboardTest {
    public static void main(String[] args) {
        System.out.println("⚡ GENERANDO DASHBOARD CON EMBUDO VISUAL...");

        try {
            DashboardService dashboard = new DashboardService();
            // 1. INYECTAR LA CONFIGURACIÓN DE PRUEBA (Lo que pediste)
            dashboard.updateConfigHeader(
                    0.0012,   // Spread (0.12%)
                    120,      // Max Latency
                    0.05,     // Max Risk
                    "SPATIAL_ARBITRAGE",
                    300.00    // Capital
            );

            System.out.println("✅ Configuración inyectada en Logs Tácticos.");
            // 1. LLENAMOS EL EMBUDO CON DATOS MASIVOS (Loop)
            // Simulamos 20 oportunidades detectadas
            for(int i=0; i<10; i++) {
                dashboard.registrarTraza(new ArbitrageTrace("BTC/USDT", ArbitrageTrace.AuditStage.SCAN_IGNORED, "Spread bajo", 0.01));
            }
            // 5 Mueren por Oráculo/Estrategia
            for(int i=0; i<5; i++) {
                dashboard.registrarTraza(new ArbitrageTrace("ETH/USDT", ArbitrageTrace.AuditStage.ORACLE_VETO, "Riesgo alto", 0.4));
            }
            // 3 Mueren por Latencia (Red)
            for(int i=0; i<3; i++) {
                dashboard.registrarTraza(new ArbitrageTrace("SOL/USDT", ArbitrageTrace.AuditStage.LATENCY_TIMEOUT, "Red lenta", 150));
            }
            // 2 Llegan a Ejecución (WINNERS)
            dashboard.registrarTraza(new ArbitrageTrace("PEPE/USDT", ArbitrageTrace.AuditStage.EXIT_FILLED, "Binance", "Kraken", 15.50, "Success"));
            dashboard.registrarTraza(new ArbitrageTrace("WIF/USDT", ArbitrageTrace.AuditStage.EXIT_FILLED, "Kucoin", "Mexc", 8.20, "Success"));

            dashboard.generate();
            System.out.println("✅ LISTO! Abre dashboard.html y mira el gráfico de barras 'EMBUDO'.");

        } catch (Exception e) { e.printStackTrace(); }
    }
}