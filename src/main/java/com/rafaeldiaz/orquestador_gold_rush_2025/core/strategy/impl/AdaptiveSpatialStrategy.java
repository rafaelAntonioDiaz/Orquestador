package com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;

import java.util.*;

/**
 * 🎯 SPATIAL STRATEGY v2.0 (ADAPTIVE)
 * - Usa umbrales BASE ultra-bajos (0.05%) para detectar TODO
 * - El Oracle decide qué ejecutar (separación de responsabilidades)
 */
public class AdaptiveSpatialStrategy implements ArbitrageStrategy {

    // 🔻 UMBRAL BASE ULTRA-AGRESIVO (detecta incluso 0.05%)
    // El filtrado real lo hace el Oracle en el Scanner
    private static final double DETECTION_THRESHOLD = 0.0005; // 0.05%

    private final PortfolioHealthManager cfo;

    public AdaptiveSpatialStrategy(PortfolioHealthManager cfo) {
        this.cfo = cfo;
    }

    @Override
    public String getName() {
        return "Spatial-Adaptive-v2.0";
    }

    @Override
    public List<ArbitrageOpportunity> findOpportunities(
            String asset,
            Map<String, Map<String, Double>> globalPrices
    ) {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        // 1️⃣ VALIDACIÓN: ¿Tenemos saldo en este activo?
        Set<String> validExchanges = cfo.getValidExchangesForAsset(asset);
        if (validExchanges.size() < 2) return opportunities; // Necesitamos mínimo 2 cuentas

        // 2️⃣ OPTIMIZACIÓN: Construir mapa de precios de exchanges válidos
        Map<String, Double> assetPrices = new HashMap<>();
        String pair = asset + "USDT";

        for (String ex : validExchanges) {
            Map<String, Double> exPrices = globalPrices.get(ex);
            if (exPrices != null && exPrices.containsKey(pair)) {
                assetPrices.put(ex, exPrices.get(pair));
            }
        }

        if (assetPrices.size() < 2) return opportunities;

        // 3️⃣ ESCANEO COMPLETO (O(n²) pero con n pequeño)
        List<Map.Entry<String, Double>> entries = new ArrayList<>(assetPrices.entrySet());

        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                String exA = entries.get(i).getKey();
                String exB = entries.get(j).getKey();
                double priceA = entries.get(i).getValue();
                double priceB = entries.get(j).getValue();

                // Determinar dirección del arbitraje
                boolean buyA = priceA < priceB;
                String buyEx = buyA ? exA : exB;
                String sellEx = buyA ? exB : exA;
                double buyPrice = buyA ? priceA : priceB;
                double sellPrice = buyA ? priceB : priceA;

                // 🔍 CÁLCULO DE SPREAD BRUTO
                double spreadPct = (sellPrice - buyPrice) / buyPrice;

                // ✅ DETECCIÓN ULTRA-SENSIBLE (sin filtrado aquí)
                if (spreadPct >= DETECTION_THRESHOLD) {
                    // 📦 CREAMOS LA OPORTUNIDAD CRUDA
                    // El Oracle decidirá si es viable o ruido
                    opportunities.add(new ArbitrageOpportunity(
                            "SPATIAL_ADAPTIVE",
                            asset,
                            buyEx,
                            sellEx,
                            buyPrice,
                            sellPrice,
                            spreadPct,
                            0.0, // Cantidad calculada por ProfitEstimator
                            0.0, // Profit calculado por ProfitEstimator
                            timestamp,
                            0.5, // Score provisional (Oracle lo ajustará)
                            "RAW_DETECTION" // Fuente temporal
                    ));
                }
            }
        }

        return opportunities;
    }
}