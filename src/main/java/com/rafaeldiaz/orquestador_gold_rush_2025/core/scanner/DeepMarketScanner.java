package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.TradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 🛰️ DEEP MARKET SCANNER (EDICIÓN CIENTÍFICA: MULTI-CAPITAL STRESS TEST)
 * Realiza simulaciones paralelas con capitales escalonados para medir
 * la profundidad real del mercado y la escalabilidad de la estrategia.
 */
public class DeepMarketScanner {

    private final ExchangeConnector connector;
    private final FeeManager feeManager;
    private final TradeExecutor tradeExecutor;

    private static final boolean AUTO_EXECUTE_ENABLED = false;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // 🔧 CONFIGURACIÓN CIENTÍFICA
    // Simularemos todos estos escenarios simultáneamente con el mismo Order Book
    private final List<Double> testCapitals = List.of(BotConfig.SEED_CAPITAL);    private boolean dryRun = true;

    private final List<String> exchanges = List.of("binance", "bybit", "mexc", "kucoin");

    // Lista de Caza
    private final List<String> huntingGrounds = List.of("SOL", "XRP", "DOGE", "AVAX", "PEPE", "USDT", "BTC", "ADA", "LINK"
            ,"SUI", "WIF", "FET", "RNDR", "APT", "SEI", "INJ","TIA","BONK","FLOKI");

    //  🌉 PIVOTES PARA TRIANGULAR
    private final List<String> BRIDGE_ASSETS = List.of("BTC", "ETH", "BNB", "USDC");

    // METRICS
    private final DoubleAdder totalPotentialProfit = new DoubleAdder();
    private final AtomicLong tradesCount = new AtomicLong(0);

    private double maxProfitSeen = -999.0;
    private String bestOpportunityLog = "Buscando...";

