package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💲 GESTOR DE TARIFAS VIVO (LIVING FEE MANAGER) 💲
 * Consulta a los exchanges en tiempo real para determinar el Costo Exacto de Ejecución.
 * Gestiona tanto Comisiones de Trading (VIP level) como Costos de Gas (Blockchain).
 */
public class FeeManager {

    private final ExchangeConnector connector;

    // --- CACHÉ INTELIGENTE (Para no saturar la API) ---
    // Key: "exchange_symbol" -> Value: [TakerFee, MakerFee]
    private final Map<String, double[]> tradingFeeCache = new ConcurrentHashMap<>();
    // Key: "exchange_coin" -> Value: WithdrawalFee (en moneda base)
    private final Map<String, Double> withdrawalFeeCache = new ConcurrentHashMap<>();

    // Timestamps para invalidar caché
    private final Map<String, Long> lastUpdateTrading = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateWithdraw = new ConcurrentHashMap<>();

    // Tiempos de vida de la caché (TTL)
    private static final long TTL_TRADING = 60 * 60 * 1000; // 1 Hora (Tu nivel VIP no cambia rápido)
    private static final long TTL_WITHDRAW = 15 * 60 * 1000; // 15 Min (El gas de la red cambia rápido)

    public FeeManager(ExchangeConnector connector) {
        this.connector = connector;
        BotLogger.info("💲 FeeManager conectado y escuchando tarifas del mercado.");
    }

    // =========================================================================
    // ⚡ 1. CÁLCULO PARA ARBITRAJE TRIANGULAR (Solo Trading Fees)
    // =========================================================================

    /**
     * Calcula cuánto cuesta ejecutar una orden TAKER (Market) en dólares.
     * Vital para calcular el costo de los 3 saltos del triángulo.
     */
    public double calculateTradingCost(String exchange, String pair, double amountUSDT) {
        // Obtenemos la tasa real (ej. 0.001 para 0.1% o 0.0002 para VIP)
        double[] rates = getTradingFeeRate(exchange, pair);
        double takerFeeRate = rates[0]; // Usamos Taker porque arbitraje requiere velocidad

        return amountUSDT * takerFeeRate;
    }

    // =========================================================================
    // 🚚 2. CÁLCULO PARA ARBITRAJE CROSS-EXCHANGE (Trading + Retiro)
    // =========================================================================

    /**
     * Calcula el Costo Total de Mover Dinero de A -> B.
     * OPTIMIZADO: Recibe el precio actual para no hacer peticiones HTTP extra.
     */
    public double calculateCrossCost(String sourceEx, String targetEx, String pair, double amountUSDT, double currentPrice) {
        // 1. Costos de Trading (Compra y Venta)
        double buyCost = calculateTradingCost(sourceEx, pair, amountUSDT);
        double sellCost = calculateTradingCost(targetEx, pair, amountUSDT);

        // 2. Costo de Retiro (Gas Fee)
        String asset = pair.replace("USDT", "").replace("-", "");

        // Obtenemos fee de retiro en cantidad de moneda (Cached)
        double withdrawQty = getWithdrawalFee(sourceEx, asset);

        // Convertimos fee a USD usando el precio que YA TENEMOS (Sin latencia de red)
        double withdrawCostUSD = withdrawQty * currentPrice;

        return buyCost + sellCost + withdrawCostUSD;
    }
    // =========================================================================
    // 🔍 3. MOTORES DE BÚSQUEDA (API + CACHÉ)
    // =========================================================================

    private double[] getTradingFeeRate(String exchange, String pair) {
        String key = exchange + "_" + pair;
        long now = System.currentTimeMillis();

        // 1. Mirar en Caché
        if (tradingFeeCache.containsKey(key) && (now - lastUpdateTrading.getOrDefault(key, 0L) < TTL_TRADING)) {
            return tradingFeeCache.get(key);
        }

        // 2. Consultar API (Vivo)
        double[] freshRates = connector.fetchDynamicTradingFee(exchange, pair);

        // 3. Guardar
        tradingFeeCache.put(key, freshRates);
        lastUpdateTrading.put(key, now);

        return freshRates;
    }

    private double getWithdrawalFee(String exchange, String coin) {
        String key = exchange + "_" + coin;
        long now = System.currentTimeMillis();

        // 1. Mirar en Caché
        if (withdrawalFeeCache.containsKey(key) && (now - lastUpdateWithdraw.getOrDefault(key, 0L) < TTL_WITHDRAW)) {
            return withdrawalFeeCache.get(key);
        }

        // 2. Consultar API (Vivo)
        // BotLogger.info("⏳ Consultando Gas Fee real para " + coin + " en " + exchange + "...");
        double freshFee = connector.fetchLiveWithdrawalFee(exchange, coin);

        // 3. Guardar
        withdrawalFeeCache.put(key, freshFee);
        lastUpdateWithdraw.put(key, now);

        return freshFee;
    }
}