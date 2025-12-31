package com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces;

import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;

public interface ProfitEstimator {
    /**
     * Calcula la viabilidad financiera real de una oportunidad cruda.
     * * Responsabilidades:
     * 1. Descargar el OrderBook real (si no está en caché).
     * 2. Calcular el VWAP (Volume Weighted Average Price) real según la profundidad.
     * 3. Descontar Fees (Taker/Maker) y Slippage estimado.
     * 4. Verificar si el balance disponible cubre la operación.
     *
     * @return Una nueva instancia de ArbitrageOpportunity con los valores finales (net profit),
     * o NULL si la oportunidad deja de ser rentable tras los cálculos.
     */
    ArbitrageOpportunity estimateProfitability(
            ArbitrageOpportunity rawOpportunity,
            BalanceSnapshot currentBalances,
            MarketDataProvider dataProvider
    );
}