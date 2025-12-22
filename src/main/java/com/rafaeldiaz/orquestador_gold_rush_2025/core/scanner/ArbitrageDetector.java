package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.connect.MarketStreamer;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.TradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🧠 CEREBRO TRIANGULAR (Sistema 1 - Bybit Stream)
 * Detecta oportunidades dentro de un mismo exchange (Bybit) usando Websockets.
 * Sincronizado con la arquitectura v2.0 (TradeExecutor Híbrido).
 */
public class ArbitrageDetector implements MarketStreamer.PriceListener {

    private final ExchangeConnector connector;
    private final TradeExecutor executor;
    private final FeeManager feeManager; // <--- El contador de costos

    // Cache de precios para cálculos rápidos
    private final Map<String, Double> priceCache = new ConcurrentHashMap<>();

    // Umbral mínimo NETO (después de fees)
    private static final double MIN_NET_PROFIT = 0.5; // Queremos 0.5% limpio

    public ArbitrageDetector(ExchangeConnector connector) {
        this.connector = connector;

        // 1. Inicializamos FeeManager PRIMERO (Requisito del Executor)
        this.feeManager = new FeeManager(connector);

        // 2. Inicializamos TradeExecutor pasándole ambas dependencias
        this.executor = new TradeExecutor(connector, this.feeManager);
    }

    @Override
    public void onPriceUpdate(String exchange, String pair, double price, long timestamp) {
        // 1. Guardar precio (ej. "PEPEUSDT" -> 0.000015)
        priceCache.put(pair, price);

        // 2. Intentar triangular
        // Asumimos estructura: COIN + USDT (ej. PEPE + USDT)
        if (pair.endsWith("USDT")) {
            String coinA = pair.replace("USDT", ""); // PEPE
            checkTriangularArbitrage(coinA, price);
        }
    }

    /**
     * Evalúa la ruta: USDT -> CoinA -> BTC -> USDT
     */
    private void checkTriangularArbitrage(String coinA, double priceA_USDT) {
        String coinB = "BTC"; // Pivote estándar (Podría ser ETH o SOL en el futuro)

        // Necesitamos 3 precios:
        // 1. USDT -> A (priceA_USDT) [YA LO TENEMOS]
        // 2. A -> B    (priceA_B)    [CONSULTAR CACHÉ O API]
        // 3. B -> USDT (priceB_USDT) [CONSULTAR CACHÉ O API]

        String pairA_B = coinA + coinB; // PEPEBTC
        String pairB_USDT = coinB + "USDT"; // BTCUSDT

        Double priceA_B = priceCache.get(pairA_B);
        Double priceB_USDT = priceCache.get(pairB_USDT);

        // Si no están en caché, salimos (en prod esto se llena con el stream)
        if (priceA_B == null || priceB_USDT == null) {
            return;
        }

        // --- CÁLCULO DE LA TRIANGULACIÓN ---

        double capitalInicial = 100.0; // Simulamos $100

        // PASO 1: Comprar A con USDT
        double qtyA = capitalInicial / priceA_USDT;

        // PASO 2: Vender A por B (PEPE -> BTC)
        double qtyB = qtyA * priceA_B;

        // PASO 3: Vender B por USDT (BTC -> USDT)
        double finalUSDT = qtyB * priceB_USDT;

        // --- CÁLCULO DE GANANCIA BRUTA ---
        double grossProfitUSD = finalUSDT - capitalInicial;
        double grossPercent = (grossProfitUSD / capitalInicial) * 100.0;

        // Si no hay ganancia bruta, ni nos molestamos en calcular fees
        if (grossPercent <= 0.1) return;

        // --- CÁLCULO DE FEES REALES (LA MAGIA) ---
        // Necesitamos sumar los fees de los 3 trades.
        // Usamos "bybit_sub1" para el cálculo preciso de fees si está disponible
        double cost1 = feeManager.calculateTradingCost("bybit", coinA + "USDT", capitalInicial);
        double cost2 = feeManager.calculateTradingCost("bybit", pairA_B, capitalInicial);
        double cost3 = feeManager.calculateTradingCost("bybit", pairB_USDT, capitalInicial);

        double totalFees = cost1 + cost2 + cost3;
        double netProfitUSD = grossProfitUSD - totalFees;
        double netPercent = (netProfitUSD / capitalInicial) * 100.0;

        // Logueamos el "Pulso" si es interesante
        BotLogger.info(String.format("🔺 TRIÁNGULO [%s]: Bruto: %.3f%% | Fees: $%.2f | Neto: %.3f%%",
                coinA, grossPercent, totalFees, netPercent));

        // --- DISPARO ---
        if (netPercent > MIN_NET_PROFIT) {
            BotLogger.warn("🚀 OPORTUNIDAD TRIANGULAR REAL: " + coinA + " Neto: " + netPercent + "%");

            // CORRECCIÓN AQUÍ: Agregamos el exchange "bybit" al inicio
            // Firma: executeTriangular(String exchange, String asset, String bridge, double capital)
            executor.executeTriangular("bybit", coinA, coinB, 20.0);
        }
    }
}