package com.rafaeldiaz.orquestador_gold_rush_2025.model;

/**
 * 📦 OrderResult (Versión 5.1 - Con Alias Financiero)
 * Contiene los datos crudos para calcular el precio promedio real.
 */
public record OrderResult(
        String orderId,
        String status,              // "FILLED", "PARTIALLY_FILLED", "CANCELED"
        double originalQty,         // Lo que pedimos (ej: 100 PEPE)
        double executedQty,         // Lo que realmente nos dieron (ej: 100 PEPE)
        double cummulativeQuoteQty, // 💰 CRÍTICO: Total USDT/Bridge gastado o recibido
        double limitPrice,          // El precio límite (si fue LIMIT, sino 0)
        double feePaid,             // Comisión pagada
        String feeAsset             // Moneda de la comisión
) {

    /**
     * ✅ Helper: ¿Se llenó la orden completa?
     */
    public boolean isFilled() {
        return "FILLED".equalsIgnoreCase(status);
    }

    /**
     * 🔗 ALIAS FINANCIERO: Valor Ejecutado.
     * Devuelve el total de moneda cotizada (USDT, BTC, etc.) movida en la operación.
     * Útil para saber exactamente cuánto "Cash" o "Puente" recibimos en una venta.
     */
    public double executedValue() {
        return cummulativeQuoteQty;
    }

    /**
     * 💰 CÁLCULO DE PRECIO PROMEDIO REAL
     */
    public double averagePrice() {
        if (executedQty > 0 && cummulativeQuoteQty > 0) {
            return cummulativeQuoteQty / executedQty;
        }
        return limitPrice > 0 ? limitPrice : 0.0;
    }
}