package com.rafaeldiaz.orquestador_gold_rush_2025.model;

/**
 * 📦 OrderResult (Versión 5.0 - Auditoría Financiera)
 * Contiene los datos crudos para calcular el precio promedio real.
 */
public record OrderResult(
        String orderId,
        String status,              // "FILLED", "PARTIALLY_FILLED", "CANCELED"
        double originalQty,         // Lo que pedimos (ej: 100 PEPE)
        double executedQty,         // Lo que realmente nos dieron (ej: 100 PEPE)
        double cummulativeQuoteQty, // 💰 CRÍTICO: Total USDT gastado/recibido (ej: $5.10)
        double limitPrice,          // El precio límite (si fue LIMIT, sino 0)
        double feePaid,             // Comisión pagada
        String feeAsset             // Moneda de la comisión (BNB, USDT, PEPE)
) {

    /**
     * ✅ Helper: ¿Se llenó la orden completa?
     */
    public boolean isFilled() {
        return "FILLED".equalsIgnoreCase(status);
    }

    /**
     * 💰 CÁLCULO DE PRECIO PROMEDIO REAL
     * Divide el dinero movido entre las monedas obtenidas.
     * Vital para órdenes MARKET donde el precio solicitado es 0.
     */
    public double averagePrice() {
        if (executedQty > 0 && cummulativeQuoteQty > 0) {
            return cummulativeQuoteQty / executedQty;
        }
        // Fallback: Si no hay ejecución (Dry Run), usamos limitPrice si existe
        return limitPrice > 0 ? limitPrice : 0.0;
    }
}