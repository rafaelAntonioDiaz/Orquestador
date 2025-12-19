package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.connect.MarketStreamer;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🧠 CEREBRO TRIANGULAR (Sistema 1)
 * Detecta oportunidades dentro de un mismo exchange (Bybit).
 * Ahora calcula FEES DINÁMICOS para los 3 saltos del triángulo.
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
        this.executor = new TradeExecutor(connector);
        // Inicializamos el Manager de Fees compartiendo el conector
        this.feeManager = new FeeManager(connector);
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

        String pairA_B = coinA + coinB; // PEPEBTC (Raro) o BTCPEPE (No existe) -> Ojo: suele ser ALTS/BTC
        // Convención normal: Base=ALTS, Quote=BTC -> PEPEBTC.
        // Si quiero pasar de PEPE a BTC, VENDO PEPE (Bid).

        String pairB_USDT = coinB + "USDT"; // BTCUSDT

        Double priceA_B = priceCache.get(pairA_B);
        Double priceB_USDT = priceCache.get(pairB_USDT);

        // Si no están en caché, intentamos fetch rápido (solo para validar lógica, en prod esto debe ser stream)
        if (priceA_B == null || priceB_USDT == null) {
            // Fetch asíncrono o silent fail para no bloquear el hilo del websocket
            // Para este test "en caliente", dejaremos que el DynamicSelector pueble el caché poco a poco
            // o hacemos un fetch rápido si es un par caliente.
            return;
        }

        // --- CÁLCULO DE LA TRIANGULACIÓN ---

        double capitalInicial = 100.0; // Simulamos $100

        // PASO 1: Comprar A con USDT
        // Cantidad A = $100 / PrecioA
        double qtyA = capitalInicial / priceA_USDT;

        // PASO 2: Vender A por B (PEPE -> BTC)
        // Cantidad B = QtyA * Precio(PEPE/BTC) [Si vendemos]
        // Ojo: En par PEPEBTC, el precio es cuántos BTC dan por 1 PEPE.
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
        // FeeManager.calculateTradingCost devuelve el costo en USD.

        double cost1 = feeManager.calculateTradingCost("bybit_sub1", coinA + "USDT", capitalInicial);
        double cost2 = feeManager.calculateTradingCost("bybit_sub1", pairA_B, capitalInicial); // Aprox valor sigue siendo $100
        double cost3 = feeManager.calculateTradingCost("bybit_sub1", pairB_USDT, capitalInicial);

        double totalFees = cost1 + cost2 + cost3;
        double netProfitUSD = grossProfitUSD - totalFees;
        double netPercent = (netProfitUSD / capitalInicial) * 100.0;

        // Logueamos el "Pulso" si es interesante
        BotLogger.info(String.format("🔺 TRIÁNGULO [%s]: Bruto: %.3f%% | Fees: $%.2f | Neto: %.3f%%",
                coinA, grossPercent, totalFees, netPercent));

        // --- DISPARO ---
        if (netPercent > MIN_NET_PROFIT) {
            BotLogger.warn("🚀 OPORTUNIDAD TRIANGULAR REAL: " + coinA + " Neto: " + netPercent + "%");
            executor.executeTriangular(coinA, coinB, 20.0); // Ejecutar con $20 reales
        }
    }
}