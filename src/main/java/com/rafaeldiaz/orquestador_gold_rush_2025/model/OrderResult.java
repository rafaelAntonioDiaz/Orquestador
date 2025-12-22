package com.rafaeldiaz.orquestador_gold_rush_2025.model;

// 📦 EL CERTIFICADO DE HECHOS (Sin inferencias)
public record OrderResult(
        String orderId,
        String status,      // "FILLED", "PARTIALLY_FILLED", "CANCELED"
        double executedQty, // La cantidad REAL que nos dieron
        double avgPrice,    // El precio REAL promedio ponderado
        double feePaid,     // La comisión REAL cobrada
        String feeAsset     // En qué moneda nos cobraron (ej: BNB o USDT)
) {
    public boolean isFilled() { return "FILLED".equalsIgnoreCase(status); }
}