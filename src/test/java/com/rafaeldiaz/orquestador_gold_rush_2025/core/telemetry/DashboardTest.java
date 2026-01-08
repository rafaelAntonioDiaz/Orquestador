package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import java.util.HashMap;
import java.util.Map;

public class DashboardTest {
    public static void main(String[] args) {
        System.out.println("⚡ GENERANDO DASHBOARD CON EMBUDO Y FILTRO DE TESORO...");

        try {
            DashboardService dashboard = new DashboardService();

            // 1. SIMULAR PRECIOS DE REFERENCIA (Para el filtro de 5 USDT)
            // Esto es necesario para que el nuevo updateInventory no oculte todo
            Map<String, Double> mockPrices = new HashMap<>();
            mockPrices.put("WIFUSDT", 2.50);
            mockPrices.put("PEPEUSDT", 0.00001);
            mockPrices.put("IMXUSDT", 1.50);
            mockPrices.put("USDTUSDT", 1.0);

            // 2. SIMULAR INVENTARIO REAL
            Map<String, Map<String, Double>> mockBalances = new HashMap<>();

            // Cuenta con saldo suficiente (> 5 USDT)
            Map<String, Double> binanceBal = new HashMap<>();
            binanceBal.put("USDT", 100.0);
            binanceBal.put("WIF", 10.0); // 10 * 2.5 = 25 USD (Pasa el filtro)

            // Cuenta con "Polvo" (< 5 USDT) -> Esto debería desaparecer en el Dashboard
            Map<String, Double> kucoinBal = new HashMap<>();
            kucoinBal.put("PEPE", 100.0); // 100 * 0.00001 = 0.001 USD (Invisible)
            kucoinBal.put("BNB", 0.001);  // Los fees (BNB/MX) SIEMPRE se muestran

            mockBalances.put("BINANCE", binanceBal);
            mockBalances.put("KUCOIN", kucoinBal);

            // 3. ACTUALIZAR DASHBOARD
            dashboard.updateInventory(mockBalances, mockPrices);
            dashboard.updateStats(150, 25.40); // 150 ciclos, $25.40 PnL

            // 4. LLENAR EL EMBUDO (AuditTrail)
            // Simulamos detecciones que mueren en distintas etapas
            for(int i=0; i<15; i++)
                dashboard.registrarTraza(new ArbitrageTrace("WIF/USDT", ArbitrageTrace.AuditStage.SCAN_IGNORED, "Spread < 0.12%", 0.05));

            for(int i=0; i<8; i++)
                dashboard.registrarTraza(new ArbitrageTrace("PEPE/USDT", ArbitrageTrace.AuditStage.ORACLE_VETO, "Z-Score bajo", 1.2));

            // Simulamos 3 WINNERS exitosos
            dashboard.registrarTraza(new ArbitrageTrace("WIF/USDT", ArbitrageTrace.AuditStage.EXIT_FILLED, "Binance", "Mexc", 5.20, "Success"));
            dashboard.registrarTraza(new ArbitrageTrace("PEPE/USDT", ArbitrageTrace.AuditStage.EXIT_FILLED, "Bybit", "Binance", 12.80, "Success"));

            // 5. GENERAR HTML
            dashboard.generate();
            System.out.println("✅ LISTO! Abre dashboard.html.");
            System.out.println("   - Verás el Embudo con 3 WINNERS.");
            System.out.println("   - En la Bóveda verás BINANCE (WIF/USDT) y KUCOIN (solo BNB por ser fee).");

        } catch (Exception e) { e.printStackTrace(); }
    }
}