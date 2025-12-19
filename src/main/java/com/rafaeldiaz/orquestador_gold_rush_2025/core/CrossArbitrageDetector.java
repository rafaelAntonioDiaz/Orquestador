package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cerebro del Arbitraje Cross-Exchange.
 * Compara precios entre exchanges, calcula costos REALES (Fees + Gas) y dispara.
 */
public class CrossArbitrageDetector {

    private final Map<String, Map<String, Double>> priceCache = new ConcurrentHashMap<>();
    private final CrossTradeExecutor executor;
    private final FeeManager feeManager;

    // Mínimo beneficio NETO deseado (después de pagar todos los fees)
    private static final double MIN_NET_PROFIT_USD = 1.0;

    // Constructor Actualizado: Recibe el Conector para inicializar el FeeManager Vivo
    public CrossArbitrageDetector(CrossTradeExecutor executor, ExchangeConnector connector) {
        this.executor = executor;
        this.feeManager = new FeeManager(connector); // Ahora sí se instancia correctamente
    }

    public void onPriceUpdate(String exchange, String pair, double price) {
        priceCache.computeIfAbsent(exchange, k -> new ConcurrentHashMap<>()).put(pair, price);
        checkOpportunities(exchange, pair, price);
    }

    private void checkOpportunities(String sourceExchange, String pair, double sourcePrice) {
        for (String targetExchange : priceCache.keySet()) {
            if (targetExchange.equals(sourceExchange)) continue;

            Map<String, Double> targetPrices = priceCache.get(targetExchange);
            if (targetPrices == null) continue;

            Double targetPrice = targetPrices.get(pair);
            if (targetPrice == null) continue;

            evaluateAndFire(sourceExchange, targetExchange, pair, sourcePrice, targetPrice);
            evaluateAndFire(targetExchange, sourceExchange, pair, targetPrice, sourcePrice);
        }
    }

    private void evaluateAndFire(String buyExchange, String sellExchange, String pair, double buyPrice, double sellPrice) {
        double rawDiff = sellPrice - buyPrice;
        if (rawDiff <= 0) return;

        // Simulamos una operación con $100 USD para normalizar el cálculo y ver si es rentable
        double tradeSize = 100.0;
        double grossProfit = (rawDiff / buyPrice) * tradeSize; // Ganancia bruta en $
        double grossPercent = (rawDiff / buyPrice) * 100.0;

        // ¿Hay pulso de mercado? (Filtro preliminar: > 0.3% bruto)
        if (grossPercent > 0.3) {

            // 🔥 CÁLCULO DE COSTOS REALES (Trading + Retiro)
            // Pasamos 'sourcePrice' para evitar una llamada HTTP extra de 400ms
            // buyPrice es el precio actual del activo en el exchange de origen,
            // perfecto para calcular cuánto valen esos satoshis de fee en USD.
            double totalCost = feeManager.calculateCrossCost(buyExchange, sellExchange, pair, tradeSize, buyPrice);

            double netProfit = grossProfit - totalCost;

            String logMsg = String.format("👀 PULSO: %s->%s (%s) | Spread: %.2f%% ($%.2f) | Costo Total: $%.2f | Neto: $%.2f",
                    buyExchange, sellExchange, pair, grossPercent, grossProfit, totalCost, netProfit);

            BotLogger.info(logMsg);

            // GATILLO FINAL: Solo si queda dinero limpio en la mesa ($1 USD)
            if (netProfit > MIN_NET_PROFIT_USD) {
                String fireMsg = "🚨 EJECUTANDO CROSS! Oportunidad Real Validada: " + logMsg;
                BotLogger.warn(fireMsg);
                BotLogger.sendTelegram(fireMsg);

                // Ejecutamos la orden real
                executor.executeCrossTrade(buyExchange, sellExchange, pair, buyPrice, sellPrice);

                // Limpiamos caché de estos precios para evitar rebotes inmediatos
                // (Opcional, depende de la estrategia de frecuencia)
            }
        }
    }
}