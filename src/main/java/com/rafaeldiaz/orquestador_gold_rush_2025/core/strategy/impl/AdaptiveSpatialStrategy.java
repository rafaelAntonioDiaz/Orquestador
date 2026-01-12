package com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.DecisionAuditor;

import java.util.*;

/**
 * 🎯 SPATIAL STRATEGY v2.1 (ADAPTIVE & ROBUST)
 * - Usa umbrales BASE ultra-bajos (0.05%) para detectar TODO.
 * - Null-Safe: Funciona incluso si el CFO no está disponible (modo test).
 * - Trazabilidad: Integra sensores del DecisionAuditor.
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

        // 1️⃣ VALIDACIÓN ROBUSTA (Fix para Tests y Null Safety)
        // Integración: Si estamos en Test (cfo null), asumimos que todo exchange con precio es válido.
        Set<String> validExchanges;

        if (cfo != null) {
            // Modo Producción: Usamos lo que diga el CFO (Inventario real)
            validExchanges = cfo.getValidExchangesForAsset(asset);
        } else {
            // Modo Test/Fallback: Simulamos validez basada en disponibilidad de precio
            validExchanges = new HashSet<>();
            for (String ex : globalPrices.keySet()) {
                if (globalPrices.get(ex).containsKey(asset + "USDT")) {
                    validExchanges.add(ex);
                }
            }
        }

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

                // 📸 SENSOR ADAPTATIVE + LÓGICA DE NEGOCIO INTEGRADA
                if (spreadPct < DETECTION_THRESHOLD) {
                    // Si el spread es positivo pero muy bajo, solo logueamos el rechazo
                    if (spreadPct > 0) {
                        DecisionAuditor.log(
                                getName(), asset, buyEx + "->" + sellEx, spreadPct, 0.0,
                                "RADAR", "RECHAZADO", "Spread menor a umbral adaptativo");
                    }
                } else {
                    // ✅ CUMPLE EL UMBRAL: Logueamos CANDIDATO y Agregamos
                    DecisionAuditor.log(
                            getName(), asset, buyEx + "->" + sellEx, spreadPct, 0.0,
                            "RADAR", "CANDIDATO", "Detectado por Adaptive Logic");

                    // 📦 CREAMOS LA OPORTUNIDAD CRUDA
                    opportunities.add(new ArbitrageOpportunity(
                            getName(), // Usamos el nombre dinámico de la clase
                            asset,
                            buyEx,
                            sellEx,
                            buyPrice,
                            sellPrice,
                            spreadPct,
                            0.0, // Cantidad calculada por ProfitEstimator
                            0.0, // Profit calculado por ProfitEstimator
                            timestamp,
                            0.5, // Score provisional
                            "RAW_DETECTION"
                    ));
                }
            }
        }
        opportunities = prioritizeMexcRoutes(opportunities);
        return opportunities;
    }
    /**
     * 🎯 OPTIMIZADOR MEXC-FIRST CASCADE
     *
     * Prioriza rutas en este orden:
     * 1. MEXC ↔ MEXC (0% fees)
     * 2. MEXC → Otro (0% + X%)
     * 3. Otro → MEXC (X% + 0%)
     * 4. Otro ↔ Otro (X% + Y%)
     *
     * @param opportunities Lista original de oportunidades
     * @return Lista ordenada por costo de fees (menor a mayor)
     */
    private List<ArbitrageOpportunity> prioritizeMexcRoutes(List<ArbitrageOpportunity> opportunities) {
        // Separamos en 4 buckets según la lógica cascade
        List<ArbitrageOpportunity> tier1 = new ArrayList<>(); // MEXC ↔ MEXC
        List<ArbitrageOpportunity> tier2 = new ArrayList<>(); // MEXC → Otro
        List<ArbitrageOpportunity> tier3 = new ArrayList<>(); // Otro → MEXC
        List<ArbitrageOpportunity> tier4 = new ArrayList<>(); // Otro ↔ Otro

        for (ArbitrageOpportunity opp : opportunities) {
            String buy = opp.buyExchange();
            String sell = opp.sellExchange();

            // Tier 1: Ambos son MEXC (imposible por diseño, pero lo dejamos por completitud)
            if (buy.equals("mexc") && sell.equals("mexc")) {
                tier1.add(opp);
            }
            // Tier 2: MEXC compra (Maker = 0%)
            else if (buy.equals("mexc")) {
                tier2.add(opp);
            }
            // Tier 3: MEXC vende (Taker = 0%)
            else if (sell.equals("mexc")) {
                tier3.add(opp);
            }
            // Tier 4: Sin MEXC
            else {
                tier4.add(opp);
            }
        }

        // Ordenamos cada tier por spread (mayor a menor) dentro de su categoría
        Comparator<ArbitrageOpportunity> bySpread =
                (o1, o2) -> Double.compare(o2.grossSpreadPct(), o1.grossSpreadPct());

        tier1.sort(bySpread);
        tier2.sort(bySpread);
        tier3.sort(bySpread);
        tier4.sort(bySpread);

        // Concatenamos en orden de prioridad
        List<ArbitrageOpportunity> result = new ArrayList<>();
        result.addAll(tier1);
        result.addAll(tier2);
        result.addAll(tier3);
        result.addAll(tier4);

        return result;
    }
}