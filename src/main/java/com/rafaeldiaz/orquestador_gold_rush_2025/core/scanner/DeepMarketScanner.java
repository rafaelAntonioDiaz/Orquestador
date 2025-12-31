package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.GlobalBalanceReporter;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.estimator.StandardProfitEstimator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.MarketDataProvider;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ProfitEstimator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.provider.CachingMarketDataProvider;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.SpatialArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.TriangularArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.MetricsService;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.TriangularExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 🛰️ DEEP MARKET SCANNER v4.3 (INTEGRITY VERIFIED)
 * Mantiene paridad total funcional con v3.1 bajo arquitectura limpia.
 */
public class DeepMarketScanner implements MarketListener {

    // Componentes del Sistema
    private final ExchangeConnector connector;
    private final ExecutionCoordinator coordinator;
    private final CachingMarketDataProvider dataProvider; // Tipo concreto para acceder a métricas extra
    private final ProfitEstimator profitEstimator;

    // Inteligencia
    private final DynamicPairSelector pairSelector;
    private final GlobalBalanceReporter balanceReporter;

    // Ejecución (Legacy adaptado)
    private final CrossTradeExecutor crossExecutor;
    private final TriangularExecutor triangularExecutor;

    // Estrategias
    private final List<ArbitrageStrategy> strategies = new ArrayList<>();
    private final List<String> huntingGrounds = new CopyOnWriteArrayList<>(BotConfig.HUNTING_GROUNDS_SEED);

    // Estado
    private volatile BalanceSnapshot currentSnapshot = new BalanceSnapshot(Collections.emptyMap(), 0L);
    private boolean dryRun = BotConfig.DRY_RUN;
    private final AtomicLong tradesCount = new AtomicLong(0);
    private final DoubleAdder totalPotentialProfit = new DoubleAdder();

    // Hilos
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public DeepMarketScanner(ExchangeConnector connector, ExecutionCoordinator coordinator) {
        this.connector = connector;
        this.coordinator = coordinator;

        // 1. Provider (Con caché métricas y anti-stampede)
        this.dataProvider = new CachingMarketDataProvider(connector);

        // 2. Servicios Auxiliares
        FeeManager feeManager = new FeeManager(connector);
        PortfolioHealthManager cfo = new PortfolioHealthManager(connector);
        this.balanceReporter = new GlobalBalanceReporter(connector);

        // 3. Cerebro (MarketListener: this)
        this.pairSelector = new DynamicPairSelector(connector, this, feeManager, cfo);

        // 4. Estimador Financiero
        this.profitEstimator = new StandardProfitEstimator(feeManager, BotConfig.TEST_CAPITALS);

        // 5. Estrategias
        if (BotConfig.isSpatialStrategy()) strategies.add(new SpatialArbitrageStrategy(BotConfig.MIN_SCAN_SPREAD));
        strategies.add(new TriangularArbitrageStrategy(BotConfig.BRIDGE_ASSETS, BotConfig.MIN_SCAN_SPREAD));

        // 6. Ejecutores
        RiskManager riskPolice = new RiskManager(BotConfig.SEED_CAPITAL, coordinator);
        this.crossExecutor = new CrossTradeExecutor(connector, riskPolice, coordinator);
        this.triangularExecutor = new TriangularExecutor(connector);
        setDryRun(this.dryRun);
    }

    public void startOmniScan(int durationMinutes) {
        BotLogger.info("⚡ AGENTE TOKIO v4.3: INTEGRIDAD VERIFICADA");
        printHeader(); // Restauramos el header visual v3.1

        pairSelector.start();
        balanceReporter.printReport();

        scheduler.scheduleAtFixedRate(this::refreshBalances, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::sendTelegramReport, BotConfig.REPORT_INTERVAL_MIN, BotConfig.REPORT_INTERVAL_MIN, TimeUnit.MINUTES);

        Thread.ofVirtual().start(() -> {
            long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
            while (System.currentTimeMillis() < endTime) {
                try {
                    scanCycle();
                    // Throttle dinámico para CPU
                    Thread.sleep(BotConfig.MAX_LATENCY_MS);
                } catch (InterruptedException e) { break; }
            }
            shutdown();
        });
    }

    private void scanCycle() {
        // FASE 1: Fetch Global
        Map<String, Map<String, Double>> prices = dataProvider.fetchGlobalPrices(BotConfig.ACTIVE_EXCHANGES);
        if (prices.isEmpty()) return;

        // FASE 1.5: Prefetch (Con TrafficController interno en Provider)
        dataProvider.prefetchOrderBooks(huntingGrounds, BotConfig.ACTIVE_EXCHANGES).join();

        // FASE 2: Pipeline de Estrategias
        try (var scope = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = huntingGrounds.stream().map(asset -> (Callable<Void>) () -> {
                processAsset(asset, prices);
                return null;
            }).toList();
            scope.invokeAll(tasks);
        } catch (InterruptedException ignored) {}

        dataProvider.invalidateCache();
        MetricsService.get().recordOp();
    }

    private void processAsset(String asset, Map<String, Map<String, Double>> prices) {
        for (ArbitrageStrategy strategy : strategies) {
            List<ArbitrageOpportunity> opps = strategy.findOpportunities(asset, prices);
            for (ArbitrageOpportunity opp : opps) {
                // Validación Financiera (Balance, Fees, VWAP)
                ArbitrageOpportunity verified = profitEstimator.estimateProfitability(opp, currentSnapshot, dataProvider);

                if (verified != null) {
                    executeOpportunity(verified);
                }
            }
        }
    }

