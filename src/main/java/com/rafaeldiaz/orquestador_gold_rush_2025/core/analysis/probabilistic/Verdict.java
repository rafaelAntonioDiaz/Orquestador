package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic;

/**
 * ⚖️ VERDICT (RECORD)
 * Estructura pública y compartida. Sin lógica, solo datos.
 */
public record Verdict(
        String signalSource,       // Ej: "LEAD_LAG"
        double confidenceScore,    // 0.0 a 1.0
        double suggestedThreshold  // Spread sugerido
) {}