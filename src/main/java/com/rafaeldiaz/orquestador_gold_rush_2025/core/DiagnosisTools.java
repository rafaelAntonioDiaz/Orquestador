package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.text.DecimalFormat;

/**
 * 🧰 DIAGNOSIS TOOLS 2.1 (FIXED)
 * Incluye: Compatibilidad con el nuevo FeeManager optimizado (5 params).
 */
public class DiagnosisTools {

    public static void main(String[] args) throws InterruptedException {
        BotLogger.info("🧪 INICIANDO PROTOCOLO DE PRUEBAS 2.1...");

        ExchangeConnector connector = new ExchangeConnector();
        FeeManager feeManager = new FeeManager(connector);

        // ---------------------------------------------------------
        // 🔊 PRUEBA 2: EL ECO (CACHE WARM-UP)
        // ---------------------------------------------------------
        BotLogger.info("\n=== 🔊 TEST 2: EL ECO (CACHE vs API) ===");
        String[] coins = {"BTC", "ETH", "SOL", "XRP", "DOGE", "AVAX", "PEPE"};

        // --- VUELTA 1: FRÍA (Consulta API real para fees) ---
        BotLogger.info("❄️ Vuelta 1 (Caché Fría - Consultando Red):");
        long start1 = System.currentTimeMillis();
        for (String coin : coins) {
            // FIX: Agregamos 50000.0 como precio dummy para satisfacer la firma del método
            feeManager.calculateCrossCost("bybit_sub1", "binance", coin + "USDT", 100.0, 50000.0);
            System.out.print("📡 ");
        }
        long end1 = System.currentTimeMillis();
        BotLogger.info("\n⏱️ Tiempo Vuelta 1: " + (end1 - start1) + "ms (Lento, es normal).");

        BotLogger.info("...Esperando 1 segundo...");
        Thread.sleep(1000);

        // --- VUELTA 2: CALIENTE (Memoria RAM) ---
        BotLogger.info("🔥 Vuelta 2 (Caché Caliente - Memoria RAM):");
        long start2 = System.currentTimeMillis();
        for (String coin : coins) {
            // FIX: Agregamos 50000.0 aquí también
            feeManager.calculateCrossCost("bybit_sub1", "binance", coin + "USDT", 100.0, 50000.0);
            System.out.print("⚡ ");
        }
        long end2 = System.currentTimeMillis();
        BotLogger.info("\n⏱️ Tiempo Vuelta 2: " + (end2 - start2) + "ms.");

        if ((end2 - start2) < 50) {
            BotLogger.info("✅ RESULTADO: El sistema vuela. Latencia CERO confirmada.");
        } else {
            BotLogger.warn("⚠️ ALERTA: La caché sigue lenta (" + (end2 - start2) + "ms). Revisar lógica.");
        }

        Thread.sleep(2000);

        // ---------------------------------------------------------
        // 🔬 PRUEBA 4: EL MICROSCOPIO (PRECISIÓN DECIMAL)
        // ---------------------------------------------------------
        BotLogger.info("\n=== 🔬 TEST 4: EL MICROSCOPIO (PRECISIÓN DECIMAL) ===");

        String pair = "PEPEUSDT";
        double investment = 15.0; // Invertir $15

        BotLogger.info("Escenario: Comprar $" + investment + " de " + pair);
        double currentPrice = connector.fetchPrice("bybit_sub1", pair);

        if (currentPrice > 0) {
            BotLogger.info("Precio Real Mercado: " + String.format("%.8f", currentPrice));

            double rawQty = investment / currentPrice;
            BotLogger.info("Cantidad Bruta (Java): " + rawQty);

            double formattedQty = Math.floor(rawQty);
            DecimalFormat df = new DecimalFormat("0");
            df.setMaximumFractionDigits(0);
            String jsonQty = df.format(formattedQty);

            BotLogger.info("Payload a enviar: " + jsonQty);

            if (rawQty > 0 && !jsonQty.contains("E")) {
                BotLogger.info("✅ FORMATO VÁLIDO: El JSON saldrá limpio.");
            } else {
                BotLogger.error("❌ ERROR DE FORMATO.");
            }
        } else {
            BotLogger.error("❌ No se pudo obtener precio para la prueba.");
        }

        BotLogger.info("\n🏁 DIAGNÓSTICO 2.1 FINALIZADO.");
    }
}