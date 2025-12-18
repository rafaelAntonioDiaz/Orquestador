package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.MarketStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cerebro analítico que detecta oportunidades de arbitraje triangular.
 * Task 2.2.1 del Backlog.
 */
public class ArbitrageDetector implements MarketStreamer.PriceListener {

    private static final Logger logger = LoggerFactory.getLogger(ArbitrageDetector.class);

    // Caché de precios en memoria (Thread-Safe para acceso concurrente)
    private final Map<String, Double> priceCache = new ConcurrentHashMap<>();

    // Comisiones estimadas (0.1% taker x 3 patas = 0.3%)
    // En producción esto vendrá del FeeRateManager dinámico
    private static final double ESTIMATED_TOTAL_FEE = 0.003;

    // Mínimo beneficio neto esperado para gritar "EUREKA" (0.2%)
    private static final double MIN_PROFIT_THRESHOLD = 0.002;

    @Override
    public void onPriceUpdate(String exchange, String pair, double price, long timestamp) {
        // 1. Actualizamos la caché instantáneamente
        priceCache.put(pair, price);

        // 2. Evaluamos oportunidades (Solo si tenemos los datos necesarios)
        // Ejemplo de Ciclo: USDT -> BTC -> SOL -> USDT
        evaluateTriangularCycle("BTC", "SOL");
    }

    /**
     * Evalúa el ciclo triangular: USDT -> A -> B -> USDT
     * @param coinA La moneda intermedia 1 (ej. BTC)
     * @param coinB La moneda intermedia 2 (ej. SOL)
     */
    private void evaluateTriangularCycle(String coinA, String coinB) {
        // Nombres de los pares (Bybit standard)
        String pairA_USDT = coinA + "USDT"; // BTCUSDT
        String pairB_USDT = coinB + "USDT"; // SOLUSDT
        String pairB_CoinA = coinB + coinA; // SOLBTC

        // Verificamos si tenemos precios para las 3 patas
        Double priceA_USDT = priceCache.get(pairA_USDT);
        Double priceB_USDT = priceCache.get(pairB_USDT);
        Double priceB_CoinA = priceCache.get(pairB_CoinA);

        if (priceA_USDT == null || priceB_USDT == null || priceB_CoinA == null) {
            return; // Datos incompletos, esperamos
        }

        // --- CÁLCULO DE LA RUTA ---
        // Ruta: Compro A (divido), Compro B con A (divido o multiplico?), Vendo B (multiplico)
        // Simplificación matemática del "Implied Rate" vs "Real Rate":

        // 1. Precio Real de B en USDT (Directo)
        double realPrice = priceB_USDT;

        // 2. Precio Sintético de B en USDT (Pasando por A)
        // Valor de 1 B -> en A (priceB_CoinA) -> en USDT (priceB_CoinA * priceA_USDT)
        double syntheticPrice = priceB_CoinA * priceA_USDT;

        // 3. Calculamos la discrepancia (Spread)
        // Si Sintético > Real: Compro B barato (USDT->B) y vendo B caro (B->A->USDT) ... espera, al revés.

        // Vamos a simular $100 USDT en el ciclo forward:
        // Paso 1: USDT -> A (Compro A) => 100 / priceA_USDT
        // Paso 2: A -> B (Compro B con A) => (Amt A) / priceB_CoinA  <-- OJO: Depende si el par es B/A o A/B.
        // Asumiendo par estándar SOLBTC (Base SOL, Quote BTC):
        // Para tener SOL pagando BTC, divido? No, el precio es cuantos BTC vale 1 SOL.
        // Tengo BTC, quiero SOL. 1 SOL = 0.00x BTC.
        // Amount SOL = Amount BTC / Price_SOLBTC.

        // Paso 3: B -> USDT (Vendo B) => Amt B * priceB_USDT.

        double startCapital = 100.0;
        double amtA = startCapital / priceA_USDT;       // Buy BTC
        double amtB = amtA / priceB_CoinA;              // Buy SOL with BTC
        double endCapital = amtB * priceB_USDT;         // Sell SOL for USDT

        double grossProfitParams = (endCapital - startCapital) / startCapital;
        double netProfit = grossProfitParams - ESTIMATED_TOTAL_FEE;

        // --- LOG DE OPORTUNIDAD ---
        if (netProfit > MIN_PROFIT_THRESHOLD) {
            logger.warn("🚨 OPORTUNIDAD DETECTADA! [USDT->{}->{}->USDT] Neto: {}%",
                    coinA, coinB, String.format("%.4f", netProfit * 100));

            // Aquí en el futuro llamaremos a TradeExecutor.execute()
        }
        // Debug para ver que estamos vivos (opcional, quitar en prod)
        else if (Math.random() < 0.01) { // Loguear solo el 1% de las veces para no saturar
            logger.info("Monitoreando ciclo {}/{}... Spread: {}%", coinA, coinB, String.format("%.4f", grossProfitParams * 100));
        }
    }
}