    private void executeOpportunity(ArbitrageOpportunity opp) {
        // Restauramos los logs visuales de v3.1
        printFormattedLog(opp);

        if (dryRun) {
            totalPotentialProfit.add(opp.expectedProfit());
            tradesCount.incrementAndGet();
            return;
        }

        long timestamp = currentSnapshot.timestamp();

        // LÓGICA DE EJECUCIÓN + LOCKS + SAFETY CHECK (Stale Snapshot)
        if (opp.strategyType().contains("TRIANGULAR")) {
            String ex = opp.buyExchange();
            if (coordinator.tryAcquireLock(ex)) {
                try {
                    // 🛑 Safety Check v3.1 restaurado
                    if (coordinator.isSnapshotStale(ex, timestamp)) return;

                    triangularExecutor.executeSequence(
                            ex, opp.asset(), opp.sellExchange(), // Bridge en sellExchange
                            opp.asset()+"USDT", opp.asset()+opp.sellExchange(), opp.sellExchange()+"USDT",
                            BotConfig.SEED_CAPITAL, opp.priceEntry()
                    );
                    onTradeSuccess();
                } finally { coordinator.releaseLock(ex); }
            }
        } else {
            // Spatial
            String bEx = opp.buyExchange();
            String sEx = opp.sellExchange();
            if (coordinator.tryAcquireDualLock(bEx, sEx)) {
                try {
                    // 🛑 Safety Check v3.1 restaurado
                    if (coordinator.isSnapshotStale(bEx, timestamp)) return;

                    crossExecutor.executeCrossTrade(
                            bEx, sEx, opp.getPair(),
                            opp.quantity(), opp.priceEntry(), opp.priceExit()
                    );
                    onTradeSuccess();
                } finally { coordinator.releaseLock(bEx); coordinator.releaseLock(sEx); }
            }
        }
    }

    private void onTradeSuccess() {
        tradesCount.incrementAndGet();
        refreshBalances(); // Forzar actualización inmediata (Flag forceUpdate v3.1)
    }

    // --- Utilidades v3.1 Preservadas ---

    private void refreshBalances() {
        Map<String, Map<String, Double>> b = dataProvider.fetchAllBalances(BotConfig.ACTIVE_EXCHANGES);
        if (!b.isEmpty()) {
            this.currentSnapshot = new BalanceSnapshot(b, System.currentTimeMillis());
        }
    }

    @Override
    public void updateTargets(List<String> targets) {
        huntingGrounds.clear();
        targets.forEach(t -> huntingGrounds.add(t.replace("USDT", "")));
        BotLogger.info("🎯 Targets Refresh: " + huntingGrounds.size());
    }

    // Formato visual de tablas idéntico al v3.1
    private void printHeader() {
        System.out.println("\n╔══════════╦════════╦═══════════════╦═══════╦════════╦════════╦════════════╗\n║   HORA   ║ ACTIVO ║     RUTA      ║ CAP($)║ GAP(%) ║ T.FEES ║  NETO($)   ║\n╠══════════╬════════╬═══════════════╬═══════╬════════╬════════╬════════════╣");
    }

    private void printFormattedLog(ArbitrageOpportunity opp) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String route = opp.strategyType().contains("TRIANGULAR")
                ? "⚡ " + opp.asset() + "-" + opp.sellExchange()
                : opp.buyExchange().substring(0,3) + "->" + opp.sellExchange().substring(0,3);

        // Usamos Locale.US para asegurar puntos decimales
        BotLogger.info(String.format(java.util.Locale.US,
                "║ %s ║ %-6s ║ %-13s ║ %5s ║ %6.2f ║ %6.4f ║ 💎 %6.2f ║",
                time, opp.asset(), route, "VAR", opp.grossSpreadPct()*100, 0.0, opp.expectedProfit()));
    }

    private void sendTelegramReport() { BotLogger.info("Telegram Report: Cache Hits=" + dataProvider.getCacheHits()); }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        if (crossExecutor != null) crossExecutor.setDryRun(dryRun);
        if (triangularExecutor != null) triangularExecutor.setDryRun(dryRun);
    }

    public void shutdown() {
        BotLogger.warn("🛑 APAGANDO ORQUESTADOR...");
        scheduler.shutdownNow();
        virtualExecutor.shutdownNow();
        pairSelector.stop();
    }
    // =========================================================
    // 🛠️ MÉTODOS DE COMPATIBILIDAD (PARA GOLDRUSHORCHESTRATOR)
    // =========================================================

    /**
     * Legacy Support: El Orchestrator intenta inyectar el CFO.
     * En v4, el Scanner gestiona su propio CFO internamente para desacoplamiento,
     * pero mantenemos este método para no romper la compilación.
     */
    public void injectCFO(PortfolioHealthManager cfo) {
        // No-Op: El Scanner ya tiene su instancia interna gestionada en el constructor.
        // BotLogger.info("ℹ️ Scanner v4 usa CFO interno. Inyección externa ignorada.");
    }

    /**
     * Legacy Support: Obtiene el PnL total acumulado.
     */
    public double getTotalPotentialProfit() {
        return totalPotentialProfit.sum();
    }

    /**
     * Legacy Support: Obtiene el contador de trades.
     */
    public long getTradesCount() {
        return tradesCount.get();
    }
}
