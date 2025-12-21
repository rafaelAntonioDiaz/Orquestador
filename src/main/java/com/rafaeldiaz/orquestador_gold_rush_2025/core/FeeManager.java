package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💲 GESTOR DE TARIFAS VIVO (CON PROTOCOLO PEP) 💲
 * Autoridad central de costos. Maneja Caché, APIs y estimaciones pesimistas.
 */
public class FeeManager {

    private final ExchangeConnector connector;

    // --- CACHÉ ---
    private final Map<String, double[]> tradingFeeCache = new ConcurrentHashMap<>();
    private final Map<String, Double> withdrawalFeeCache = new ConcurrentHashMap<>();

    private final Map<String, Long> lastUpdateTrading = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateWithdraw = new ConcurrentHashMap<>();

    private static final long TTL_TRADING = 60 * 60 * 1000; // 1 Hora
    private static final long TTL_WITHDRAW = 15 * 60 * 1000; // 15 Minutos

    // 🛡️ MAPA DE TARIFAS PESIMISTAS (INDUSTRY CEILING)
    // Se usan SOLO si la API falla o no tenemos credenciales para leer el fee real.
    private static final Map<String, Double> PESSIMISTIC_FEES = new ConcurrentHashMap<>();
    static {
        PESSIMISTIC_FEES.put("BTC", 0.0006);    // ~$50 USD
        PESSIMISTIC_FEES.put("ETH", 0.005);     // ~$15 USD
        PESSIMISTIC_FEES.put("SOL", 0.02);      // ~$3 USD (Alto, pero seguro)
        PESSIMISTIC_FEES.put("AVAX", 0.1);
        PESSIMISTIC_FEES.put("XRP", 1.0);
        PESSIMISTIC_FEES.put("PEPE", 2000000.0); // Ajustado a realidad
        PESSIMISTIC_FEES.put("USDT", 2.0);      // Promedio TRC20/BSC
    }

    public FeeManager(ExchangeConnector connector) {
        this.connector = connector;
        BotLogger.info("💲 FeeManager: Protocolo PEP (Pesimista) ACTIVADO.");
    }

    // =========================================================================
    // 🚚 CÁLCULO CROSS-EXCHANGE (Total estimado)
    // =========================================================================
    public double calculateCrossCost(String sourceEx, String targetEx, String pair, double amountUSDT, double currentPrice) {
        double buyCost = calculateTradingCost(sourceEx, pair, amountUSDT);
        double sellCost = calculateTradingCost(targetEx, pair, amountUSDT);

        String asset = pair.replace("USDT", "").replace("-", "");

        // Obtenemos fee de retiro (Cantidad de monedas)
        double withdrawQty = getWithdrawalFee(sourceEx, asset);

        // Si falló totalmente (ni API ni Pesimista), devolvemos costo infinito para evitar trade
        if (withdrawQty < 0) return 99999999.9;

        double withdrawCostUSD = withdrawQty * currentPrice;
        return buyCost + sellCost + withdrawCostUSD;
    }

    // =========================================================================
    // ⚡ CÁLCULO TRADING (Solo compra/venta)
    // =========================================================================
    public double calculateTradingCost(String exchange, String pair, double amountUSDT) {
        double[] rates = getTradingFeeRate(exchange, pair);
        // Usamos el Taker Fee (rates[0]) por defecto para ser conservadores
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

        // 🚨 CORRECCIÓN: Umbral subido al 20% (0.2) para tolerar Memecoins con Tax
        if (freshRates[0] > 0.2 || freshRates[0] < 0) freshRates[0] = 0.001; // Default 0.1%
        if (freshRates[1] > 0.2 || freshRates[1] < 0) freshRates[1] = 0.001;

        tradingFeeCache.put(key, freshRates);
        lastUpdateTrading.put(key, now);
        return freshRates;
    }

    /**
     * Obtiene el fee de retiro. AHORA ES PÚBLICO.
     * @param exchange Nombre del exchange (binance, bybit)
     * @param coin Símbolo de la moneda (BTC, SOL, USDT)
     * @return Cantidad de moneda que cobra la red (o negativo si falla)
     */
    public double getWithdrawalFee(String exchange, String coin) {
        String key = exchange + "_" + coin;
        long now = System.currentTimeMillis();

        // 1. Revisar Caché
        if (withdrawalFeeCache.containsKey(key) && (now - lastUpdateWithdraw.getOrDefault(key, 0L) < TTL_WITHDRAW)) {
            return withdrawalFeeCache.get(key);
        }

        // 2. Intentar Fetch Real (Requiere API Keys válidas en Connector)
        double freshFee = connector.fetchLiveWithdrawalFee(exchange, coin);

        // 3. Fallback: Si la API falla (-1.0), usamos la Tabla Pesimista
        if (freshFee < 0) {
            double pessimisticFee = PESSIMISTIC_FEES.getOrDefault(coin, -1.0);

            if (pessimisticFee > 0) {
                // Logueamos advertencia solo la primera vez para no ensuciar la consola
                if (!withdrawalFeeCache.containsKey(key)) {
                    BotLogger.warn("⚠️ API Fee Error (" + exchange + "/" + coin + "). Usando Pesimista: " + pessimisticFee);
                }
                freshFee = pessimisticFee;
            } else {
                // Si no está en la tabla pesimista, usamos un default genérico de emergencia
                // (Mejor perder un trade por fee alto estimado que perder dinero real)
                freshFee = 0.05; // Default agresivo si no sabemos qué moneda es
            }
        }

        withdrawalFeeCache.put(key, freshFee);
        lastUpdateWithdraw.put(key, now);

        return freshFee;
    }
}