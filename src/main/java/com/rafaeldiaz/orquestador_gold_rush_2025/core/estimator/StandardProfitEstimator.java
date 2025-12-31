package com.rafaeldiaz.orquestador_gold_rush_2025.core.estimator;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector.OrderBook;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.MarketDataProvider;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ProfitEstimator;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;

import java.util.List;

public class StandardProfitEstimator implements ProfitEstimator {

    private final FeeManager feeManager;
    private final List<Double> testCapitals;

    public StandardProfitEstimator(FeeManager feeManager, List<Double> testCapitals) {
        this.feeManager = feeManager;
        this.testCapitals = testCapitals;
    }

    @Override
    public ArbitrageOpportunity estimateProfitability(ArbitrageOpportunity rawOpp, BalanceSnapshot balances, MarketDataProvider dataProvider) {
        // 🚦 ROUTER DE ESTRATEGIAS
        if (rawOpp.strategyType().contains("TRIANGULAR")) {
            return estimateTriangular(rawOpp, balances, dataProvider);
        } else {
            return estimateSpatial(rawOpp, balances, dataProvider);
        }
    }

    // ==================================================================================
    // 🌍 VALIDACIÓN ESPACIAL (EXCHANGE A -> EXCHANGE B)
    // ==================================================================================
    private ArbitrageOpportunity estimateSpatial(ArbitrageOpportunity rawOpp, BalanceSnapshot balances, MarketDataProvider dataProvider) {
        String quoteCurrency = "USDT";

        // 1. Check Balance
        double availableBalance = balances.getAvailableBalance(rawOpp.buyExchange(), quoteCurrency);
        if (availableBalance < BotConfig.MIN_ASSET_VALUE_USDT) return null;

        // 2. Fetch Books
        String pair = rawOpp.asset() + quoteCurrency;
        OrderBook bookBuy = dataProvider.getOrderBook(rawOpp.buyExchange(), pair, 20);
        OrderBook bookSell = dataProvider.getOrderBook(rawOpp.sellExchange(), pair, 20);
        if (bookBuy == null || bookSell == null) return null;

        // 3. Fees
        double feeBuyPct = feeManager.getTradingFee(rawOpp.buyExchange(), pair, "TAKER");
        double feeSellPct = feeManager.getTradingFee(rawOpp.sellExchange(), pair, "TAKER");

        ArbitrageOpportunity bestResult = null;
        double maxProfit = -1.0;

        for (Double capital : testCapitals) {
            if (capital > availableBalance) continue;

            // PASO 1: Comprar Asset con USDT (Ask)
            double avgBuyPrice = calculateVwap(bookBuy.asks(), capital);
            if (avgBuyPrice == 0) continue;

            // Cantidad REAL de activo que compramos
            double assetQty = (capital / avgBuyPrice);

            // PASO 2: Vender Asset por USDT (Bid) en otro Exchange
            double revenueGross = calculateRevenueFromSell(bookSell.bids(), assetQty);
            if (revenueGross == 0) continue;
            double effectiveSellPrice = revenueGross / assetQty;

            // Neto
            double costTotal = capital * (1 + feeBuyPct);
            double revenueNet = revenueGross * (1 - feeSellPct);
            double netProfit = revenueNet - costTotal;

            if (netProfit > BotConfig.NORMAL_MIN_PROFIT && netProfit > maxProfit) {
                maxProfit = netProfit;
                // ✅ CORRECCIÓN: Pasamos 'assetQty' calculado, no el original.quantity (que es 0)
                bestResult = createValidatedOpp(rawOpp, avgBuyPrice, effectiveSellPrice, netProfit, assetQty);
            }
        }
        return bestResult;
    }

