package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.GlobalBalanceReporter;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic.MarketCortex;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic.ProbabilisticOracle;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.estimator.StandardProfitEstimator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ProfitEstimator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.provider.CachingMarketDataProvider;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.AdaptiveSpatialStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.SpatialArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.TriangularArbitrageStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.ArbitrageTrace;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.DashboardService;
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
    private final CachingMarketDataProvider dataProvider;
    private final ProfitEstimator profitEstimator;
    private final com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager feeManager;
    private final PortfolioHealthManager cfo;
    // Inteligencia
    private final DynamicPairSelector pairSelector;
    private final GlobalBalanceReporter balanceReporter;
    // Probabilísticos
    private final MarketCortex cortex;
    private final ProbabilisticOracle oracle;

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
    // [NEON] 💾 VARIABLES PARA EL DASHBOARD CYBERPUNK
    private final DashboardService dashboard = new DashboardService();
    private final java.util.Map<String, Long> networkLatencies = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicLong cyclesCount = new AtomicLong(0);
    private final java.time.Instant bootTime = java.time.Instant.now();
    // Hilos
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public DeepMarketScanner(ExchangeConnector connector, ExecutionCoordinator coordinator) {
        this.connector = connector;
        this.coordinator = coordinator;

        // 1. Provider (Con caché métricas y anti-stampede)
        this.dataProvider = new CachingMarketDataProvider(connector);

        // 2. Servicios Auxiliares
        this.feeManager = new com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager(connector);
        this.cfo = new PortfolioHealthManager(connector); // <--- CORREGIDO: Asignamos al campo 'this.cfo'
        this.balanceReporter = new GlobalBalanceReporter(connector);

        // 3. Cerebro (MarketListener: this)
        // Nota: Pasamos 'this.cfo'
        this.pairSelector = new DynamicPairSelector(connector, this, this.feeManager, this.cfo);

        // 4. Estimador Financiero
        this.profitEstimator = new StandardProfitEstimator(this.feeManager, BotConfig.TEST_CAPITALS);

        // 5. Estrategias
        // --- INICIALIZACIÓN ORACLE ---
        this.cortex = new MarketCortex();
        this.oracle = new ProbabilisticOracle(this.cortex);

        if (BotConfig.isSpatialStrategy()) {
            strategies.add(new AdaptiveSpatialStrategy(this.cfo));
        }

            // 6. Ejecutores
        RiskManager riskPolice = new RiskManager(BotConfig.SEED_CAPITAL, coordinator);
        this.crossExecutor = new CrossTradeExecutor(connector, riskPolice, coordinator);
        this.triangularExecutor = new TriangularExecutor(connector);
        setDryRun(this.dryRun);
    }


    public void startOmniScan(int durationMinutes) {
        BotLogger.info("⚡ AGENTE TOKIO v4.3: INTEGRIDAD VERIFICADA");
       // printHeader(); // Restauramos el header visual v3.1

        pairSelector.start();
        balanceReporter.printReport();

        scheduler.scheduleAtFixedRate(this::refreshBalances, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::sendTelegramReport, BotConfig.REPORT_INTERVAL_MIN, BotConfig.REPORT_INTERVAL_MIN, TimeUnit.MINUTES);
        // [NEON] 1. PROBADOR DE RED (Ping Real cada 5s)
        scheduler.scheduleWithFixedDelay(this::pingNetwork, 2, 5, TimeUnit.SECONDS);

        // [NEON] 2. INTERFAZ CYBERPUNK (Refresco visual cada 3s)
        scheduler.scheduleAtFixedRate(this::printCyberpunkDashboard, 3, 3, TimeUnit.SECONDS);
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
        Map<String, Map<String, Double>> prices = dataProvider.fetchGlobalPrices(BotConfig.getActiveExchanges());
        if (prices.isEmpty()) return;
        Map<String, Double> refPrices =
                prices.getOrDefault(BotConfig.
                        getAdvisorRefExchange(), prices.values().iterator().next());
        // 🔥 FASE 1.1: INGESTA PARALELA (Sidecar)
        // Alimentamos el Cortex sin bloquear el flujo principal
        virtualExecutor.submit(() -> cortex.ingest(prices));
        // FASE 1.5: Prefetch (Con TrafficController interno en Provider)
        dataProvider.prefetchOrderBooks(huntingGrounds, BotConfig.getActiveExchanges()).join();

        // FASE 2: Pipeline de Estrategias
        try (var scope = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = huntingGrounds.stream().map(asset -> (Callable<Void>) () -> {
                processAssetWithOracle(asset, prices);
                return null;
            }).toList();
            scope.invokeAll(tasks);
        } catch (InterruptedException ignored) {}

        dataProvider.invalidateCache();
        MetricsService.get().recordOp();
        cyclesCount.incrementAndGet();
        // [NEON] ACTUALIZAR DASHBOARD HTML
        dashboard.updateNetwork(networkLatencies);
        dashboard.updateStats(cyclesCount.get(), totalPotentialProfit.sum());

        // Pasamos datos del Oráculo (si los tenemos a mano, o 0.0)
        // Para simplificar, puede pasar 0.0 por ahora o capturar el último 'verdict'

        dashboard.updateInventory(currentSnapshot.balances(),refPrices);
        dashboard.updateStats(cyclesCount.get(), totalPotentialProfit.sum());
        dashboard.generate();
    }

    private void processAsset(String asset, Map<String, Map<String, Double>> prices) {
        for (ArbitrageStrategy strategy : strategies) {
            // ETAPA 1: El Radar (Ya tiene su log interno en la estrategia, aquí recibimos los candidatos)
            List<ArbitrageOpportunity> opps = strategy.findOpportunities(asset, prices);

            for (ArbitrageOpportunity opp : opps) {
                String route = opp.buyExchange() + "->" + opp.sellExchange();

                // ETAPA 2 y 3: La Aduana y el Contador (Validación Financiera)
                ArbitrageOpportunity verified = profitEstimator.estimateProfitability(opp, currentSnapshot, dataProvider);

                if (verified != null) {
                    // 🚩 BANDERA VERDE: PASA A EJECUCIÓN
                    // Registramos que sobrevivió a los Fees y al Inventario
                    com.rafaeldiaz.orquestador_gold_rush_2025.utils.DecisionAuditor.log(
                            opp.strategyType(),
                            asset,
                            route,
                            opp.grossSpreadPct(),
                            verified.expectedProfit(),
                            "FINANCIERO",
                            "APROBADO",
                            "Profit Neto: $" + String.format("%.4f", verified.expectedProfit())
                    );

                    executeOpportunity(verified);

                } else {
                    // 🏳️ BANDERA BLANCA: MUERTE SILENCIOSA
                    // Aquí es donde mueren tus trades hoy. El auditor nos dirá si fue por fees o saldo.
                    com.rafaeldiaz.orquestador_gold_rush_2025.utils.DecisionAuditor.log(
                            opp.strategyType(),
                            asset,
                            route,
                            opp.grossSpreadPct(),
                            -1.0, // PnL no calculado o negativo
                            "FINANCIERO",
                            "RECHAZADO",
                            "Fees excesivos, PnL negativo o Sin Saldo en " + opp.sellExchange()
                    );
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
        Map<String, Map<String, Double>> b = dataProvider.fetchAllBalances(BotConfig.getActiveExchanges());
        if (!b.isEmpty()) {
            this.currentSnapshot = new BalanceSnapshot(b, System.currentTimeMillis());
        }
    }

    @Override
    public void updateTargets(List<String> targets) {
        huntingGrounds.clear();
        targets.forEach(t -> huntingGrounds.add(t.replace("USDT", "")));

        // ANTES: Imprimía en consola
        // AHORA: Silencio absoluto en consola.
        // Si hay cambios drásticos, el DynamicPairSelector se encargará de notificar al Telegram.

        // Solo actualizamos el log interno del Dashboard HTML
        dashboard.addLog("🎯 RADAR ACTUALIZADO: " + huntingGrounds.size() + " Activos");
    }

    private void printFormattedLog(ArbitrageOpportunity opp) {
        // Formato Estandarizado [INFO] - Fácil de leer para humanos y parsers
        // Ejemplo: ORDER_FILLED | WIF/USDT | Spread: 1.2% | PnL: $0.45 | Src: ORACLE

        BotLogger.info(String.format(java.util.Locale.US,
                "⚡ MATCH_FOUND | %s | %s -> %s | Spread: %.2f%% | Est.PnL: $%.4f | Src: %s",
                opp.asset(),
                opp.buyExchange(),
                opp.sellExchange(),
                opp.grossSpreadPct() * 100,
                opp.expectedProfit(),
                opp.signalSource()
        ));
    }

    private void sendTelegramReport() {
        // 1. Recopilar métricas
        long hits = dataProvider.getCacheHits();
        long trades = tradesCount.get();
        double pnl = totalPotentialProfit.sum();
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        // 2. Construir mensaje bonito
        String report = """
                📊 **REPORTE DE ESTADO** (%s)
                
                💰 **PnL Estimado:** $%.2f
                🔫 **Trades Detectados:** %d
                ⚡ **Cache Hits:** %d
                
                🛡️ *Sistema Operativo y Vigilando...*
                """.formatted(time, pnl, trades, hits);

        // 3. Loguear en consola Y ENVIAR a Telegram
        BotLogger.info("📨 Enviando reporte periódico a Telegram...");
        BotLogger.sendTelegram(report);
    }
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
    // ========================================================



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

    // Método para incluir Lógica Oracle
    private void processAssetWithOracle(String asset, Map<String, Map<String, Double>> prices) {
        for (ArbitrageStrategy strategy : strategies) {
            // 1. Detección Base (Cruda)
            // Nota: La estrategia usa MIN_SCAN_SPREAD por defecto internamente.
            // Si la estrategia encuentra algo > 0.3%, lo retorna.
            // ¿Qué pasa con los de 0.1%? Necesitamos que la estrategia acepte un umbral dinámico
            // O, más simple para el diseño ADITIVO:
            // Dejamos que la estrategia encuentre TODO (bajando su umbral interno) y filtramos aquí.

            // Para v4.5: Asumimos que SpatialStrategy busca >= MIN_SCAN_SPREAD.
            // Si queremos que encuentre oportunidades menores (0.1%), SpatialStrategy debe
            // configurarse con un umbral base bajo (ej. 0.0005) y filtramos AQUI.

            List<ArbitrageOpportunity> opps = strategy.findOpportunities(asset, prices);

            for (ArbitrageOpportunity opp : opps) {
                // 2. CONSULTA AL ORÁCULO
                String targetEx = opp.buyExchange().equals(BotConfig.ADVISOR_REF_EXCHANGE)
                        ? opp.sellExchange()
                        : opp.buyExchange();
                if (opp.grossSpreadPct() < BotConfig.getMinScanSpread()) {
                    dashboard.registrarTraza(new ArbitrageTrace(asset, ArbitrageTrace.AuditStage.SCAN_IGNORED, "Spread bajo", opp.grossSpreadPct()));
                    continue;
                }


                // 3. FILTRADO DINÁMICO
                // Si el spread es menor al sugerido por el oráculo, descartamos.
                var verdict = oracle.getVerdict(asset, opp.grossSpreadPct(), targetEx);

                // 4. ENRIQUECIMIENTO DEL MODELO
                // Creamos una nueva instancia del record con los datos del oráculo
                ArbitrageOpportunity enrichedOpp = new ArbitrageOpportunity(
                        opp.strategyType(), opp.asset(), opp.buyExchange(), opp.sellExchange(),
                        opp.priceEntry(), opp.priceExit(), opp.grossSpreadPct(),
                        opp.quantity(), opp.expectedProfit(), opp.detectedAtTimestamp(),
                        verdict.confidenceScore(), verdict.signalSource()
                );

                // 5. Validación Financiera
                ArbitrageOpportunity verified = profitEstimator.estimateProfitability(enrichedOpp, currentSnapshot, dataProvider);

                if (verified != null) {
                    executeOpportunity(verified);
                }
            }
        }
    }
    // =================================================================================
    // 🌆 TOKYO NEON DASHBOARD (VISUALIZACIÓN TÁCTICA)
    // =================================================================================

    private void pingNetwork() {
        // Usa hilos virtuales para pings paralelos reales
        BotConfig.getActiveExchanges().forEach(ex -> virtualExecutor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                // Golpeamos endpoint público ligero para medir la fibra
                connector.fetchPrice(ex, "BTCUSDT");
                long latency = System.currentTimeMillis() - start;
                networkLatencies.put(ex, latency);
            } catch (Exception e) {
                networkLatencies.put(ex, 9999L); // Código de error (Offline)
            }
        }));
    }

    private void printCyberpunkDashboard() {
        // 1. SILENCIO: Actualizamos el HTML en segundo plano sin imprimir nada
        dashboard.updateNetwork(networkLatencies);
        dashboard.updateStats(cyclesCount.get(), totalPotentialProfit.sum());
        dashboard.generate(); // Escribe dashboard.html
    }
    // EN DeepMarketScanner.java (Implementando MarketListener)

    @Override
    public void reportRadarDetection(String symbol, double score, double spreadPct, double volatility) {
        // 1. Determinar Estado Visual
        String status;
        if (score > 0.85) status = "🔥 HOT";
        else if (score > 0.6) status = "🚀 HIGH";
        else status = "👀 RADAR";

        // 2. Enviar al Dashboard
        // Nota: spreadPct viene como 2.5 (2.5%), el Dashboard espera 0.025 para formatearlo a %
        dashboard.updateRadar(symbol, score, spreadPct / 100.0, status);
    }
}
