package com.rafaeldiaz.orquestador_gold_rush_2025.test.fixtures;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 🏭 FÁBRICA DE FIXTURES PARA TESTING
 *
 * Proporciona datos realistas y reutilizables para pruebas de arbitraje.
 * Todos los métodos son estáticos y thread-safe.
 *
 * Filosofía de diseño:
 * - Datos deterministas por defecto (reproducibilidad)
 * - Opción de aleatorización controlada (chaos testing)
 * - Valores basados en observaciones reales del mercado cripto
 */
public class ArbitrageTestFixtures {

    // =========================================================================
    // 📊 ORDERBOOK BUILDERS
    // =========================================================================

    /**
     * Crea OrderBook con spread y liquidez configurables.
     *
     * @param basePrice Precio central del mercado
     * @param spreadPercent Spread bid-ask en porcentaje (ej: 0.1 = 0.1%)
     * @param liquidityUSD Liquidez total en USD en los primeros 5 niveles
     * @return OrderBook realista
     */
    public static ExchangeConnector.OrderBook buildOrderBook(
            double basePrice,
            double spreadPercent,
            double liquidityUSD) {

        double halfSpread = (basePrice * spreadPercent / 100.0) / 2.0;
        double bestBid = basePrice - halfSpread;
        double bestAsk = basePrice + halfSpread;

        // Distribución de liquidez: Decae exponencialmente con la distancia
        double[] liquidity = distributeLiquidity(liquidityUSD, 20);

        List<double[]> bids = new ArrayList<>();
        List<double[]> asks = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            // Bids: Precio decrece, liquidez decrece
            double bidPrice = bestBid - (i * 0.01 * basePrice / 100);
            double bidQty = liquidity[i] / bidPrice;
            bids.add(new double[]{bidPrice, bidQty});

            // Asks: Precio crece, liquidez decrece
            double askPrice = bestAsk + (i * 0.01 * basePrice / 100);
            double askQty = liquidity[i] / askPrice;
            asks.add(new double[]{askPrice, askQty});
        }

