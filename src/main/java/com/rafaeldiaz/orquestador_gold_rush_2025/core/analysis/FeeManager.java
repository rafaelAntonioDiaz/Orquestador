package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💲 FEE MANAGER (AUTORIDAD CENTRAL DE TARIFAS)
 * Única fuente de verdad para costos. El Estimador solo pregunta, no decide.
 */
public class FeeManager {

    private final ExchangeConnector connector;
    private final Map<String, CachedValue> feeCache = new ConcurrentHashMap<>();

    private record CachedValue(double value, long expiry) {}

    public FeeManager(ExchangeConnector connector) {
        this.connector = connector;
        BotLogger.info("💲 FeeManager ACTIVO | Zero-Fee Exchange: " + BotConfig.ZERO_FEE_EXCHANGE);
    }

    public double calculateCrossCost(String sourceEx, String targetEx, String pair,
                                     double amountUSDT, double currentPrice) {
        // En modelo "Human-in-the-Loop", ignoramos withdraws por ahora.
        double buyCost = calculateTradingCost(sourceEx, pair, amountUSDT);
        double sellCost = calculateTradingCost(targetEx, pair, amountUSDT);
        return buyCost + sellCost;
    }

    public double calculateTradingCost(String exchange, String pair, double amountUSDT) {
        double rate = getTradingFee(exchange, pair, "TAKER");
        return amountUSDT * rate;
    }

    /**
     * Obtiene el % de comisión REAL (Aplicando promociones y Overrides).
     */
    public double getTradingFee(String exchange, String pair, String type) {
        String key = (exchange + "_" + pair + "_" + type).toUpperCase();

        // 1. LÓGICA DE PROMOCIÓN (ZERO FEE ZONE)
        // Verificamos si el exchange es el bonificado (MEXC)
        if (isZeroFeeExchange(exchange)) {
            // Aplica a TODOS los activos o solo a la lista
            if (isZeroFeeAsset(pair)) {
                return 0.0000; // ¡GRATIS!
            }
        }

        // 2. FALLBACKS COMUNES (Si no es promo)
        if (exchange.equalsIgnoreCase("binance")) return 0.00075; // BNB tier (aprox)
        if (exchange.toLowerCase().contains("bybit")) return 0.0010;

        // 3. CACHÉ
        CachedValue cached = feeCache.get(key);
        if (cached != null && System.currentTimeMillis() < cached.expiry) {
            return cached.value;
        }

        return 0.001; // Default 0.1%
    }
    public double getWithdrawalFee(String exchange, String asset) {
        // TODO: Idealmente esto debería consultar al endpoint de "asset details" del ExchangeConnector.
        // Por ahora, devolvemos estimaciones estáticas conservadoras para la simulación.

        String cleanAsset = asset.toUpperCase();

        // Costos aproximados de red (Network Fee)
        if (cleanAsset.equals("USDT")) return 1.0;   // Estándar TRC20/BSC
        if (cleanAsset.equals("BTC")) return 0.0005; // ~ $30-$50 dependiendo de la mempool
        if (cleanAsset.equals("ETH")) return 0.004;  // ~ $10-$15 gas
        if (cleanAsset.equals("SOL")) return 0.01;   // Muy barato
        if (cleanAsset.equals("XRP")) return 0.25;

        // Fallback genérico para altcoins (simulación)
        return 0.1;
    }
    // --- LÓGICA PRIVADA DE NEGOCIO ---

    private boolean isZeroFeeExchange(String exchange) {
        // Chequeo rápido por Override Global o por Nombre Configurado
        return BotConfig.MEXC_ZERO_FEE_OVERRIDE && exchange.toLowerCase().contains("mexc")
                || exchange.equalsIgnoreCase(BotConfig.ZERO_FEE_EXCHANGE);
    }

    private boolean isZeroFeeAsset(String pair) {
        // Si la lista está vacía, asumimos que TODO el exchange es Zero Fee (Promo Global)
        if (BotConfig.ZERO_FEE_ASSETS.isEmpty()) return true;

        // Limpiamos el par para obtener el asset (ej. "WIFUSDT" -> "WIF")
        String asset = pair.toUpperCase().replace("USDT", "").replace("-", "");

        // Chequeamos si el activo está en la lista VIP
        return BotConfig.ZERO_FEE_ASSETS.contains(asset);
    }

    public void forceFeeCacheInvalidation(String exchange) { feeCache.clear(); }
}