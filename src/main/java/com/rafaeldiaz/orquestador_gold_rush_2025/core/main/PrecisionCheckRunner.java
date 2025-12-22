package com.rafaeldiaz.orquestador_gold_rush_2025.core.main;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.text.DecimalFormat;

/**
 * 📏 DIAGNÓSTICO DE PRECISIÓN (V2: COBERTURA TOTAL)
 * Verifica Binance, Bybit, MEXC y KuCoin.
 */
public class PrecisionCheckRunner {

    // Variable estática para uso global
    private static final DecimalFormat df = new DecimalFormat("0.00000000");

    public static void main(String[] args) {
        BotLogger.info("🔬 INICIANDO DIAGNÓSTICO DE CALIBRACIÓN (FULL SPECTRUM)...");

        ExchangeConnector connector = new ExchangeConnector();

        // 1. BINANCE (Referencia)
        testPair(connector, "binance", "SOLUSDT", 224.0);

        // 2. BYBIT (Confirmación de parche V5)
        testPair(connector, "bybit", "BTCUSDT", 500.0);

        // 3. MEXC (Debe comportarse igual que Binance)
        // Probamos con MX (Token nativo) y XRP (barato)
        testPair(connector, "mexc", "MXUSDT", 20.0);
        testPair(connector, "mexc", "XRPUSDT", 20.0);

        // 4. KUCOIN (Lógica única 'baseIncrement')
        // Kucoin suele usar guión "BTC-USDT", pero el conector lo maneja automático.
        testPair(connector, "kucoin", "KCSUSDT", 20.0);
        testPair(connector, "kucoin", "DOGEUSDT", 20.0);

        BotLogger.info("🏁 DIAGNÓSTICO FINALIZADO.");
        System.exit(0);
    }

    private static void testPair(ExchangeConnector connector, String exchange, String pair, double capital) {
        System.out.println("\n------------------------------------------------");
        System.out.println("🧪 PROBANDO: " + pair + " en " + exchange.toUpperCase());

        // 1. Obtener Precio
        double price = connector.fetchPrice(exchange, pair);
        System.out.println("   💵 Precio Actual: " + price);

        if (price <= 0) {
            System.out.println("   ❌ FALLO: No se pudo obtener precio. (¿Par existe en " + exchange + "?)");
            return;
        }

        // 2. Obtener StepSize (La prueba de fuego)
        double stepSize = connector.getStepSize(exchange, pair);
        System.out.println("   📏 StepSize (Regla del Exchange): " + df.format(stepSize));

        // 3. Simular Cálculo
        double rawQty = capital / price;
        System.out.println("   🧮 Cantidad Cruda: " + rawQty);

        // 4. Normalizar
        double normalizedQty = normalizeQuantity(rawQty, stepSize);
        System.out.println("   ✅ Cantidad Normalizada: " + df.format(normalizedQty));

        // 5. Validación
        double residue = normalizedQty % stepSize;
        // Tolerancia a error de punto flotante (0.00000001)
        boolean isClean = residue < 0.00000001 || Math.abs(residue - stepSize) < 0.00000001;

        if (normalizedQty > 0 && isClean) {
            System.out.println("   🟢 PRUEBA: ÉXITO");
        } else {
            System.out.println("   🔴 PRUEBA: PELIGRO (Residuo: " + df.format(residue) + ")");
        }
    }

    private static double normalizeQuantity(double rawQty, double stepSize) {
        if (stepSize == 0) return rawQty;
        double steps = Math.floor(rawQty / stepSize);
        return steps * stepSize;
    }
}