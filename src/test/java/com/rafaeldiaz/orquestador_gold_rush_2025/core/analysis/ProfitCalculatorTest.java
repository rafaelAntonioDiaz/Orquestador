package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfitCalculatorTest {

    private final ProfitCalculator calculator = new ProfitCalculator();

    @Test
    void testEscenarioRealSolana() {
        System.out.println("🧪 TEST: Simulando Arbitraje SOL (Binance -> Bybit)");

        // DATOS DE MERCADO
        double capital = 300.0;     // $300 USD
        double pBinance = 145.50;   // Compramos aquí (Caro? No, Barato)
        double pBybit = 147.20;     // Vendemos aquí (Caro)

        // DATOS DE COSTOS (Aquí está la clave)
        double feeTaker = 0.001;    // 0.1% (Usuario Normal)
        double networkFee = 0.01;   // 0.01 SOL por retirar (aprox $1.45)

        // EJECUCIÓN
        ProfitCalculator.AnalysisResult result = calculator.calculateCrossTrade(
                capital, pBinance, pBybit,
                feeTaker, feeTaker,
                networkFee
        );

        // REPORTE
        System.out.println("------------------------------------------------");
        System.out.println("📈 Spread de Precios: " + (pBybit - pBinance));
        System.out.println("💵 Ganancia Bruta (Teórica): $" + String.format("%.2f", result.grossProfit()));
        System.out.println("📉 Costos Totales (Fees+Gas): $" + String.format("%.2f", result.totalFees()));
        System.out.println("💰 GANANCIA NETA REAL: $" + String.format("%.4f", result.netProfit()));
        System.out.println("📊 ROI: " + String.format("%.3f%%", result.roiPercent()));
        System.out.println("📝 Breakdown: " + result.breakdown());
        System.out.println("------------------------------------------------");

        // VALIDACIONES (Asserts)
        assertTrue(result.netProfit() < result.grossProfit(), "El neto debe ser menor al bruto");

        // Cálculo manual rápido para validar al ingeniero:
        // Compro: 300 / 145.50 = 2.0618 SOL
        // Fee Compra: 2.0618 * 0.999 = 2.0597 SOL
        // Fee Red: 2.0597 - 0.01 = 2.0497 SOL
        // Vendo: 2.0497 * 147.20 = $301.71
        // Fee Venta: 301.71 * 0.999 = $301.41
        // Neto: 301.41 - 300 = $1.41

        // Verificamos que el calculator de cerca de 1.41
        assertEquals(1.41, result.netProfit(), 0.05, "El cálculo financiero falló por precisión");
    }
}