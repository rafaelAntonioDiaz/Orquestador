package com.rafaeldiaz.orquestador_gold_rush_2025.core.estimator;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector.OrderBook;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.MarketDataProvider;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ProfitEstimator;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.DecisionAuditor;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * 🧮 STANDARD PROFIT ESTIMATOR (CLEAN ARCHITECTURE)
 * - Delega 100% de la lógica de costos al FeeManager.
 * - Mantiene la agresividad HFT (Profit > 0).
 */
public class StandardProfitEstimator implements ProfitEstimator {

    private final FeeManager feeManager;
    private final PortfolioHealthManager cfo;
    private final List<Double> testCapitals;

    public StandardProfitEstimator(FeeManager feeManager, PortfolioHealthManager cfo, List<Double> testCapitals) {
        this.feeManager = feeManager;
        this.cfo = cfo;

        // 🌊 CASCADA FORZADA: Mayor a Menor
        List<Double> sortedCaps = new ArrayList<>(testCapitals);
        sortedCaps.sort(Collections.reverseOrder());
        this.testCapitals = sortedCaps;
    }

    @Override
    public ArbitrageOpportunity estimateProfitability(ArbitrageOpportunity rawOpp, BalanceSnapshot balances, MarketDataProvider dataProvider) {
        if (rawOpp.strategyType().contains("TRIANGULAR")) {
            return estimateTriangular(rawOpp, balances, dataProvider);
        } else {
            return estimateSpatial(rawOpp, balances, dataProvider);
        }
    }

