package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
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
    private static final double MIN_NET_PROFIT_USD = 0.01;

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

        // Si no hay diferencia matemática, ni nos molestamos.
        if (rawDiff <= 0) return;

        double tradeSize = 300.0; // Base de simulación
        double grossProfit = (rawDiff / buyPrice) * tradeSize;
        double grossPercent = (rawDiff / buyPrice) * 100.0;

        // Permitimos calcular CUALQUIER diferencia positiva para ver el log,
        // pero mantenemos el gatillo de ejecución estricto abajo.
        if (grossPercent > 0.0) {

            // Calculamos el costo real (Fee de retiro + Trading Fees)
            double totalCost = feeManager.calculateCrossCost(buyExchange, sellExchange, pair, tradeSize, buyPrice);
            double netProfit = grossProfit - totalCost;

            // Preparamos el mensaje con TODOS los datos financieros
            String logMsg = String.format("📊 ANALISIS: %s->%s (%s) | Bruto: $%.2f (%.3f%%) | Costo Op: $%.2f | Neto Proyectado: $%.2f",
                    buyExchange, sellExchange, pair, grossProfit, grossPercent, totalCost, netProfit);

            // --- EL GATILLO DE ORO (Sin cambios, Estricto) ---
            if (netProfit > MIN_NET_PROFIT_USD) {
                // CASO: ES RENTABLE -> DISPARAMOS
                String fireMsg = "🚨 EJECUTANDO CROSS! Oportunidad Validada: " + logMsg;
                BotLogger.warn(fireMsg);
                BotLogger.sendTelegram(fireMsg);

                executor.executeCrossTrade(buyExchange, sellExchange, pair, buyPrice, sellPrice);

            } else {
                // CASO: NO ES RENTABLE (Lo que pediste ver)
                // Mostramos el valor de la oportunidad que dejamos pasar y por qué.
                BotLogger.info(logMsg + " -> ❌ DESCARTADA (No cumple objetivo $0.01)");
            }
        }
    }}