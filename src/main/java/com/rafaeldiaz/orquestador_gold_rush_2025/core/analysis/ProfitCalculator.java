package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

/**
 * 🧮 PROFIT CALCULATOR (El Contador)
 * Determina el resultado matemático exacto de una operación Cross-Exchange.
 * No adivina. Calcula.
 */
public class ProfitCalculator {

    // Record para devolver un análisis financiero completo, no solo un número.
    public record AnalysisResult(
            boolean isProfitable,
            double grossProfit,   // Ganancia bruta (Spread puro)
            double totalFees,     // Suma de todos los costos
            double netProfit,     // Lo que te queda en el bolsillo
            double roiPercent,    // Retorno sobre Inversión %
            String breakdown      // Explicación en texto para logs
    ) {}

    /**
     * Calcula la rentabilidad de un ciclo COMPLETO: USDT -> Compra(A) -> Transferencia -> Venta(B) -> USDT
     *
     * @param capitalUSDT    Capital inicial (ej. 200.0)
     * @param buyPrice       Precio en Exchange Origen (Ask)
     * @param sellPrice      Precio en Exchange Destino (Bid)
     * @param buyFeeRate     % Comisión Compra (ej. 0.001 para 0.1%)
     * @param sellFeeRate    % Comisión Venta  (ej. 0.001 para 0.1%)
     * @param networkFeeAsset Costo fijo de retiro en MONEDA (ej. 0.01 SOL)
     * @return AnalysisResult con todos los detalles.
     */
    public AnalysisResult calculateCrossTrade(
            double capitalUSDT,
            double buyPrice,
            double sellPrice,
            double buyFeeRate,
            double sellFeeRate,
            double networkFeeAsset
    ) {
        // 1. FASE DE COMPRA (Exchange A)
        // Pagamos fee sobre el capital o recibimos menos monedas.
        // Asumimos modelo estándar: Fee se descuenta de la moneda comprada.

        double rawCoins = capitalUSDT / buyPrice;          // Monedas teóricas
        double coinsReceived = rawCoins * (1 - buyFeeRate); // Monedas reales tras fee

        // 2. FASE DE TRANSFERENCIA (El Puente)
        // La red cobra un peaje fijo en monedas (ej. 0.01 SOL)
        double coinsArrived = coinsReceived - networkFeeAsset;

        if (coinsArrived <= 0) {
            return new AnalysisResult(false, 0, 0, -capitalUSDT, -100.0, "💀 La red se comió todo el capital.");
        }

        // 3. FASE DE VENTA (Exchange B)
        // Vendemos lo que llegó.
        double grossSaleUSDT = coinsArrived * sellPrice;
        double finalUSDT = grossSaleUSDT * (1 - sellFeeRate); // Descontamos fee de venta

        // --- RESULTADOS ---
        double netProfit = finalUSDT - capitalUSDT;
        double totalCost = capitalUSDT - finalUSDT + (rawCoins * buyPrice * (sellPrice/buyPrice) - rawCoins*buyPrice); // Aprox
        // Simplificamos costo: Lo que dejamos de ganar vs spread perfecto
        double grossTheoretical = (rawCoins * sellPrice) - capitalUSDT; // Si no hubiera fees
        double feesPaidUSD = grossTheoretical - netProfit;

        double roi = (netProfit / capitalUSDT) * 100.0;
        boolean profitable = netProfit > 0.05; // Umbral mínimo de 5 centavos para considerar "Verde"

        String log = String.format(
                "Cap:%.1f | BuyFee:%.1f%% | NetFee:%.4f Asset | SellFee:%.1f%% | 🏁 Final:$%.2f",
                capitalUSDT, buyFeeRate*100, networkFeeAsset, sellFeeRate*100, finalUSDT
        );

        return new AnalysisResult(profitable, grossTheoretical, feesPaidUSD, netProfit, roi, log);
    }
}