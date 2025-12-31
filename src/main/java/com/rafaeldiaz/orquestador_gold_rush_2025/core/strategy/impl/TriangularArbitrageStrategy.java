package com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estrategia de Arbitraje Triangular (Intra-Exchange).
 * Detecta ineficiencias en ciclos de 3 activos: USDT -> ALT -> BRIDGE -> USDT.
 */
public class TriangularArbitrageStrategy implements ArbitrageStrategy {

    private final List<String> bridgeAssets; // Ej: ["BTC", "ETH", "BNB"]
    private final double minProfitThreshold; // Ej: 0.003 (0.3%)

    public TriangularArbitrageStrategy(List<String> bridgeAssets, double minProfitThreshold) {
        this.bridgeAssets = bridgeAssets;
        this.minProfitThreshold = minProfitThreshold;
    }

    @Override
    public String getName() {
        return "TRIANGULAR_LOOP_V1";
    }

    @Override
    public List<ArbitrageOpportunity> findOpportunities(String asset, Map<String, Map<String, Double>> marketPrices) {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();

        // El par base siempre es asset + "USDT" (Ej: "LTCUSDT")
        String pairBase = asset + "USDT";

        for (String exchange : marketPrices.keySet()) {
            Map<String, Double> prices = marketPrices.get(exchange);
            if (prices == null || !prices.containsKey(pairBase)) continue;

            double priceBase = prices.get(pairBase); // Precio USDT -> ALT

            // Iteramos sobre los puentes (BTC, ETH...)
            for (String bridge : bridgeAssets) {
                // Construimos los pares del loop
                // 1. Altcoin contra Bridge (Ej: "LTCBTC")
                String pairCross = asset + bridge;
                // 2. Bridge contra USDT (Ej: "BTCUSDT")
                String pairBridge = bridge + "USDT";

                if (prices.containsKey(pairCross) && prices.containsKey(pairBridge)) {
                    double priceCross = prices.get(pairCross);
                    double priceBridge = prices.get(pairBridge);

                    // 🧮 FÓRMULA TRIANGULAR:
                    // Ruta Directa: Comprar ALT con USDT directamente. Costo = priceBase.
                    // Ruta Indirecta: Comprar Bridge con USDT, luego ALT con Bridge.
                    // Pero aquí buscamos el profit del ciclo:
                    // 1. Tengo 1 USDT -> Compro ALT = (1 / priceBase) Unidades ALT
                    // 2. Vendo ALT por Bridge = (Unidades ALT * priceCross) Unidades Bridge
                    // 3. Vendo Bridge por USDT = (Unidades Bridge * priceBridge) USDT Finales

                    double finalUsdt = (1.0 / priceBase) * priceCross * priceBridge;
                    double potentialProfitPct = finalUsdt - 1.0;

                    if (potentialProfitPct > minProfitThreshold) {
                        // 💎 ¡Oportunidad Encontrada!
                        opportunities.add(new ArbitrageOpportunity(
                                getName(),
                                asset,         // Activo Principal (ej: LTC)
                                exchange,      // Exchange (ej: BINANCE)
                                bridge,        // ⚠️ USAMOS ESTE CAMPO PARA EL BRIDGE (ej: BTC)
                                priceBase,     // Precio Entrada (Referencia)
                                priceCross,    // Precio Intermedio (Referencia)
                                potentialProfitPct,
                                0.0,
                                0.0,
                                System.currentTimeMillis()
                        ));
                    }
                }
            }
        }
        return opportunities;
    }
}