    // ==================================================================================
    // 📐 VALIDACIÓN TRIANGULAR (USDT -> ALT -> BRIDGE -> USDT)
    // ==================================================================================
    private ArbitrageOpportunity estimateTriangular(ArbitrageOpportunity rawOpp, BalanceSnapshot balances, MarketDataProvider dataProvider) {
        String exchange = rawOpp.buyExchange();
        String asset = rawOpp.asset();
        String bridge = rawOpp.sellExchange();

        // 1. Check Balance
        double availableBalance = balances.getAvailableBalance(exchange, "USDT");
        if (availableBalance < BotConfig.MIN_ASSET_VALUE_USDT) return null;

        // 2. Definir Pares
        String p1 = asset + "USDT";
        String p2 = asset + bridge;
        String p3 = bridge + "USDT";

        // 3. Fetch Books
        OrderBook b1 = dataProvider.getOrderBook(exchange, p1, 20);
        OrderBook b2 = dataProvider.getOrderBook(exchange, p2, 20);
        OrderBook b3 = dataProvider.getOrderBook(exchange, p3, 20);
        if (b1 == null || b2 == null || b3 == null) return null;

        ArbitrageOpportunity bestResult = null;
        double maxProfit = -1.0;

        for (Double capital : testCapitals) {
            if (capital > availableBalance) continue;

            // PASO 1: USDT -> ALT
            double price1 = calculateVwap(b1.asks(), capital);
            if (price1 == 0) continue;
            double qtyAlt = (capital / price1) * 0.999; // Simulación conservadora de fee

            // PASO 2: ALT -> BRIDGE
            double revenueBridgeGross = calculateRevenueFromSell(b2.bids(), qtyAlt);
            if (revenueBridgeGross == 0) continue;
            double qtyBridge = revenueBridgeGross * 0.999;

            // PASO 3: BRIDGE -> USDT
            double revenueUsdtGross = calculateRevenueFromSell(b3.bids(), qtyBridge);
            if (revenueUsdtGross == 0) continue;
            double finalUsdt = revenueUsdtGross * 0.999;

            double netProfit = finalUsdt - capital;

            if (netProfit > BotConfig.MIN_PROFIT_USDT && netProfit > maxProfit) {
                maxProfit = netProfit;
                // ✅ CORRECCIÓN: En triangular, usamos el 'capital' inicial como referencia de tamaño
                bestResult = createValidatedOpp(rawOpp, price1, finalUsdt/capital, netProfit, capital);
            }
        }
        return bestResult;
    }

    // ==================================================================================
    // 🧮 MÉTODOS MATEMÁTICOS DE APOYO
    // ==================================================================================

    /**
     * Crea la oportunidad validada inyectando los datos calculados.
     * @param calculatedQty La cantidad real a operar (Asset units para Spatial, Capital para Triangular)
     */
    private ArbitrageOpportunity createValidatedOpp(ArbitrageOpportunity original, double entry, double exit, double netProfit, double calculatedQty) {
        return new ArbitrageOpportunity(
                original.strategyType(),
                original.asset(),
                original.buyExchange(),
                original.sellExchange(),
                entry,
                exit,
                original.grossSpreadPct(),
                calculatedQty, // ✅ AQUÍ INYECTAMOS LA CANTIDAD REAL
                netProfit,     // ✅ AQUÍ INYECTAMOS EL PROFIT REAL
                original.detectedAtTimestamp()
        );
    }

    private double calculateRevenueFromSell(List<double[]> bids, double assetQtyToSell) {
        if (bids == null || bids.isEmpty()) return 0;
        double remainingQty = assetQtyToSell;
        double totalRevenue = 0;
        for (double[] level : bids) {
            double price = level[0];
            double qtyInLevel = level[1];
            if (qtyInLevel >= remainingQty) {
                totalRevenue += remainingQty * price;
                remainingQty = 0;
                break;
            } else {
                totalRevenue += qtyInLevel * price;
                remainingQty -= qtyInLevel;
            }
        }
        return (remainingQty > 0.000001) ? 0 : totalRevenue;
    }

    private double calculateVwap(List<double[]> asks, double targetVolumeUsdt) {
        if (asks == null || asks.isEmpty()) return 0;
        double remainingVol = targetVolumeUsdt;
        double totalQtyAccumulated = 0;
        for (double[] level : asks) {
            double price = level[0];
            double qtyInLevel = level[1];
            double volInLevel = price * qtyInLevel;
            if (volInLevel >= remainingVol) {
                double neededQty = remainingVol / price;
                totalQtyAccumulated += neededQty;
                remainingVol = 0;
                break;
            } else {
                totalQtyAccumulated += qtyInLevel;
                remainingVol -= volInLevel;
            }
        }
        return (remainingVol > 1.0) ? 0 : targetVolumeUsdt / totalQtyAccumulated;
    }
}