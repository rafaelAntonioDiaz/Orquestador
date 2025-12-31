package com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estrategia de Arbitraje Espacial (Simple).
 * Busca comprar en Exchange A y vender en Exchange B el mismo par.
 */
public class SpatialArbitrageStrategy implements ArbitrageStrategy {

    private final double minSpreadThreshold;

    public SpatialArbitrageStrategy(double minSpreadThreshold) {
        this.minSpreadThreshold = minSpreadThreshold;
    }

    @Override
    public String getName() {
        return "SPATIAL_SIMPLE";
    }

    @Override
    public List<ArbitrageOpportunity> findOpportunities(String asset, Map<String, Map<String, Double>> marketPrices) {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        String pair = asset + "USDT";

        String bestBuyEx = null;
        double minAsk = Double.MAX_VALUE;

        String bestSellEx = null;
        double maxBid = -1.0;

        // 1. Barrido para encontrar el mejor Ask (Compra) y mejor Bid (Venta)
        for (String exchange : marketPrices.keySet()) {
            Map<String, Double> prices = marketPrices.get(exchange);

            // Validación defensiva rápida
            if (prices == null || !prices.containsKey(pair)) continue;

            double price = prices.get(pair);

            // Buscamos el precio más bajo para comprar
            if (price < minAsk) {
                minAsk = price;
                bestBuyEx = exchange;
            }

            // Buscamos el precio más alto para vender
            if (price > maxBid) {
                maxBid = price;
                bestSellEx = exchange;
            }
        }

        // 2. Validación lógica de la oportunidad
        if (bestBuyEx != null && bestSellEx != null && !bestBuyEx.equals(bestSellEx)) {
            // Cálculo del spread bruto: (Venta - Compra) / Compra
            double spread = (maxBid - minAsk) / minAsk;

            if (spread > minSpreadThreshold) {
                opportunities.add(new ArbitrageOpportunity(
                        getName(),
                        asset,
                        bestBuyEx,
                        bestSellEx,
                        minAsk,
                        maxBid,
                        spread,
                        0.0, // Quantity (Aún no calculada)
                        0.0,    // Profit (Aún no calculado)
                        System.currentTimeMillis()
                ));
            }
        }

        return opportunities;
    }
}