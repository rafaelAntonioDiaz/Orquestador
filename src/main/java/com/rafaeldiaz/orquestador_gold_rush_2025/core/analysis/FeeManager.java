package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💲 GESTOR DE TARIFAS ADAPTATIVO (SMART FEE MANAGER) 💲
 * Autoridad central de costos.
 * Evolución: Inicia con valores pesimistas, pero APRENDE los costos reales
 * y los recuerda para no bloquear operaciones válidas por falta de datos.
 */
public class FeeManager {

    private final ExchangeConnector connector;

    // --- CACHÉ INTELIGENTE (Versión 2.0) ---
    // Key: "EXCHANGE_PAIR_TYPE" (ej: "BYBIT_BTCUSDT_TAKER" o "MEXC_WITHDRAW_SOL")
    private final Map<String, CachedValue> feeCache = new ConcurrentHashMap<>();

    // Duración: 10 Minutos (Balance entre frescura y rate limits)
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;

    // --- FALLBACKS DE SEGURIDAD (Cinturón de seguridad) ---
    private static final double DEFAULT_TAKER_FEE = 0.001; // 0.1%
    private static final double DEFAULT_MAKER_FEE = 0.001; // 0.1%
    private static final Map<String, Double> WITHDRAW_FALLBACKS = Map.of(
            "USDT", 2.0, "SOL", 0.02, "XRP", 0.5, "BTC", 0.0005,
            "ETH", 0.005, "DOGE", 5.0, "DEFAULT", 1.0
    );

    // Record interno para el caché
    private record CachedValue(double value, long expiry) {}

    public FeeManager(ExchangeConnector connector) {
        this.connector = connector;
        BotLogger.info("💲 FeeManager 2.0: Caché Inteligente (10min) INICIADO.");
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

        // Costo en USD del retiro
        double withdrawCostUSD = withdrawQty * currentPrice;

        return buyCost + sellCost + withdrawCostUSD;
    }

    // =========================================================================
    // ⚡ CÁLCULO TRADING (Solo compra/venta)
    // =========================================================================
    // =========================================================================
    // ⚡ 1. CÁLCULO TRADING (Actualizado 2.0)
    // =========================================================================

    public double calculateTradingCost(String exchange, String pair, double amountUSDT) {
        // Asumimos TAKER para arbitraje de alta velocidad (Market Orders)
        double rate = getTradingFee(exchange, pair, "TAKER");
        return amountUSDT * rate;
    }

    /**
     * Obtiene el % de comisión real.
     * @param type "MAKER" o "TAKER"
     */
    public double getTradingFee(String exchange, String pair, String type) {
        String key = (exchange + "_" + pair + "_" + type).toUpperCase();

        // 1. Consultar Caché
        CachedValue cached = feeCache.get(key);
        if (cached != null && System.currentTimeMillis() < cached.expiry) {
            return cached.value;
        }

        // 2. Fetch Real (Si caché expiró)
        try {
            double[] fees = connector.fetchDynamicTradingFee(exchange, pair);
            double taker = (fees[0] < 0 || fees[0] > 0.1) ? DEFAULT_TAKER_FEE : fees[0];
            double maker = (fees[1] < 0 || fees[1] > 0.1) ? DEFAULT_MAKER_FEE : fees[1];

            // Guardamos ambos en caché
            long expiry = System.currentTimeMillis() + CACHE_TTL_MS;
            feeCache.put((exchange + "_" + pair + "_TAKER").toUpperCase(), new CachedValue(taker, expiry));
            feeCache.put((exchange + "_" + pair + "_MAKER").toUpperCase(), new CachedValue(maker, expiry));

            return type.equalsIgnoreCase("TAKER") ? taker : maker;

        } catch (Exception e) {
            // Si hay dato viejo, úsalo aunque haya expirado (mejor que fallback ciego)
            if (cached != null) return cached.value;
            return type.equalsIgnoreCase("MAKER") ? DEFAULT_MAKER_FEE : DEFAULT_TAKER_FEE;
        }
    }

    // =========================================================================
    // 🚚 2. FEE DE RETIRO (Actualizado 2.0 - Compatible con nuevo Caché)
    // =========================================================================

    /**
     * Obtiene el costo fijo de retirar una moneda (en unidades de la moneda).
     * @param exchange Exchange origen
     * @param asset Moneda a retirar (ej: SOL, USDT)
     * @return Cantidad a descontar (ej: 0.01)
     */
    public double getWithdrawalFee(String exchange, String asset) {
        // Usamos una llave única para el caché unificado
        String key = (exchange + "_WITHDRAW_" + asset).toUpperCase();

        // 1. Consultar Caché (El nuevo mapa 'feeCache')
        CachedValue cached = feeCache.get(key);
        if (cached != null && System.currentTimeMillis() < cached.expiry) {
            return cached.value;
        }

        // 2. Intentar Fetch Real (La Verdad del Mercado)
        try {
            double fee = connector.fetchLiveWithdrawalFee(exchange, asset);

            // Si la API responde con un valor válido (> 0)
            if (fee > 0) {
                // Guardar en el nuevo caché unificado con TTL de 10 min
                feeCache.put(key, new CachedValue(fee, System.currentTimeMillis() + CACHE_TTL_MS));
                return fee;
            }
        } catch (Exception e) {
            BotLogger.warn("⚠️ Withdraw Fetch Fail (" + exchange + "-" + asset + "): " + e.getMessage());
        }

        // 3. Fallback de Seguridad (Si todo falla)
        // Si teníamos un dato viejo en caché, úsalo (mejor viejo que inventado)
        if (cached != null) return cached.value;

        // Si no hay nada, usa la tabla estática de seguridad
        return WITHDRAW_FALLBACKS.getOrDefault(asset.toUpperCase(), WITHDRAW_FALLBACKS.get("DEFAULT"));
    }
    // =========================================================================
    // 🔄 INVALIDACIÓN REACTIVA
    // =========================================================================

    /**
     * Fuerza el borrado de la caché de fees para un exchange específico.
     * Se debe llamar:
     * 1. Al detectar un cambio de día UTC (00:00).
     * 2. Después de una operación exitosa (para asegurar datos frescos).
     * 3. Si el sistema detecta inconsistencias en los cálculos.
     */
    public void forceFeeCacheInvalidation(String exchange) {
        String exKey = exchange.toUpperCase();

        // Removemos todas las entradas que empiecen con el nombre del exchange
        feeCache.keySet().removeIf(key -> key.startsWith(exKey + "_"));

        BotLogger.info("♻️ Fee Cache invalidado para: " + exchange + " (Datos frescos requeridos)");
    }
}