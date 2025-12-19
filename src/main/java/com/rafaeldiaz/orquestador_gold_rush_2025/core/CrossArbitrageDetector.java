package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Epic 4: Cerebro para Arbitraje entre Exchanges (Cross-Exchange).
 * Compara precios de compra en A vs precios de venta en B.
 */
public class CrossArbitrageDetector {

    // Memoria de precios: Exchange -> Par -> Precio
    // Ejemplo: prices.get("binance").get("BTCUSDT") = 50000.0
    private final Map<String, Map<String, Double>> priceCache = new ConcurrentHashMap<>();

    // Umbral mínimo de ganancia para considerar la oportunidad (0.8% para cubrir fees de retiro)
    private static final double MIN_PROFIT_PERCENT = 0.8;

    /**
     * Recibe actualización de precios y busca discrepancias inmediatas.
     */
    public void onPriceUpdate(String exchange, String pair, double price) {
        // 1. Guardar precio actual
        priceCache.computeIfAbsent(exchange, k -> new ConcurrentHashMap<>()).put(pair, price);

        // 2. Comparar contra todos los otros exchanges conocidos
        checkOpportunities(exchange, pair, price);
    }

    private void checkOpportunities(String sourceExchange, String pair, double sourcePrice) {
        // Iteramos sobre los otros exchanges en memoria
        for (String targetExchange : priceCache.keySet()) {
            if (targetExchange.equals(sourceExchange)) continue; // No comparamos con nosotros mismos

            Double targetPrice = priceCache.get(targetExchange).get(pair);
            if (targetPrice == null) continue; // No tenemos precio del otro lado aún

            // ESCENARIO 1: Comprar en Source (Barato) -> Vender en Target (Caro)
            calculateSpread(sourceExchange, targetExchange, pair, sourcePrice, targetPrice);

            // ESCENARIO 2: Comprar en Target (Barato) -> Vender en Source (Caro)
            calculateSpread(targetExchange, sourceExchange, pair, targetPrice, sourcePrice);
        }
    }

    private void calculateSpread(String buyExchange, String sellExchange, String pair, double buyPrice, double sellPrice) {
        // Fórmula de Profit: (Venta - Compra) / Compra
        double rawDiff = sellPrice - buyPrice;
        double profitPercent = (rawDiff / buyPrice) * 100.0;

        // Filtro de ruido: Solo nos interesan ganancias reales > 0.8%
        if (profitPercent > MIN_PROFIT_PERCENT) {
            String msg = String.format("🚨 CROSS-ARBITRAJE: Comprar %s en %s ($%.2f) -> Vender en %s ($%.2f) | Profit: %.3f%%",
                    pair, buyExchange, buyPrice, sellExchange, sellPrice, profitPercent);

            BotLogger.info(msg);
            // Aquí en el futuro llamaremos al TradeExecutor (Task 4.3)
        }
    }
}