package com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;

import java.util.Collections;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estrategia de Arbitraje Espacial (Simple).
 * Busca comprar en Exchange A y vender en Exchange B el mismo par.
 */
public class SpatialArbitrageStrategy implements ArbitrageStrategy {

    private final double minSpreadThreshold;
    private final PortfolioHealthManager cfo;

    // Constructor actualizado para recibir al CFO
    public SpatialArbitrageStrategy(double minSpreadThreshold, PortfolioHealthManager cfo) {
        this.minSpreadThreshold = minSpreadThreshold;
        this.cfo = cfo;
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

        // 0. CONSULTAR AL CFO (Una sola vez antes del bucle)
        // Obtenemos la lista blanca de dónde podemos vender.
        Set<String> validSellers = (cfo != null)
                ? cfo.getValidExchangesForAsset(asset)
                : Collections.emptySet();

        // Si la lista no está vacía Y NO estamos en Dry Run, activamos el "Modo Estricto"
        boolean strictMode = !validSellers.isEmpty() && !BotConfig.DRY_RUN;
        // 1. BARRIDO INTELIGENTE
        for (String exchange : marketPrices.keySet()) {
            Map<String, Double> prices = marketPrices.get(exchange);

            // Validación defensiva
            if (prices == null || !prices.containsKey(pair)) continue;

            double price = prices.get(pair);

            // A) LÓGICA DE COMPRA (ASK): Siempre buscamos el más barato globalmente.
            // (Asumimos que siempre tenemos USDT en todos lados, o el CrossExecutor validará eso después)
            if (price < minAsk) {
                minAsk = price;
                bestBuyEx = exchange;
            }

            // B) LÓGICA DE VENTA (BID): Buscamos el más alto... ¡PERO VÁLIDO!
            // Solo consideramos este exchange como candidato de venta si:
            // 1. Estamos en modo Radar (strictMode = false) -> Aceptamos todo.
            // 2. O estamos en modo Ejecución y el exchange está en la lista blanca.
            boolean isAllowedSeller = !strictMode || validSellers.contains(exchange);

            if (isAllowedSeller && price > maxBid) {
                maxBid = price;
                bestSellEx = exchange;
            }
        }

        // 2. CONSTRUCCIÓN DE LA OPORTUNIDAD
        // Si llegamos aquí, bestSellEx YA ES VÁLIDO (o es null), no hace falta filtrar de nuevo.
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
                        0.0,
                        0.0,
                        System.currentTimeMillis()
                ));
            }
        }
        return opportunities;
    }}