package com.rafaeldiaz.orquestador_gold_rush_2025.core;

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

    // --- CACHÉ DE CORTO PLAZO (TTL) ---
    // Guarda el dato fresco por un tiempo limitado para no saturar la API
    private final Map<String, double[]> tradingFeeCache = new ConcurrentHashMap<>();
    private final Map<String, Double> withdrawalFeeCache = new ConcurrentHashMap<>();

    private final Map<String, Long> lastUpdateTrading = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateWithdraw = new ConcurrentHashMap<>();

    private static final long TTL_TRADING = 60 * 60 * 1000; // 1 Hora
    private static final long TTL_WITHDRAW = 15 * 60 * 1000; // 15 Minutos

    // --- MEMORIA DE LARGO PLAZO (KNOWLEDGE BASE) ---
    // Inicia con valores "Monstruosos" por seguridad, pero se actualiza con la REALIDAD.
    // Si la API falla, usamos el último valor real conocido, no el monstruo inicial.
    private final Map<String, Double> knownNetworkFees = new ConcurrentHashMap<>();

    public FeeManager(ExchangeConnector connector) {
        this.connector = connector;
        initializeSafetyDefaults();
        BotLogger.info("💲 FeeManager: Memoria Adaptativa INICIADA.");
    }

    private void initializeSafetyDefaults() {
        // Valores iniciales "Paracaídas" (Solo se usan si NUNCA hemos podido leer la API)
        knownNetworkFees.put("BTC", 0.0006);
        knownNetworkFees.put("ETH", 0.005);
        knownNetworkFees.put("SOL", 0.02);      // Empieza asumiendo ~$4
        knownNetworkFees.put("AVAX", 0.1);
        knownNetworkFees.put("XRP", 1.0);
        knownNetworkFees.put("PEPE", 2000000.0); // Empieza asumiendo ~$20
        knownNetworkFees.put("USDT", 2.0);
        // Si entra una moneda nueva que no conocemos, asumiremos un default en tiempo de ejecución.
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
    public double calculateTradingCost(String exchange, String pair, double amountUSDT) {
        double[] rates = getTradingFeeRate(exchange, pair);
        return amountUSDT * rates[0]; // Usamos Taker Fee por seguridad
    }

    // =========================================================================
    // 🔍 MOTORES DE BÚSQUEDA INTELIGENTE
    // =========================================================================

    private double[] getTradingFeeRate(String exchange, String pair) {
        String key = exchange + "_" + pair;
        long now = System.currentTimeMillis();

        if (tradingFeeCache.containsKey(key) && (now - lastUpdateTrading.getOrDefault(key, 0L) < TTL_TRADING)) {
            return tradingFeeCache.get(key);
        }

        double[] freshRates = connector.fetchDynamicTradingFee(exchange, pair);

        // Validación anti-scam (Tax Tokens)
        if (freshRates[0] > 0.2 || freshRates[0] < 0) freshRates[0] = 0.001;
        if (freshRates[1] > 0.2 || freshRates[1] < 0) freshRates[1] = 0.001;

        tradingFeeCache.put(key, freshRates);
        lastUpdateTrading.put(key, now);
        return freshRates;
    }

    /**
     * Obtiene el fee de retiro con INTELIGENCIA ADAPTATIVA.
     * 1. Busca en Caché reciente (TTL).
     * 2. Intenta API en vivo.
     * -> Si ÉXITO: Actualiza Caché y MEJORA la Memoria de Largo Plazo (Olvida al monstruo).
     * -> Si FALLO: Usa la Memoria de Largo Plazo (que puede ser el monstruo inicial o un dato real previo).
     */
    public double getWithdrawalFee(String exchange, String coin) {
        String key = exchange + "_" + coin;
        long now = System.currentTimeMillis();

        // 1. Revisar Caché Fresco (Velocidad HFT)
        if (withdrawalFeeCache.containsKey(key) && (now - lastUpdateWithdraw.getOrDefault(key, 0L) < TTL_WITHDRAW)) {
            return withdrawalFeeCache.get(key);
        }

        // 2. Intentar Fetch Real (La Verdad del Mercado)
        double freshFee = connector.fetchLiveWithdrawalFee(exchange, coin);

        if (freshFee >= 0) {
            // ✅ ÉXITO: Aprendimos el costo real.
            // Actualizamos la memoria inmediata
            withdrawalFeeCache.put(key, freshFee);
            lastUpdateWithdraw.put(key, now);

            // 🔥 APRENDIZAJE: Actualizamos la base de conocimiento para el futuro.
            // Si antes pensábamos que PEPE costaba 2M, y la API dice 300k, ahora recordaremos 300k.
            knownNetworkFees.put(coin, freshFee);

            return freshFee;
        }

        // 3. FALLBACK INTELIGENTE (Si la API falla)
        // No devolvemos error (-1), devolvemos "Lo mejor que sabemos hasta ahora".
        // Si ya habíamos operado antes, este valor será realista. Si es el primer intento, será el default seguro.
        double bestKnownFee = knownNetworkFees.getOrDefault(coin, 0.1); // 0.1 Genérico si es moneda rara nueva

        // Solo logueamos si estamos recurriendo a memoria para no ensuciar logs
        // BotLogger.warn("⚠️ API Fee Off (" + exchange + "). Usando Memoria para " + coin + ": " + bestKnownFee);

        return bestKnownFee;
    }
}