package com.rafaeldiaz.orquestador_gold_rush_2025.model;

/**
 * Representa una oportunidad de arbitraje.
 * Se usa en dos estados:
 * 1. Cruda (Detectada por Strategy): quantity=0, expectedProfit=0.
 * 2. Validada (Salida de ProfitEstimator): quantity=Real, expectedProfit=Neto.
 */
public record ArbitrageOpportunity(
        String strategyType,      // "SPATIAL_SIMPLE", "TRIANGULAR_LOOP"
        String asset,             // "BTC"
        String buyExchange,       // "BINANCE"
        String sellExchange,      // "KRAKEN" (o Bridge Asset en Triangular)
        double priceEntry,        // Precio Ask (Compra)
        double priceExit,         // Precio Bid (Venta)
        double grossSpreadPct,    // Spread Bruto
        double quantity,          // <--- NUEVO: Cantidad a operar (Asset units)
        double expectedProfit,    // <--- NUEVO: Profit Neto proyectado ($ USDT)
        long detectedAtTimestamp  // Timestamp
) {
    public String getPair() {
        return asset + "USDT";
    }
}