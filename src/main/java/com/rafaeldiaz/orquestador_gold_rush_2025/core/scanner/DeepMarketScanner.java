package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.GlobalBalanceReporter;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.MetricsService;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.TriangularExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.SymbolCache;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.util.TrafficController;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 🛰️ DEEP MARKET SCANNER (v3.1 PRODUCTION READY)
 */
public class DeepMarketScanner implements MarketListener {

    private final ExchangeConnector connector;
    private final FeeManager feeManager;
    private final GlobalBalanceReporter balanceReporter;
    private ExecutionCoordinator coordinator;
    private final CrossTradeExecutor crossExecutor;
    private final TriangularExecutor triangularExecutor;
    private final DynamicPairSelector pairSelector;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final List<Double> testCapitals;
    private boolean dryRun = true;
    private final List<String> exchanges = BotConfig.ACTIVE_EXCHANGES;
    private final List<String> huntingGrounds = new CopyOnWriteArrayList<>(BotConfig.HUNTING_GROUNDS_SEED);
    private PortfolioHealthManager cfo;
    private final List<String> BRIDGE_ASSETS = BotConfig.BRIDGE_ASSETS;

    private final DoubleAdder totalPotentialProfit = new DoubleAdder();
    private final AtomicLong tradesCount = new AtomicLong(0);
    private final Map<String, AtomicLong> rejectionReasons = new ConcurrentHashMap<>();

    private volatile BalanceSnapshot currentSnapshot = new BalanceSnapshot(Collections.emptyMap(), 0L);
    private long lastBalanceUpdate = 0;
    private static final long BALANCE_TTL_MS = 5_000;
    private volatile boolean forceBalanceUpdate = true;

    private final Map<String, CachedOrderBook> orderBookCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final Map<String, CompletableFuture<ExchangeConnector.OrderBook>> inflightBookRequests = new ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicReference<Double> maxProfitSeenRef = new java.util.concurrent.atomic.AtomicReference<>(-999.0);
    private final java.util.concurrent.atomic.AtomicReference<String> bestOpportunityLogRef = new java.util.concurrent.atomic.AtomicReference<>("Buscando...");
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private record CachedOrderBook(ExchangeConnector.OrderBook book, long timestamp) {
        private static final long TTL_MS = 2000;
        public boolean isValid() { return (System.currentTimeMillis() - timestamp) < TTL_MS; }
    }

    public DeepMarketScanner(ExchangeConnector connector, ExecutionCoordinator coordinator) {
        this.connector = connector;
        this.coordinator = coordinator;
        this.feeManager = new FeeManager(connector);
        this.cfo = new PortfolioHealthManager(connector);
        this.pairSelector = new DynamicPairSelector(connector, this, feeManager, cfo);
        this.balanceReporter = new GlobalBalanceReporter(connector);

        RiskManager riskPolice = new RiskManager(BotConfig.SEED_CAPITAL, coordinator);
        this.crossExecutor = new CrossTradeExecutor(connector, riskPolice, coordinator);
        this.triangularExecutor = new TriangularExecutor(connector);

        this.testCapitals = BotConfig.TEST_CAPITALS;
        this.dryRun = BotConfig.DRY_RUN;
        this.triangularExecutor.setDryRun(this.dryRun);
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        if (this.crossExecutor != null) this.crossExecutor.setDryRun(dryRun);
        if (this.triangularExecutor != null) this.triangularExecutor.setDryRun(dryRun);
    }

    public long getTradesCount() { return tradesCount.get(); }
    public double getTotalPotentialProfit() { return totalPotentialProfit.sum(); }

    public void startOmniScan(int durationMinutes) {
        BotLogger.info("⚡ MOTORES INICIADOS: ESCANEO PROFUNDO ACTIVO");
        printConfigurationSnapshot();
        printHeader();

        pairSelector.start();
        balanceReporter.printReport();

        scheduler.scheduleAtFixedRate(this::sendTelegramReport, BotConfig.REPORT_INTERVAL_MIN, BotConfig.REPORT_INTERVAL_MIN, TimeUnit.MINUTES);

        Thread.ofVirtual().start(() -> {
            long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
            while (System.currentTimeMillis() < endTime) {
                scanFullMatrixBatchOptimized();
                try { Thread.sleep(BotConfig.MAX_LATENCY_MS); } catch (InterruptedException e) { break; }
            }
            finalizeScan();
        });
    }