        return new ExchangeConnector.OrderBook(bids, asks);
    }

    /**
     * Crea OrderBook con liquidez ALTA (mercado saludable)
     */
    public static ExchangeConnector.OrderBook buildHighLiquidityBook(double price) {
        return buildOrderBook(price, 0.05, 500_000.0);
    }

    /**
     * Crea OrderBook con liquidez BAJA (riesgo de slippage)
     */
    public static ExchangeConnector.OrderBook buildLowLiquidityBook(double price) {
        return buildOrderBook(price, 0.5, 10_000.0);
    }

    /**
     * Crea OrderBook con spread AGRESIVO (volátil, buenas oportunidades)
     */
    public static ExchangeConnector.OrderBook buildVolatileBook(double price) {
        return buildOrderBook(price, 1.0, 200_000.0);
    }

    /**
     * Crea OrderBook VACÍO (exchange con problemas)
     */
    public static ExchangeConnector.OrderBook buildEmptyBook() {
        return new ExchangeConnector.OrderBook(
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    // =========================================================================
    // 💰 BALANCE SNAPSHOT BUILDERS
    // =========================================================================

    /**
     * Crea snapshot con balances distribuidos entre exchanges.
     *
     * @param totalCapital Capital total a distribuir en USDT
     * @param exchanges Lista de exchanges activos
     * @return BalanceSnapshot con distribución equitativa
     */
    public static BalanceSnapshot buildDistributedSnapshot(
            double totalCapital,
            List<String> exchanges) {

        Map<String, Map<String, Double>> balances = new HashMap<>();
        double capitalPerExchange = totalCapital / exchanges.size();

        for (String exchange : exchanges) {
            Map<String, Double> assets = new HashMap<>();
            assets.put("USDT", capitalPerExchange);
            assets.put("BTC", 0.001);  // Residuo realista
            assets.put("ETH", 0.01);
            balances.put(exchange, assets);
        }

        return new BalanceSnapshot(balances, System.currentTimeMillis());
    }

    /**
     * Crea snapshot con UN SOLO exchange con fondos.
     */
    public static BalanceSnapshot buildSingleExchangeSnapshot(
            String exchange,
            String asset,
            double amount) {

        Map<String, Map<String, Double>> balances = Map.of(
                exchange, Map.of(asset, amount)
        );

        return new BalanceSnapshot(balances, System.currentTimeMillis());
    }

    /**
     * Crea snapshot con CERO balance (cuenta vacía).
     */
    public static BalanceSnapshot buildEmptySnapshot(List<String> exchanges) {
        Map<String, Map<String, Double>> balances = new HashMap<>();
        for (String exchange : exchanges) {
            balances.put(exchange, Map.of("USDT", 0.0));
        }
        return new BalanceSnapshot(balances, System.currentTimeMillis());
    }

    /**
     * Crea snapshot con balance DESBALANCEADO (un exchange con mucho, otros poco).
     * Simula condiciones post-arbitraje exitoso.
     */
    public static BalanceSnapshot buildImbalancedSnapshot(
            List<String> exchanges,
            double totalCapital) {

        Map<String, Map<String, Double>> balances = new HashMap<>();

        // 80% en el primer exchange, 20% distribuido en el resto
        String richExchange = exchanges.get(0);
        balances.put(richExchange, Map.of("USDT", totalCapital * 0.8));

        double remaining = totalCapital * 0.2;
        double perPoor = remaining / (exchanges.size() - 1);

        for (int i = 1; i < exchanges.size(); i++) {
            balances.put(exchanges.get(i), Map.of("USDT", perPoor));
        }

        return new BalanceSnapshot(balances, System.currentTimeMillis());
    }

    // =========================================================================
    // 📈 MARKET DATA BUILDERS
    // =========================================================================

    /**
     * Crea market data con SPREAD RENTABLE entre exchanges.
     *
     * @param pair Par de trading (ej: "SOLUSDT")
     * @param buyExchange Exchange donde comprar (precio más bajo)
     * @param sellExchange Exchange donde vender (precio más alto)
     * @param basePrice Precio base del activo
     * @param spreadPercent Diferencia en % entre exchanges
     * @return Mapa de precios por exchange
     */
    public static Map<String, Map<String, Double>> buildProfitableSpread(
            String pair,
            String buyExchange,
            String sellExchange,
            double basePrice,
            double spreadPercent) {

        double buyPrice = basePrice;
        double sellPrice = basePrice * (1 + spreadPercent / 100.0);

        return Map.of(
                buyExchange, Map.of(pair, buyPrice),
                sellExchange, Map.of(pair, sellPrice)
        );
    }

    /**
     * Crea market data SIN spread significativo (no rentable).
     */
    public static Map<String, Map<String, Double>> buildNoSpreadMarket(
            String pair,
            List<String> exchanges,
            double basePrice) {

        Map<String, Map<String, Double>> marketData = new HashMap<>();

        // Todos los exchanges al mismo precio (±0.01% de ruido)
        for (String exchange : exchanges) {
            double noise = ThreadLocalRandom.current().nextDouble(-0.0001, 0.0001);
            double price = basePrice * (1 + noise);
            marketData.put(exchange, Map.of(pair, price));
        }

        return marketData;
    }

    /**
     * Crea market data con VOLATILIDAD EXTREMA (múltiples oportunidades).
     */
    public static Map<String, Map<String, Double>> buildVolatileMarket(
            String pair,
            List<String> exchanges,
            double basePrice) {

        Map<String, Map<String, Double>> marketData = new HashMap<>();

        // Cada exchange con precio diferente (±5%)
        for (String exchange : exchanges) {
            double variance = ThreadLocalRandom.current().nextDouble(-0.05, 0.05);
            double price = basePrice * (1 + variance);
            marketData.put(exchange, Map.of(pair, price));
        }

        return marketData;
    }

    // =========================================================================
    // 📝 ORDER RESULT BUILDERS
    // =========================================================================

    /**
     * Crea OrderResult de ejecución EXITOSA (FILLED).
     */
    public static OrderResult buildSuccessfulOrder(
            String orderId,
            double qty,
            double price) {

        double executedValue = qty * price;
        double fee = executedValue * 0.001; // 0.1% fee

        return new OrderResult(
                orderId,
                "FILLED",
                qty,
                qty,           // executedQty = qty (100% filled)
                executedValue,
                price,
                fee,
                "TAKER"
        );
    }

    /**
     * Crea OrderResult de ejecución PARCIAL (PARTIALLY_FILLED).
     */
    public static OrderResult buildPartialOrder(
            String orderId,
            double requestedQty,
            double filledQty,
            double price) {

        double executedValue = filledQty * price;
        double fee = executedValue * 0.001;

        return new OrderResult(
                orderId,
                "PARTIALLY_FILLED",
                requestedQty,
                filledQty,
                executedValue,
                price,
                fee,
                "TAKER"
        );
    }

    /**
     * Crea OrderResult de ejecución FALLIDA (REJECTED).
     */
    public static OrderResult buildRejectedOrder(String orderId, String reason) {
        return new OrderResult(
                orderId,
                "REJECTED",
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                reason
        );
    }

    /**
     * Crea OrderResult de orden CANCELADA (CANCELLED).
     */
    public static OrderResult buildCancelledOrder(
            String orderId,
            double requestedQty,
            double filledQty,
            double price) {

        double executedValue = filledQty * price;
        double fee = filledQty > 0 ? executedValue * 0.001 : 0.0;

        return new OrderResult(
                orderId,
                "CANCELLED",
                requestedQty,
                filledQty,
                executedValue,
                price,
                fee,
                "TAKER"
        );
    }

    // =========================================================================
    // 🎲 CHAOS TESTING HELPERS
    // =========================================================================

    /**
     * Introduce latencia simulada (simula red lenta).
     * Útil para probar timeouts y race conditions.
     */
    public static void simulateNetworkLatency(int minMs, int maxMs) {
        try {
            int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Lanza excepción aleatoria (simula fallo de red).
     *
     * @param probability Probabilidad de fallo (0.0 - 1.0)
     */
    public static void maybeThrowNetworkError(double probability) {
        if (ThreadLocalRandom.current().nextDouble() < probability) {
            throw new RuntimeException("Simulated network timeout");
        }
    }

    // =========================================================================
    // 🔧 PRIVATE HELPERS
    // =========================================================================

    /**
     * Distribuye liquidez usando función exponencial decreciente.
     * Los primeros niveles tienen más liquidez (realista).
     */
    private static double[] distributeLiquidity(double totalLiquidity, int levels) {
        double[] distribution = new double[levels];
        double sum = 0.0;

        // Generar pesos exponenciales
        for (int i = 0; i < levels; i++) {
            distribution[i] = Math.exp(-i * 0.3);
            sum += distribution[i];
        }

        // Normalizar para que sumen totalLiquidity
        for (int i = 0; i < levels; i++) {
            distribution[i] = (distribution[i] / sum) * totalLiquidity;
        }

        return distribution;
    }

    // =========================================================================
    // 📋 CONSTANTES PRECONFIGURADAS PARA TESTS RÁPIDOS
    // =========================================================================

    public static class Presets {
        // Exchanges comunes
        public static final String BINANCE = "binance";
        public static final String BYBIT = "bybit_sub1";
        public static final String MEXC = "mexc";
        public static final List<String> ALL_EXCHANGES = List.of(BINANCE, BYBIT, MEXC);

        // Pares de trading populares
        public static final String SOL_USDT = "SOLUSDT";
        public static final String BTC_USDT = "BTCUSDT";
        public static final String ETH_USDT = "ETHUSDT";

        // Precios típicos (aproximados, útiles para testing)
        public static final double SOL_PRICE = 200.0;
        public static final double BTC_PRICE = 100_000.0;
        public static final double ETH_PRICE = 3_500.0;

        // Capitales de prueba
        public static final double SMALL_CAPITAL = 50.0;
        public static final double MEDIUM_CAPITAL = 500.0;
        public static final double LARGE_CAPITAL = 5_000.0;

        // Fees estándar
        public static final double TAKER_FEE = 0.001;  // 0.1%
        public static final double MAKER_FEE = 0.0008; // 0.08%
    }
}