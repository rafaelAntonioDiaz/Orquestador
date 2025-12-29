package com.rafaeldiaz.orquestador_gold_rush_2025.core.main;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner.DeepMarketScanner;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🧪 THE MATRIX ULTIMATE: Suite de Pruebas con Telemetría de Tiempos.
 * Mide la velocidad de reacción del Agente ante 5 escenarios críticos.
 */
public class WorkflowStressTest {

    public static void main(String[] args) throws InterruptedException {
        BotLogger.info("🧪 INICIANDO PROTOCOLO DE PRUEBA: MATRIX ULTIMATE (TIMED)");
        BotLogger.info("=========================================================");

        GenericMockConnector mock = new GenericMockConnector();
        ExecutionCoordinator coordinator = new ExecutionCoordinator();
        DeepMarketScanner scanner = new DeepMarketScanner(mock, coordinator);
        scanner.setDryRun(true);

        long suiteStart = System.currentTimeMillis();

        // =====================================================================
        // ✅ ESCENARIO 1: SPATIAL PROFIT
        // =====================================================================
        BotLogger.info("\n🔵 [TEST 1] SPATIAL: BTC (Binance $50k -> Bybit $55k)");
        mock.reset();
        mock.setPrice("binance", "BTCUSDT", 50000.0);
        mock.setPrice("bybit_sub1", "BTCUSDT", 55000.0);
        mock.setLiquidity("BTCUSDT", 100.0);

        scanner.updateTargets(List.of("BTC"));
        measureScenario("SPATIAL_PROFIT", scanner);

        // =====================================================================
        // ✅ ESCENARIO 2: TRIANGULAR PROFIT
        // =====================================================================
        BotLogger.info("\n🟣 [TEST 2] TRIANGULAR: SOL-ETH (Binance)");
        mock.reset();
        mock.setPrice("binance", "SOLUSDT", 100.0);
        mock.setPrice("binance", "SOLETH", 0.04);
        mock.setPrice("binance", "ETHUSDT", 3000.0);
        mock.setLiquidity("SOLUSDT", 1000.0);
        mock.setLiquidity("SOLETH", 1000.0);
        mock.setLiquidity("ETHUSDT", 1000.0);

        scanner.updateTargets(List.of("SOL"));
        measureScenario("TRIANGULAR_PROFIT", scanner);

        // =====================================================================
        // ❌ ESCENARIO 3: FEE TRAP
        // =====================================================================
        BotLogger.info("\n🟠 [TEST 3] FEE TRAP: BNB (Gap 0.5% vs Fees 1.2%)");
        mock.reset();
        mock.setPrice("binance", "BNBUSDT", 300.0);
        mock.setPrice("bybit_sub1", "BNBUSDT", 301.5);
        mock.setLiquidity("BNBUSDT", 500.0);
        mock.setFeeRate(0.006);

        scanner.updateTargets(List.of("BNB"));
        measureScenario("FEE_TRAP", scanner);

        // =====================================================================
        // ❌ ESCENARIO 4: SLIPPAGE HELL
        // =====================================================================
        BotLogger.info("\n🔴 [TEST 4] SLIPPAGE: XRP (Gap 5% pero Liquidez $5)");
        mock.reset();
        mock.setPrice("binance", "XRPUSDT", 1.00);
        mock.setPrice("bybit_sub1", "XRPUSDT", 1.05);
        mock.setLiquidity("XRPUSDT", 5.0);

        scanner.updateTargets(List.of("XRP"));
        measureScenario("SLIPPAGE_FAIL", scanner);

        // =====================================================================
        // ❌ ESCENARIO 5: LATENCY KILLER
        // =====================================================================
        BotLogger.info("\n🐢 [TEST 5] LATENCY: DOGE (Gap 10% pero Ping 500ms)");
        mock.reset();
        mock.setPrice("binance", "DOGEUSDT", 0.10);
        mock.setPrice("bybit_sub1", "DOGEUSDT", 0.11);
        mock.setLiquidity("DOGEUSDT", 100000.0);
        mock.setSimulatedLatencyMs(500); // 500ms de Lag simulado

        scanner.updateTargets(List.of("DOGE"));
        measureScenario("LATENCY_FAIL", scanner);

        long suiteEnd = System.currentTimeMillis();
        BotLogger.info("\n🏁 SUITE COMPLETADA en " + (suiteEnd - suiteStart) + "ms totales.");
        System.exit(0);
    }

