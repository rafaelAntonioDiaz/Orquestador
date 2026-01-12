package com.rafaeldiaz.orquestador_gold_rush_2025.model;

// LatencyBreakdown.java
public record LatencyBreakdown(
        long timestamp,
        long netInUs,   // Ingesta Red
        long feeCalcUs, // Cálculo Fees
        long logicUs,   // Tu lógica Java
        long cfoUs,     // Reserva Saldo
        long netOutUs   // Disparo Red
) {}