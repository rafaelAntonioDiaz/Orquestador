package com.rafaeldiaz.orquestador_gold_rush_2025.model;

/**
 * Modelo de Datos enriquecido para soportar la Tríada Probabilística.
 */
public record ArbitrageOpportunity(
        String strategyType,      // "SPATIAL_SIMPLE", "TRIANGULAR", "ORACLE_LEAD_LAG"
        String asset,
        String buyExchange,
        String sellExchange,
        double priceEntry,
        double priceExit,
        double grossSpreadPct,
        double quantity,
        double expectedProfit,
        long detectedAtTimestamp,

        // --- NUEVOS CAMPOS (V4.5 Oracle) ---
        double probabilityScore,  // 1.0 = Certeza, 0.85 = Alta Probabilidad
        String signalSource       // "HARD_MATH" o "LEAD_LAG", "MEAN_REV"
) {
    // Constructor Canónico Simplificado (Compatibilidad hacia atrás)
    // Asigna valores por defecto para estrategias viejas
    public ArbitrageOpportunity(String strategyType, String asset, String buyExchange, String sellExchange,
                                double priceEntry, double priceExit, double grossSpreadPct,
                                double quantity, double expectedProfit, long detectedAtTimestamp) {
        this(strategyType, asset, buyExchange, sellExchange, priceEntry, priceExit, grossSpreadPct,
                quantity, expectedProfit, detectedAtTimestamp, 1.0, "HARD_MATH");
    }

    public String getPair() {
        return asset + "USDT";
    }
}