    private ArbitrageOpportunity estimateSpatial(ArbitrageOpportunity rawOpp, BalanceSnapshot balances, MarketDataProvider dataProvider) {
        String quoteCurrency = "USDT";
        String pair = rawOpp.asset() + quoteCurrency;

        // 🔍 DIAGNÓSTICO 1: BALANCE FÍSICO
        double availableBalance = balances.getAvailableBalance(rawOpp.buyExchange(), quoteCurrency);
        if (availableBalance < 5.0) {
            BotLogger.warn("⛔ RECHAZO [Balance Físico]: Solo tienes $" + availableBalance + " en " + rawOpp.buyExchange() + ". Mínimo requerido $5.0");
            return null;
        }

        // 🔍 DIAGNÓSTICO 2: LIBRO DE ORDENES
        OrderBook bookBuy = dataProvider.getOrderBook(rawOpp.buyExchange(), pair, BotConfig.BOOK_DEPTH);
        OrderBook bookSell = dataProvider.getOrderBook(rawOpp.sellExchange(), pair, BotConfig.BOOK_DEPTH);

        if (bookBuy == null || bookBuy.asks().isEmpty()) {
            BotLogger.warn("⛔ RECHAZO [Data]: Libro de COMPRA vacío o nulo en " + rawOpp.buyExchange() + " para " + pair);
            return null;
        }
        if (bookSell == null || bookSell.bids().isEmpty()) {
            BotLogger.warn("⛔ RECHAZO [Data]: Libro de VENTA vacío o nulo en " + rawOpp.sellExchange() + " para " + pair);
            return null;
        }

        // 3. CONSULTA AL FEE MANAGER
        double feeBuyPct = feeManager.getTradingFee(rawOpp.buyExchange(), pair, "TAKER");
        double feeSellPct = feeManager.getTradingFee(rawOpp.sellExchange(), pair, "TAKER");

        double maxProfit = -9999.0;
        boolean cfoRejectionLogged = false;

        for (Double capital : testCapitals) {
            if (capital > availableBalance) continue; // No logueamos esto, es obvio

            // 🔍 DIAGNÓSTICO 3: EL CFO (Saldo Virtual)
            double virtualAvailable = cfo.getVirtualAvailableBalance(rawOpp.buyExchange(), quoteCurrency);
            if (virtualAvailable < capital) {
                if (!cfoRejectionLogged) {
                    BotLogger.warn("⛔ RECHAZO [CFO]: Tienes saldo físico, pero el Virtual está ocupado/reservado. Físico: " + availableBalance + " | Virtual: " + virtualAvailable);
                    cfoRejectionLogged = true;
                }
                continue;
            }

            double avgBuyPrice = calculateVwap(bookBuy.asks(), capital);
            if (avgBuyPrice == 0) {
                BotLogger.warn("⚠️ VWAP Fallido: No hay liquidez suficiente en ASK para $" + capital);
                continue;
            }

            double assetQty = (capital / avgBuyPrice);
            double revenueGross = calculateRevenueFromSell(bookSell.bids(), assetQty);
            if (revenueGross == 0) continue;

            double effectiveSellPrice = revenueGross / assetQty;

            // NETO
            double costTotal = capital * (1 + feeBuyPct);
            double revenueNet = revenueGross * (1 - feeSellPct);
            double netProfit = revenueNet - costTotal;

            if (netProfit > maxProfit) maxProfit = netProfit;

            // 🔍 DIAGNÓSTICO 4: RENTABILIDAD
            if (netProfit > 0) {
                if (cfo.tryReserveFunds(rawOpp.buyExchange(), "USDT", capital)) {
                    BotLogger.info("✅ ¡DISPARO APROBADO! Profit: " + netProfit + " | Capital: " + capital);
                    return createValidatedOpp(rawOpp, avgBuyPrice, effectiveSellPrice, netProfit, assetQty);
                } else {
                    BotLogger.error("❌ FALLO CRÍTICO: El CFO rechazó la reserva en el último segundo.");
                }
            } else {
                // Opcional: Descomentar si quieres ver por qué fallan las que dan pérdida
                // BotLogger.debug("📉 Rechazo Matemático: Profit $" + String.format("%.4f", netProfit) + " con capital $" + capital);
            }
        }

        return null;
    }
    // ==================================================================================
    // 📐 VALIDACIÓN TRIANGULAR  (USDT -> ALT -> BRIDGE -> USDT)
    // ==================================================================================
    private ArbitrageOpportunity estimateTriangular(ArbitrageOpportunity rawOpp, BalanceSnapshot balances, MarketDataProvider dataProvider) {
        String exchange = rawOpp.buyExchange();
        String asset = rawOpp.asset();
        String bridge = rawOpp.sellExchange(); // En triangular, 'sellExchange' suele ser el bridge currency

        double availableBalance = balances.getAvailableBalance(exchange, "USDT");
        if (availableBalance < BotConfig.getMinAssetValueUsdt()) return null;

        String p1 = asset + "USDT";
        String p2 = asset + bridge;
        String p3 = bridge + "USDT"; // Asumiendo ruta: USDT -> ASSET -> BRIDGE -> USDT

        // OBTENER FEES REALES DEL FEEMANAGER
        double fee1 = feeManager.getTradingFee(exchange, p1, "TAKER");
        double fee2 = feeManager.getTradingFee(exchange, p2, "TAKER");
        double fee3 = feeManager.getTradingFee(exchange, p3, "TAKER");

        OrderBook b1 = dataProvider.getOrderBook(exchange, p1, 20);
        OrderBook b2 = dataProvider.getOrderBook(exchange, p2, 20);
        OrderBook b3 = dataProvider.getOrderBook(exchange, p3, 20);
        if (b1 == null || b2 == null || b3 == null) return null;

        double maxProfit = -9999.0;

        for (Double capital : testCapitals) {
            if (capital > availableBalance) continue;

            // PASO 1: USDT -> ALT (Compra)
            double price1 = calculateVwap(b1.asks(), capital);
            if (price1 == 0) continue;
            // 🔥 CORRECCIÓN 2: Usar fee real, no 0.999
            double qtyAlt = (capital / price1) * (1 - fee1);

            // PASO 2: ALT -> BRIDGE (Venta)
            // Nota: Aquí asumo que vendes ALT por BRIDGE. Si el par es BRIDGE/ALT, la lógica cambia.
            // Asumiendo par estándar ALT/BRIDGE:
            double revenueBridgeGross = calculateRevenueFromSell(b2.bids(), qtyAlt);
            if (revenueBridgeGross == 0) continue;
            // 🔥 CORRECCIÓN 2: Usar fee real
            double qtyBridge = revenueBridgeGross * (1 - fee2);

            // PASO 3: BRIDGE -> USDT (Venta)
            double revenueUsdtGross = calculateRevenueFromSell(b3.bids(), qtyBridge);
            if (revenueUsdtGross == 0) continue;
            // 🔥 CORRECCIÓN 2: Usar fee real
            double finalUsdt = revenueUsdtGross * (1 - fee3);

            double netProfit = finalUsdt - capital;

            if (netProfit > maxProfit) maxProfit = netProfit;

            // 🔥 CORRECCIÓN 3: Relajamos la restricción
            if (netProfit > BotConfig.MIN_PROFIT_USDT) {

                double virtualAvailable = cfo.getVirtualAvailableBalance(exchange, "USDT");
                if (virtualAvailable < capital) continue;

                if (!cfo.tryReserveFunds(exchange, "USDT", capital)) return null;

                return createValidatedOpp(rawOpp, price1, finalUsdt/capital, netProfit, capital);
            }
        }
        return null;
    }
    // ==================================================================================
    // 🧮 MÉTODOS MATEMÁTICOS DE APOYO (Sin cambios, solo copiados para integridad)
    // ==================================================================================

    private ArbitrageOpportunity createValidatedOpp(ArbitrageOpportunity original, double entry, double exit, double netProfit, double calculatedQty) {
        return new ArbitrageOpportunity(
                original.strategyType(), original.asset(), original.buyExchange(), original.sellExchange(),
                entry, exit, original.grossSpreadPct(), calculatedQty, netProfit, original.detectedAtTimestamp()
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