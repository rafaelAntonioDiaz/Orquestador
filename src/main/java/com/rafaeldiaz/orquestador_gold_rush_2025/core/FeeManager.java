package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💲 GESTOR DE TARIFAS VIVO (CON PROTOCOLO PEP) 💲
 * Solución Creativa: Si el API falla, aplicamos una "Tarifa Pesimista Estándar".
 * Esto permite que el bot siga calculando sin arriesgar capital con tarifas irreales.
 */
public class FeeManager {

    private final ExchangeConnector connector;

    // --- CACHÉ ---
    private final Map<String, double[]> tradingFeeCache = new ConcurrentHashMap<>();
    private final Map<String, Double> withdrawalFeeCache = new ConcurrentHashMap<>();

    private final Map<String, Long> lastUpdateTrading = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateWithdraw = new ConcurrentHashMap<>();

    private static final long TTL_TRADING = 60 * 60 * 1000;
    private static final long TTL_WITHDRAW = 15 * 60 * 1000;

    // 🛡️ MAPA DE TARIFAS PESIMISTAS (INDUSTRY CEILING)
    // Valores ligeramente altos pero REALISTAS para desbloquear el cálculo.
    private static final Map<String, Double> PESSIMISTIC_FEES = new ConcurrentHashMap<>();
    static {
        // BTC: La media es 0.0002 - 0.0005. Ponemos 0.0006 (~$50 USD) para ser duros.
        PESSIMISTIC_FEES.put("BTC", 0.0006);
        // ETH: La media es 0.002 - 0.005. Ponemos 0.008 (~$25 USD)
        PESSIMISTIC_FEES.put("ETH", 0.008);
        // Altcoins rápidas (Fees reales suelen ser centavos, ponemos márgenes de seguridad)
        PESSIMISTIC_FEES.put("SOL", 0.02);
        PESSIMISTIC_FEES.put("AVAX", 0.1);
        PESSIMISTIC_FEES.put("XRP", 1.0);
        PESSIMISTIC_FEES.put("PEPE", 10000000.0); // PEPE suele tener fees de millones de unidades
        PESSIMISTIC_FEES.put("USDT", 5.0);    // Red TRC20 cuesta $1, ERC20 cuesta $5. Asumimos la cara.
    }

    public FeeManager(ExchangeConnector connector) {
        this.connector = connector;
        BotLogger.info("💲 FeeManager: Protocolo PEP (Pesimista) ACTIVADO.");
    }

    // =========================================================================
    // 🚚 CÁLCULO CROSS-EXCHANGE
    // =========================================================================
    public double calculateCrossCost(String sourceEx, String targetEx, String pair, double amountUSDT, double currentPrice) {
        double buyCost = calculateTradingCost(sourceEx, pair, amountUSDT);
        double sellCost = calculateTradingCost(targetEx, pair, amountUSDT);

        String asset = pair.replace("USDT", "").replace("-", "");

        // Obtenemos fee (Real o Pesimista)
        double withdrawQty = getWithdrawalFee(sourceEx, asset);

        // Si aún así falló catastróficamente
        if (withdrawQty < 0) return 99999999.9;

        double withdrawCostUSD = withdrawQty * currentPrice;
        return buyCost + sellCost + withdrawCostUSD;
    }

    // =========================================================================
    // ⚡ CÁLCULO TRADING
    // =========================================================================
    public double calculateTradingCost(String exchange, String pair, double amountUSDT) {
        double[] rates = getTradingFeeRate(exchange, pair);
        return amountUSDT * rates[0];
    }

    // =========================================================================
    // 🔍 MOTORES DE BÚSQUEDA
    // =========================================================================

    private double[] getTradingFeeRate(String exchange, String pair) {
        String key = exchange + "_" + pair;
        long now = System.currentTimeMillis();

        if (tradingFeeCache.containsKey(key) && (now - lastUpdateTrading.getOrDefault(key, 0L) < TTL_TRADING)) {
            return tradingFeeCache.get(key);
        }

        double[] freshRates = connector.fetchDynamicTradingFee(exchange, pair);
        if (freshRates[0] > 0.01) freshRates[0] = 0.001;
        if (freshRates[1] > 0.01) freshRates[1] = 0.001;

        tradingFeeCache.put(key, freshRates);
        lastUpdateTrading.put(key, now);
        return freshRates;
    }

    private double getWithdrawalFee(String exchange, String coin) {
        String key = exchange + "_" + coin;
        long now = System.currentTimeMillis();

        if (withdrawalFeeCache.containsKey(key) && (now - lastUpdateWithdraw.getOrDefault(key, 0L) < TTL_WITHDRAW)) {
            return withdrawalFeeCache.get(key);
        }

        double freshFee = connector.fetchLiveWithdrawalFee(exchange, coin);

        // 🚨 AQUÍ ESTÁ LA MAGIA CREATIVA 🚨
        if (isSuspiciousFee(coin, freshFee)) {
            // En lugar de abortar, sacamos el "Libro de Tarifas Pesimistas"
            double pessimisticFee = PESSIMISTIC_FEES.getOrDefault(coin, -1.0);

            if (pessimisticFee > 0) {
                // Solo advertimos la primera vez por par para no ensuciar
                if (!withdrawalFeeCache.containsKey(key)) {
                    BotLogger.warn("⚠️ API Error (" + freshFee + ") para " + coin + " en " + exchange +
                            ". Aplicando Tarifa Pesimista: " + pessimisticFee);
                }
                freshFee = pessimisticFee;
            } else {
                // Si no tenemos estimación para esa moneda, ahí si abortamos.
                return -1.0;
            }
        }

        withdrawalFeeCache.put(key, freshFee);
        lastUpdateWithdraw.put(key, now);

        return freshFee;
    }

    private boolean isSuspiciousFee(String coin, double fee) {
        if (fee < 0) return true;
        // Si el fee es > 0.1 BTC/ETH es error (nadie paga $8k de fee)
        if ((coin.equals("BTC") || coin.equals("ETH")) && fee > 0.1) return true;
        // El famoso error "1.0"
        if (fee == 1.0 && !coin.equals("USDT") && !coin.equals("USDC") && !coin.equals("DAI")) return true;
        return false;
    }
}