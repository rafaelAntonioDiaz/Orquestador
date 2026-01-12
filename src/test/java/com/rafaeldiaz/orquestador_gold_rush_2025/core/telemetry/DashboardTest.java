package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import java.util.HashMap;
import java.util.Map;

public class DashboardTest {
    public static void main(String[] args) {
        System.out.println("🍩 CALIBRANDO LA DONA DE GAP ANALYSIS...");
        System.out.println("🧪 ESCENARIO: Mercado volátil (0.09%) vs Bot Exigente (0.12%)");

        try {
            DashboardService dashboard = new DashboardService();

            // --- 1. CONFIGURACIÓN VISUAL BÁSICA ---
            Map<String, Double> prices = new HashMap<>(); prices.put("SOLUSDT", 145.0);
            Map<String, Map<String, Double>> bals = new HashMap<>();
            Map<String, Double> bnb = new HashMap<>(); bnb.put("USDT", 500.0);
            bals.put("BINANCE", bnb);

            dashboard.updateInventory(bals, prices);
            dashboard.updateStats(300, 0.0); // Aún no hemos ganado mucho porque somos exigentes

            // --- 2. INYECCIÓN DE DATA PARA LA DONA (GAP ANALYSIS) ---

            // ⚫ CASO A: RUIDO DE MERCADO (60% de los datos)
            // Spreads de 0.01% - 0.03%. Esto es basura, está bien rechazarlo.
            // La dona mostrará una base NEGRA saludable.
            for (int i = 0; i < 60; i++) {
                dashboard.registrarTraza(new ArbitrageTrace(
                        "BTC/USDT",
                        ArbitrageTrace.AuditStage.SCAN_IGNORED,
                        "Ruido",
                        0.0003 // 0.03%
                ));
            }

            // 🟡 CASO B: EL DINERO EN LA MESA (35% de los datos) -> ¡EL PODER DE LA DONA!
            // Spreads de 0.09% - 0.11%. Son rentables, pero tu bot pide 0.12%.
            // Esto pintará una sección AMARILLA GRANDE.
            // ALERTA VISUAL: "Estás perdiendo todos estos trades por ser muy fino".
            for (int i = 0; i < 35; i++) {
                dashboard.registrarTraza(new ArbitrageTrace(
                        "SOL/USDT",
                        ArbitrageTrace.AuditStage.SCAN_IGNORED,
                        "Casi...",
                        0.0010 // 0.10% (Oro puro rechazado)
                ));
            }


            // updateRadar ahora pide (Pair, Score, Spread,  Status)
            dashboard.updateRadar("SOL/USDT", 0.92, 0.0025, "🔥 ALTA VOLATILIDAD"); // 12.5M volumen
            dashboard.updateRadar("WIF/USDT", 0.85, 0.0018,  "👀 VIGILANDO"); // 500K volumen
            // --- 3. GENERAR ---
            dashboard.generate();
            Thread.sleep(1000);

            System.out.println("✅ DASHBOARD GENERADO.");
            System.out.println("👉 Abre dashboard.html y mira el gráfico 'GAP ANALYSIS'.");
            System.out.println("👀 INTERPRETACIÓN:");
            System.out.println("   - Si ves mucho AMARILLO: Significa que el mercado te está dando 0.10% a gritos.");
            System.out.println("   - ACCIÓN RECOMENDADA: Bajar MIN_SCAN_SPREAD a 0.0009.");

        } catch (Exception e) { e.printStackTrace(); }
    }
}