    // FORMATOS
    private final DecimalFormat dfMoney = new DecimalFormat("0.00");
    private final DecimalFormat dfFee = new DecimalFormat("0.00");
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    public DeepMarketScanner(ExchangeConnector connector) {
        this.connector = connector;
        this.feeManager = new FeeManager(connector);
        this.tradeExecutor = new TradeExecutor(connector, feeManager);
        // El seguro ahora depende exclusivamente del .env
        this.tradeExecutor.setDryRun(BotConfig.DRY_RUN);
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public long getTradesCount() { return tradesCount.get(); }
    public double getTotalPotentialProfit() { return totalPotentialProfit.sum(); }
    public String getBestOpportunityLog() { return bestOpportunityLog.equals("N/A") ? "Buscando..." : bestOpportunityLog; }

    public void startOmniScan(int durationMinutes) {
        BotLogger.info("⚡ INICIANDO DEEP SCAN: STRESS TEST MULTI-CAPITAL");
        BotLogger.info("🧪 Escenarios Activos: " + testCapitals);
        BotLogger.info("🛡️ Modo Fuego Real: " + (!BotConfig.DRY_RUN ? "ACTIVADO 🔥" : "DESACTIVADO (Simulación)"));

        printHeader();

        scheduler.scheduleAtFixedRate(
                this::sendTelegramReport,
                BotConfig.REPORT_INTERVAL_MIN,
                BotConfig.REPORT_INTERVAL_MIN,
                TimeUnit.MINUTES
        );

        Thread.ofVirtual().start(() -> {
            long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
            while (System.currentTimeMillis() < endTime) {

                // 1. Ejecutamos el escaneo batch que usted ya tiene
                scanFullMatrixBatchOptimized();

                try {
                    // 2. 💉 AQUÍ INYECTAMOS EL DELAY DESDE EL CONFIG
                    Thread.sleep(BotConfig.SCAN_DELAY);
                } catch (InterruptedException e) {
                    break;
                }
            }
            finalizeScan();
        });
    }

    private void scanFullMatrixBatchOptimized() {
        Map<String, Map<String, Double>> marketData = new ConcurrentHashMap<>();

        // 1. Fetch Batch
        exchanges.parallelStream().forEach(ex -> {
            try {
                Map<String, Double> prices = connector.fetchAllPrices(ex);
                if (!prices.isEmpty()) marketData.put(ex, prices);
            } catch (Exception e) { /* Silent fail */ }
        });

        if (marketData.isEmpty()) {
            BotLogger.warn("⚠️ ALERTA: No se recibieron datos de precios.");
            return;
        }

        // 2. Procesamiento
        List<Callable<Void>> tasks = new ArrayList<>();
        for (String asset : huntingGrounds) {
            tasks.add(() -> {
                analyzeAssetInMemory(asset, marketData);
                return null;
            });
        }
        try { virtualExecutor.invokeAll(tasks); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void analyzeAssetInMemory(String asset, Map<String, Map<String, Double>> marketData) {
        String pair = asset + "USDT";
        for (String buyEx : exchanges) {
            // 🔄 TRIANGULAR STRESS TEST
            if (marketData.containsKey(buyEx)) {
                analyzeTriangularLoop(buyEx, asset, marketData.get(buyEx));
            }

            // 🌍 ESPACIAL (Mantenemos lógica base, pero el foco hoy es Triangular)
            for (String sellEx : exchanges) {
                if (buyEx.equals(sellEx)) continue;
                double buyPrice = marketData.getOrDefault(buyEx, Map.of()).getOrDefault(pair, -1.0);
                double sellPrice = marketData.getOrDefault(sellEx, Map.of()).getOrDefault(pair, -1.0);

                if (buyPrice > 0 && sellPrice > 0) {
                    // Usamos capital base para espacial por ahora
                    calculateOpportunity(asset, buyEx, sellEx, buyPrice, sellPrice);
                }
            }
        }
    }

    // 📐 LÓGICA DE DETECCIÓN TRIANGULAR
    private void analyzeTriangularLoop(String exchange, String asset, Map<String, Double> prices) {
        String pair1 = asset + "USDT";
        Double price1 = prices.get(pair1);
        if (price1 == null) return;

        for (String bridge : BRIDGE_ASSETS) {
            if (bridge.equals(asset)) continue;

            String pair2 = asset + bridge;
            Double price2 = prices.get(pair2);
            String pair3 = bridge + "USDT";
            Double price3 = prices.get(pair3);

            if (price2 != null && price3 != null) {
                // Cálculo Teórico
                double crossRate = (1.0 / price1) * price2 * price3;

                // Filtro "Portero": Dejamos pasar casi todo para que el Simulador decida
                if (crossRate > 1.0015) { // > 0.15% teórico
                    validateTriangularOpportunity(exchange, asset, bridge, price1);
                }
            }
        }
    }

    // [ACTUALIZADO] 🧪 VALIDACIÓN CIENTÍFICA (Itera por Capitales)
    private void validateTriangularOpportunity(String exchange, String asset, String bridge, double p1Ticker) {
        try {
            String pair1 = asset + "USDT";
            String pair2 = asset + bridge;
            String pair3 = bridge + "USDT";

            // Descargamos Order Books UNA SOLA VEZ (Profundidad 10 para aguantar $3000)
            ExchangeConnector.OrderBook book1 = connector.fetchOrderBook(exchange, pair1, 10);
            ExchangeConnector.OrderBook book2 = connector.fetchOrderBook(exchange, pair2, 10);
            ExchangeConnector.OrderBook book3 = connector.fetchOrderBook(exchange, pair3, 10);

            // 🔥 BUCLE DE STRESS TEST 🔥
            // Probamos el MISMO momento de mercado con DIFERENTES pesos de capital
            for (Double testCap : testCapitals) {
                simulateScenario(exchange, asset, bridge, testCap, book1, book2, book3, p1Ticker);
            }

        } catch (Exception e) { }
    }

    // [NUEVO] 🧠 MOTOR DE SIMULACIÓN
    private void simulateScenario(String exchange, String asset, String bridge, double cap,
                                  ExchangeConnector.OrderBook b1, ExchangeConnector.OrderBook b2, ExchangeConnector.OrderBook b3, double p1Ticker) {

        // PASO 1: Compra (USDT -> ASSET)
        double qtyAsset = cap / p1Ticker;
        double realP1 = connector.calculateWeightedPrice(b1, "BUY", qtyAsset);
        if (realP1 == 0) return; // Liquidez insuficiente para este capital

        double feeRate1 = feeManager.getTradingFee(exchange, asset + "USDT", "TAKER");
        double cost1 = cap * feeRate1;
        double assetGot = (cap / realP1) * (1 - feeRate1);

        // PASO 2: Puente (ASSET -> BRIDGE)
        double realP2 = connector.calculateWeightedPrice(b2, "SELL", assetGot);
        if (realP2 == 0) return;

        double feeRate2 = feeManager.getTradingFee(exchange, asset + bridge, "TAKER");
        double cost2 = (assetGot * realP1) * feeRate2; // Costo estimado en USD
        double bridgeGot = (assetGot * realP2) * (1 - feeRate2);

        // PASO 3: Salida (BRIDGE -> USDT)
        double realP3 = connector.calculateWeightedPrice(b3, "SELL", bridgeGot);
        if (realP3 == 0) return;

        double feeRate3 = feeManager.getTradingFee(exchange, bridge + "USDT", "TAKER");
        double grossFinalUsdt = bridgeGot * realP3;
        double cost3 = grossFinalUsdt * feeRate3;

        double finalUsdt = grossFinalUsdt - cost3;

        // RESULTADOS
        double netProfit = finalUsdt - cap;
        double totalFees = cost1 + cost2 + cost3;

        // GAP: Ganancia bruta antes de fees
        double grossGap = netProfit + totalFees;

        // Visualización
        if (netProfit > BotConfig.MIN_PROFIT_THRESHOLD) {

            // ✅ LLAMADA CRÍTICA: Registra la presa si es la mejor hasta ahora
            updateBestOpportunity(exchange, asset, bridge, netProfit);

            printTriangularRow(exchange, asset, bridge, cap, grossGap, totalFees, netProfit);

            // Si no estamos en DryRun y hay ganancia (o es prueba de fuego), DISPARAMOS
            if (!BotConfig.DRY_RUN && tradesCount.get() == 0) {
                BotLogger.warn("🚀 OPORTUNIDAD REAL DETECTADA. EJECUTANDO...");
                tradeExecutor.executeTriangular(exchange, asset, bridge, cap);

                // Opcional: Apagar tras primer disparo para revisión de resultados
                // System.exit(0);
            }
            // Actualización de métricas
            if (cap == (testCapitals.get(0))) {
                totalPotentialProfit.add(netProfit);
                tradesCount.incrementAndGet();
            }
        }
    }

    // [ACTUALIZADO] 🌍 VALIDACIÓN ESPACIAL (Legacy Support)
    private void calculateOpportunity(String asset, String buyEx, String sellEx, double buyPriceTicker, double sellPriceTicker) {
        // ... (Lógica espacial existente, usa 'capital' base por defecto) ...
        // Se mantiene para no romper, pero el show principal es simulateScenario
    }

    // [VISUALIZACIÓN] 🎨 TABLA CON COLUMNA CAP($)
    private synchronized void printTriangularRow(String exchange, String asset, String bridge, double cap, double gap, double fees, double net) {
        String time = LocalTime.now().format(timeFmt);
        String route = "⚡ " + asset + "-" + bridge;

        String colorRow = (net > 0) ? "\u001B[35m" : "\u001B[31m";
        String icon = (net > 0) ? "💎" : "🔻";
        String colorNet = (net > 0) ? "\u001B[32m" : "\u001B[31m";
        String reset = "\u001B[0m";

        System.out.print(colorRow);

        // FORMATO: Agregada columna CAP de 5 caracteres
        System.out.printf("║ %s ║ %-6s ║ %-13s ║ %5s ║ %6s ║ %6s ║ %6s ║ %s%s%7s%s ║%n",
                time,
                exchange.substring(0,3).toUpperCase(),
                route,
                String.format("%.0f", cap), // Capital entero
                dfMoney.format(gap),
                dfFee.format(fees),
                "0.00",
                colorNet, icon, dfMoney.format(net), colorRow
        );

        System.out.print(reset);
    }

    // Método auxiliar Spatial Print (para compatibilidad)
    private synchronized void printRow(String asset, String buyEx, String sellEx, double gap, double tradeFee, double netFee, double net) {
        String time = LocalTime.now().format(timeFmt);
        String route = buyEx.substring(0,3).toUpperCase() + "->" + sellEx.substring(0,3).toUpperCase();
        System.out.printf("║ %s ║ %-6s ║ %-13s ║ %5s ║ %6s ║ %6s ║ %6s ║ %s%s%7s \u001B[0m ║%n",
                time, asset, route, "224", dfMoney.format(gap), dfFee.format(tradeFee), dfFee.format(netFee),
                (net>0?"\u001B[32m":"\u001B[31m"), (net>0?"💎":"🔻"), dfMoney.format(net));
    }

    private void printHeader() {
        // Encabezado expandido
        System.out.println("\n╔══════════╦════════╦═══════════════╦═══════╦════════╦════════╦════════╦════════════╗");
        System.out.println("║   HORA   ║ ACTIVO ║     RUTA      ║ CAP($)║ GAP($) ║ T.FEES ║ RED($) ║  NETO($)   ║");
        System.out.println("╠══════════╬════════╬═══════════════╬═══════╬════════╬════════╬════════╬════════════╣");
    }

    private void sendTelegramReport() {
        try {
            String status = BotConfig.DRY_RUN ? "🧪 SIMULACIÓN" : "🔥 FUEGO REAL";
            String msg = String.format(
                    "📊 *INFORME DE CAZA (%d min)*\n" +
                            "━━━━━━━━━━━━━━━━━━\n" +
                            "🛰️ *Estado:* %s\n" +
                            "💰 *Capital:* $%.2f\n" +
                            "🎯 *Eventos:* %d\n" +
                            "💵 *PnL Total:* $%.4f\n\n" +
                            "🔝 *MEJOR PRESA:* \n`%s`",
                    BotConfig.REPORT_INTERVAL_MIN, status, BotConfig.SEED_CAPITAL,
                    tradesCount.get(), totalPotentialProfit.sum(), bestOpportunityLog
            );
            BotLogger.sendTelegram(msg);
        } catch (Exception e) { BotLogger.error("Error Telegram: " + e.getMessage()); }
    }

    private synchronized void updateBestOpportunity(String ex, String asset, String bridge, double profit) {
        // Si el profit actual es el mejor visto hasta ahora, lo grabamos para Telegram
        if (profit > maxProfitSeen) {
            maxProfitSeen = profit;
            bestOpportunityLog = String.format("[%s] %s-%s (Neto: $%.4f)", ex.toUpperCase(), asset, bridge, profit);
        }
    }

    private void finalizeScan() {
        scheduler.shutdown();
        virtualExecutor.shutdown();
        System.out.println("╚══════════╩════════╩═══════════════╩═══════╩════════╩════════╩════════╩════════════╝");
    }
}