    // ⏱️ METRÓLOGO: Mide el tiempo exacto de procesamiento
    private static void measureScenario(String name, DeepMarketScanner scanner) {
        try {
            java.lang.reflect.Method method = DeepMarketScanner.class.getDeclaredMethod("scanFullMatrixBatchOptimized");
            method.setAccessible(true);

            // Inicio Cronómetro
            long start = System.nanoTime();

            // 🔥 EJECUCIÓN PURA
            method.invoke(scanner);

            // Fin Cronómetro
            long end = System.nanoTime();

            double durationMs = (end - start) / 1_000_000.0;

            // Colores según velocidad
            String speedIcon = durationMs < 10 ? "⚡" : (durationMs < 100 ? "🏎️" : "🐢");
            BotLogger.info(String.format("⏱️ [BENCHMARK] %s: %s %.4f ms", name, speedIcon, durationMs));

            // Pausa visual (fuera del cronómetro)
            Thread.sleep(200);
        } catch (Exception e) {
            BotLogger.error("Error en benchmark: " + e.getMessage());
        }
    }

    // =========================================================================
    // 🎭 MOCK GENÉRICO (Igual que antes)
    // =========================================================================
    static class GenericMockConnector extends ExchangeConnector {
        private final Map<String, Double> prices = new ConcurrentHashMap<>();
        private final Map<String, Double> liquidity = new ConcurrentHashMap<>();
        private double feeRate = 0.001;
        private long simulatedLatency = 10;

        public void reset() {
            prices.clear();
            liquidity.clear();
            feeRate = 0.001;
            simulatedLatency = 10;
        }

        public void setPrice(String ex, String pair, double price) {
            prices.put(ex + "_" + pair, price);
        }

        public void setLiquidity(String pair, double amount) {
            liquidity.put(pair, amount);
        }

        public void setFeeRate(double rate) { this.feeRate = rate; }
        public void setSimulatedLatencyMs(long ms) { this.simulatedLatency = ms; }

        @Override
        public Map<String, Double> fetchAllPrices(String exchange) {
            Map<String, Double> result = new HashMap<>();
            prices.forEach((key, val) -> {
                if (key.startsWith(exchange)) result.put(key.split("_")[1], val);
            });
            return result;
        }

        @Override
        public OrderBook fetchOrderBook(String exchange, String pair, int depth) {
            double price = prices.getOrDefault(exchange + "_" + pair, 0.0);
            if (price == 0.0) return new OrderBook(new ArrayList<>(), new ArrayList<>());
            double qty = liquidity.getOrDefault(pair, 10000.0);
            List<double[]> bids = new ArrayList<>();
            List<double[]> asks = new ArrayList<>();
            for(int i=0; i<5; i++) {
                bids.add(new double[]{price, qty});
                asks.add(new double[]{price, qty});
            }
            return new OrderBook(bids, asks);
        }

        @Override
        public Map<String, Double> fetchBalances(String exchange) {
            Map<String, Double> bal = new HashMap<>();
            bal.put("USDT", 50000.0);
            bal.put("BTC", 10.0);
            bal.put("SOL", 500.0);
            bal.put("ETH", 100.0);
            bal.put("BNB", 500.0);
            bal.put("XRP", 50000.0);
            bal.put("DOGE", 50000.0);
            return bal;
        }

        @Override
        public long getRTT(String exchange) { return simulatedLatency; }

        @Override
        public double[] fetchDynamicTradingFee(String exchange, String pair) {
            return new double[]{feeRate, feeRate};
        }
    }
}