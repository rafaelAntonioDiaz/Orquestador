package com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces;

import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import java.util.List;
import java.util.Map;

public interface ArbitrageStrategy {
    /**
     * Nombre de la estrategia para logs y métricas (ej: "Spatial-Spread-V1")
     */
    String getName();

    /**
     * Analiza el mercado y retorna oportunidades potenciales.
     * NO ejecuta, NO valida balance detallado, solo encuentra la anomalía matemática.
     *
     * @param marketPrices Mapa de [Exchange -> [Symbol -> Price]]
     * @param asset El activo pivot (ej: "BTC")
     */
    List<ArbitrageOpportunity> findOpportunities(
            String asset,
            Map<String, Map<String, Double>> marketPrices
    );
}