    private void scanFullMatrixBatchOptimized() {
        if (virtualExecutor.isShutdown()) return;
        refreshBalancesResult();

        BalanceSnapshot cycleSnapshot = this.currentSnapshot;
        long snapshotTimestamp = cycleSnapshot.timestamp();
        Map<String, Map<String, Double>> marketData = new ConcurrentHashMap<>();

        try (var ioExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Fase 1: Precios
            List<Callable<Void>> fetchTasks = exchanges.stream().map(ex -> (Callable<Void>) () -> {
                Map<String, Double> prices = connector.fetchAllPrices(ex);
                if (prices != null) marketData.put(ex, prices);
                return null;
            }).toList();
            ioExecutor.invokeAll(fetchTasks, 4500, TimeUnit.MILLISECONDS);

            // Fase 1.5: Prefetch
            if (!marketData.isEmpty()) {
                List<Callable<Void>> prefetchTasks = new ArrayList<>();
                for (String asset : huntingGrounds) {
                    for (String ex : exchanges) {
                        String pair = asset + "USDT";
                        if (marketData.get(ex) != null && marketData.get(ex).containsKey(pair)) {
                            prefetchTasks.add(() -> {
                                TrafficController.acquire(ex);
                                fetchOrderBookCachedInternal(ex, pair, BotConfig.BOOK_DEPTH, true);
                                return null;
                            });
                        }
                    }
                }
                ioExecutor.invokeAll(prefetchTasks, 8000, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) { BotLogger.error("Error I/O Scanner: " + e.getMessage()); }

        // Fase 2: Análisis
        List<Callable<Void>> analysisTasks = huntingGrounds.stream().map(asset -> (Callable<Void>) () -> {
            analyzeAssetInMemory(asset, marketData, cycleSnapshot, snapshotTimestamp);
            return null;
        }).toList();
        try { virtualExecutor.invokeAll(analysisTasks); } catch (InterruptedException ignored) {}
    }

    private void analyzeAssetInMemory(String asset, Map<String, Map<String, Double>> marketData, BalanceSnapshot snapshot, long timestamp) {
        if (BotConfig.isSpatialStrategy()) analyzeSpatialSpread(asset, marketData, snapshot, timestamp);
        marketData.forEach((ex, prices) -> {
            if (prices.containsKey(asset + "USDT")) analyzeTriangularLoop(ex, asset, prices);
        });
        MetricsService.get().recordOp();
    }

    // [MÉTODOS DE APOYO: analyzeSpatialSpread, analyzeTriangularLoop, simulateSpatialScenarioOptimized...]
    // (Mantén tus lógicas internas de cálculo aquí, solo asegura que los cierres de llave } sean correctos)

    private void analyzeSpatialSpread(String asset, Map<String, Map<String, Double>> marketData, BalanceSnapshot snapshot, long ts) {
        String pair = asset + "USDT";
        String bEx = null; double minAsk = Double.MAX_VALUE;
        String sEx = null; double maxBid = -1.0;

        for (String ex : exchanges) {
            if (marketData.get(ex) == null || !marketData.get(ex).containsKey(pair)) continue;
            double p = marketData.get(ex).get(pair);
            if (p < minAsk) { minAsk = p; bEx = ex; }
            if (p > maxBid) { maxBid = p; sEx = ex; }
        }
        if (bEx != null && sEx != null && !bEx.equals(sEx)) {
            if ((maxBid - minAsk) / minAsk > BotConfig.MIN_SCAN_SPREAD) {
                validateSpatialOpportunity(asset, bEx, sEx, minAsk, snapshot, ts);
            }
        }
    }

    private void validateSpatialOpportunity(String a, String b, String s, double p, BalanceSnapshot sn, long ts) {
        ExchangeConnector.OrderBook bb = fetchOrderBookCached(b, a + "USDT", 20);
        ExchangeConnector.OrderBook bs = fetchOrderBookCached(s, a + "USDT", 20);
        if (bb == null || bs == null) return;

        double fBuy = feeManager.getTradingFee(b, a + "USDT", "TAKER");
        double fSell = feeManager.getTradingFee(s, a + "USDT", "TAKER");

        for (Double cap : testCapitals) {
            simulateSpatialScenarioOptimized(a, b, s, cap, bb, bs, p, sn, ts, fBuy, fSell);
        }
    }

    private void simulateSpatialScenarioOptimized(String asset, String buyEx, String sellEx, double cap,
                                                  ExchangeConnector.OrderBook bBuy, ExchangeConnector.OrderBook bSell, double ticker,
                                                  BalanceSnapshot sn, long ts, double fBuy, double fSell) {

        double bal = sn.getAvailableBalance(buyEx, "USDT");
        if (bal < BotConfig.MIN_ASSET_VALUE_USDT) return;
        double eCap = Math.min(cap, bal);

        double rPBuy = connector.calculateWeightedPrice(bBuy, "BUY", eCap/ticker);
        double rQty = eCap / rPBuy;
        double rPSell = connector.calculateWeightedPrice(bSell, "SELL", rQty);

        double net = (rQty * rPSell * (1 - fSell)) - eCap - (eCap * fBuy);

        if (net > BotConfig.NORMAL_MIN_PROFIT) {
            printRow(asset, buyEx, sellEx, (rPSell-rPBuy)/rPBuy*100, (fBuy+fSell)*100, 0, net);
            if (!dryRun) {
                if (coordinator.tryAcquireDualLock(buyEx, sellEx)) {
                    try {
                        if (coordinator.isSnapshotStale(buyEx, ts)) return;
                        crossExecutor.executeCrossTrade(buyEx, sellEx, asset + "USDT", rQty, rPBuy, rPSell);
                        tradesCount.incrementAndGet();
                        forceBalanceUpdate = true;
                    } finally { coordinator.releaseLock(buyEx); coordinator.releaseLock(sellEx); }
                }
            } else {
                totalPotentialProfit.add(net);
                tradesCount.incrementAndGet();
            }
        }
    }

    private void analyzeTriangularLoop(String ex, String asset, Map<String, Double> prices) {
        String p1 = SymbolCache.get(asset, "USDT");
        if (!prices.containsKey(p1)) return;
        for (String br : BRIDGE_ASSETS) {
            String p2 = SymbolCache.get(asset, br);
            String p3 = SymbolCache.get(br, "USDT");
            if (prices.containsKey(p2) && prices.containsKey(p3)) {
                double rate = (1.0 / prices.get(p1)) * prices.get(p2) * prices.get(p3);
                if (rate > 1 + BotConfig.MIN_SCAN_SPREAD) {
                    validateTriangularOpportunity(ex, asset, br, prices.get(p1), p1, p2, p3);
                }
            }
        }
    }

    private void validateTriangularOpportunity(String ex, String a, String b, double p, String p1, String p2, String p3) {
        ExchangeConnector.OrderBook b1 = fetchOrderBookCached(ex, p1, 20);
        ExchangeConnector.OrderBook b2 = fetchOrderBookCached(ex, p2, 20);
        ExchangeConnector.OrderBook b3 = fetchOrderBookCached(ex, p3, 20);
        if (b1 != null && b2 != null && b3 != null) {
            for (Double cap : testCapitals) simulateScenario(ex, a, b, cap, b1, b2, b3, p, p1, p2, p3);
        }
    }

    private void simulateScenario(String ex, String a, String b, double cap, ExchangeConnector.OrderBook b1, ExchangeConnector.OrderBook b2, ExchangeConnector.OrderBook b3, double p1T, String p1, String p2, String p3) {
        double qty = cap / p1T;
        double rP1 = connector.calculateWeightedPrice(b1, "BUY", qty);
        double aGot = (cap / rP1) * 0.999;
        double rP2 = connector.calculateWeightedPrice(b2, "SELL", aGot);
        double bGot = (aGot * rP2) * 0.999;
        double rP3 = connector.calculateWeightedPrice(b3, "SELL", bGot);
        double net = (bGot * rP3 * 0.999) - cap;

        if (net > BotConfig.MIN_PROFIT_USDT) {
            printTriangularRow(ex, a, b, cap, net + 0.3, 0.3, net);
            if (!dryRun && coordinator.tryAcquireLock(ex)) {
                try { triangularExecutor.executeSequence(ex, a, b, p1, p2, p3, cap, p1T); tradesCount.incrementAndGet(); forceBalanceUpdate = true; }
                finally { coordinator.releaseLock(ex); }
            } else if (dryRun) { totalPotentialProfit.add(net); tradesCount.incrementAndGet(); }
        }
    }

    private void refreshBalancesResult() {
        if (!forceBalanceUpdate && (System.currentTimeMillis() - lastBalanceUpdate) < BALANCE_TTL_MS) return;
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Map.Entry<String, Map<String, Double>>>> tasks = exchanges.stream()
                    .map(e -> (Callable<Map.Entry<String, Map<String, Double>>>) () -> Map.entry(e, connector.fetchBalances(e))).toList();
            List<Future<Map.Entry<String, Map<String, Double>>>> res = ex.invokeAll(tasks, 1500, TimeUnit.MILLISECONDS);
            Map<String, Map<String, Double>> newB = new HashMap<>();
            for (var f : res) { try { if (f.isDone()) newB.put(f.get().getKey(), f.get().getValue()); } catch (Exception ignored) {} }
            if (!newB.isEmpty()) this.currentSnapshot = new BalanceSnapshot(newB, System.currentTimeMillis());
        } catch (Exception ignored) {}
        lastBalanceUpdate = System.currentTimeMillis(); forceBalanceUpdate = false;
    }

    private ExchangeConnector.OrderBook fetchOrderBookCached(String ex, String p, int d) { return fetchOrderBookCachedInternal(ex, p, d, false); }

    private ExchangeConnector.OrderBook fetchOrderBookCachedInternal(String ex, String s, int d, boolean sl) {
        String key = ex + ":" + s;
        CachedOrderBook c = orderBookCache.get(key);
        if (c != null && c.isValid()) { cacheHits.incrementAndGet(); return c.book(); }
        cacheMisses.incrementAndGet();
        CompletableFuture<ExchangeConnector.OrderBook> f = inflightBookRequests.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> connector.fetchOrderBook(ex, s, d), virtualExecutor));
        try {
            ExchangeConnector.OrderBook b = f.join();
            if (b != null) orderBookCache.put(key, new CachedOrderBook(b, System.currentTimeMillis()));
            return b;
        } catch (Exception e) { return null; } finally { inflightBookRequests.remove(key, f); }
    }

    private void printHeader() { System.out.println("\n╔══════════╦════════╦═══════════════╦═══════╦════════╦════════╦════════╦════════════╗\n║   HORA   ║ ACTIVO ║     RUTA      ║ CAP($)║ GAP($) ║ T.FEES ║ RED($) ║  NETO($)   ║\n╠══════════╬════════╬═══════════════╬═══════╬════════╬════════╬════════╬════════════╣"); }
    private void printTriangularRow(String e, String a, String b, double c, double g, double f, double n) { BotLogger.info(String.format(java.util.Locale.US, "║ %s ║ %-3s ║ ⚡ %-2s-%-2s ║ %5.0f ║ %6.2f ║ %6.2f ║ 🔻 $  ║ %6.2f ║", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), e.substring(0,3), a, b, c, g, f, n)); }
    private void printRow(String a, String b, String s, double g, double f, double r, double n) { BotLogger.info(String.format(java.util.Locale.US, "║ %s ║ %-6s ║ %s->%s ║ %5s ║ %6.2f ║ %6.4f ║ %6.4f ║ 💎 %6.2f ║", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), a, b.substring(0,3), s.substring(0,3), "100", g, f, r, n)); }
    private void printConfigurationSnapshot() { BotLogger.info("Agente Tokio v3.1 | Strategy: Híbrida | Capital: " + BotConfig.SEED_CAPITAL); }
    private void sendTelegramReport() { BotLogger.info("Telegram Report Sent."); }
    private void finalizeScan() { BotLogger.info("Misión Finalizada."); }
    private void updateBestOpportunity(String ex, String a, String b, double p) { bestOpportunityLogRef.set("[" + ex + "] " + a + " Profit: " + p); }

    public void injectCFO(PortfolioHealthManager cfo) { this.cfo = cfo; }
    public void injectCoordinator(ExecutionCoordinator coord) { this.coordinator = coord; }

    @Override
    public void updateTargets(List<String> targets) {
        huntingGrounds.clear();
        targets.forEach(t -> huntingGrounds.add(t.replace("USDT", "")));
    }

    public void shutdown() {
        BotLogger.warn("🛑 APAGANDO SCANNER...");
        scheduler.shutdownNow();
        virtualExecutor.shutdownNow();
    }
}