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
import com.rafaeldiaz.orquestador_gold_rush_2025.model.LatencyBreakdown;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.DecisionAuditor;

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

    // Estado y Métricas
    private volatile BalanceSnapshot currentSnapshot = new BalanceSnapshot(Collections.emptyMap(), 0L);
    private boolean dryRun = BotConfig.DRY_RUN;
    private final AtomicLong tradesCount = new AtomicLong(0);
    private final DoubleAdder totalPotentialProfit = new DoubleAdder();

    // [NEON] 💾 DASHBOARD & METRICS
    private final DashboardService dashboard = new DashboardService();
    private final MetricsService metricsService; // Singleton access o inyección
    private final java.util.Map<String, Long> networkLatencies = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicLong cyclesCount = new AtomicLong(0);

    // Hilos
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public DeepMarketScanner(ExchangeConnector connector, ExecutionCoordinator coordinator) {
        this.connector = connector;
        this.coordinator = coordinator;
        // 0. Enlazar Métricas (Singleton por defecto si no se pasa en constructor)
        this.metricsService = new MetricsService();

        // 1. Provider (Con caché métricas y anti-stampede)
        this.dataProvider = new CachingMarketDataProvider(connector);

        // 2. Servicios Auxiliares
        this.feeManager = new com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager(connector);
        this.cfo = new PortfolioHealthManager(connector);
        this.balanceReporter = new GlobalBalanceReporter(connector);

        // 3. Cerebro (MarketListener: this)
        this.pairSelector = new DynamicPairSelector(connector, this, this.feeManager, this.cfo);

        // 4. Estimador Financiero
        this.profitEstimator = new StandardProfitEstimator(this.feeManager, this.cfo, BotConfig.TEST_CAPITALS);

        // 5 ORACLE
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
        //  7. Métricas del sistema
    }


    public void startOmniScan(int durationMinutes) {
        BotLogger.info("⚡ AGENTE TOKIO v4.5: DASHBOARD ACTIVADO");
       // printHeader(); // Restauramos el header visual v3.1

        pairSelector.start();
        balanceReporter.printReport();

        scheduler.scheduleAtFixedRate(this::refreshBalances, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::sendTelegramReport, BotConfig.REPORT_INTERVAL_MIN, BotConfig.REPORT_INTERVAL_MIN, TimeUnit.MINUTES);
        // [NEON] 1. PROBADOR DE RED (Ping Real cada 5s)
        scheduler.scheduleWithFixedDelay(this::pingNetwork, 2, 5, TimeUnit.SECONDS);

        // [NEON] 2. Generación Visual () INTERFAZ CYBERPUNK (Hilo Lento, Refresco visual cada 3s)
        scheduler.scheduleAtFixedRate(this::printCyberpunkDashboard, 3, 3, TimeUnit.SECONDS);

        Thread.ofVirtual().start(() -> {
            long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
            while (System.currentTimeMillis() < endTime) {
                long cycleStart = System.currentTimeMillis();
                try {
                    scanCycle(); // Ahora esto es rápido y el dashboard no bloquea
                    // ⏱️ CONTROL DE RITMO DE PRECISIÓN
                    long executionTime = System.currentTimeMillis() - cycleStart;
                    long sleepTime = BotConfig.SCAN_INTERVAL_MS - executionTime;

                    // Solo dormimos si el ciclo fue más rápido que el intervalo definido
                    // Si el ciclo tardó 60ms y el intervalo es 50ms, NO dormimos (Catch up)
                    if (sleepTime > 0) { Thread.sleep(sleepTime);}
                } catch (InterruptedException e) { break; }
            }
            shutdown();
        });
    }

    private void scanCycle() {
        // =============================================================
        // 1. FASE DE PERCEPCIÓN (LEER EL MERCADO)
        // =============================================================

        // A. Descarga masiva de precios (Fast)
        Map<String, Map<String, Double>> prices = dataProvider.fetchGlobalPrices(BotConfig.getActiveExchanges());
        if (prices.isEmpty()) return;

        // B. Obtener precio de referencia (Para calcular valor en USDT)
        Map<String, Double> refPrices =
                prices.getOrDefault(BotConfig.getAdvisorRefExchange(), prices.values().iterator().next());

        // C. Ingesta Paralela al Cerebro (No bloquea)
        virtualExecutor.submit(() -> cortex.ingest(prices));

        // D. Prefetch Inteligente de Libros de Órdenes (Solo lo que nos interesa)
        dataProvider.prefetchOrderBooks(huntingGrounds, BotConfig.getActiveExchanges()).join();


        // =============================================================
        // 2. FASE DE RAZONAMIENTO (BUSCAR OPORTUNIDADES)
        // =============================================================
        try (var scope = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = huntingGrounds.stream().map(asset -> (Callable<Void>) () -> {
                // Aquí dentro ocurre la magia: Estrategia -> Oráculo -> Ejecución
                processAssetWithOracle(asset, prices);
                return null;
            }).toList();
            scope.invokeAll(tasks);
        } catch (InterruptedException ignored) {}


        // =============================================================
        // 3. FASE DE MANTENIMIENTO Y TELEMETRÍA (NO BLOQUEANTE)
        // =============================================================

        // A. Limpiar caché vieja para no operar con datos rancios
        dataProvider.invalidateCache();

        // B. Registrar "Latido" del sistema (ESTO ES recordOp)
        // Solo incrementa un contador (+1 ciclo). No toca dinero.
        metricsService.recordOp();
        long currentCycles = cyclesCount.incrementAndGet();

        // C. Actualizar datos en MEMORIA para el Dashboard (Rápido)
        // Pasamos el PnL acumulado y los ciclos
        dashboard.updateStats(currentCycles, totalPotentialProfit.sum());

        // D. Actualizar la "Foto del Dinero" en el Dashboard
        // OJO: 'currentSnapshot' ya se actualizó en otro hilo (refreshBalances).
        // Aquí solo se lo pasamos al dashboard para que lo pinte cuando pueda.
        if (refPrices != null) {
            dashboard.updateInventory(currentSnapshot.balances(), refPrices);
        }

        // 🛑 IMPORTANTE: NO llamamos a dashboard.generate() aquí.
        // Eso lo hace el hilo lento (scheduler) cada 1 segundo.
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
                    DecisionAuditor.log(
                            opp.strategyType(),
                            asset,
                            verified.buyExchange() + "->" + verified.sellExchange(),
                            opp.grossSpreadPct(),
                            verified.expectedProfit(),
                            "FINANCIERO",
                            "APROBADO",
                            "Pase a Ejecución"
                    );
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
            dashboard.registrarTrazaDecision(
                    opp.asset(),
                    ArbitrageTrace.AuditStage.EXIT_FILLED,
                    "WIN (SIM)",
                    opp.expectedProfit()
            );            return;
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
        // 🎨 Estilo Visual:
        // CYAN   -> Evento (MATCH_FOUND)
        // YELLOW -> Activo (BTC/USDT)
        // GREEN  -> Dinero y Porcentajes (Spread/PnL)

        String formattedMsg = String.format(java.util.Locale.US,
                BotLogger.CYAN + "⚡ MATCH_FOUND" + BotLogger.RESET + " | " +
                        BotLogger.YELLOW + "%-8s" + BotLogger.RESET + " | %s -> %s | " +
                        "Spread: " + BotLogger.GREEN + "%.2f%%" + BotLogger.RESET + " | " +
                        "Est.PnL: " + BotLogger.GREEN + "$%.4f" + BotLogger.RESET + " | Src: %s",
                opp.asset(),
                opp.buyExchange(),
                opp.sellExchange(),
                opp.grossSpreadPct() * 100,
                opp.expectedProfit(),
                opp.signalSource()
        );

        // 1. Log a consola y archivo general
        BotLogger.info(formattedMsg);

        // 2. (Opcional pero recomendado) Registrar en el CSV de Oportunidades
        // Esto llena el archivo opportunities.csv definido en BotLogger
        BotLogger.logOpportunity(
                "ARBITRAGE",               // Type/Strategy
                opp.asset(),               // Asset
                opp.buyExchange() + "->" + opp.sellExchange(), // Route
                opp.grossSpreadPct() * 100, // Gross Gap
                opp.expectedProfit(),       // Net Profit
                "DETECTED",                 // Status
                "Source: " + opp.signalSource() // Reason
        );
    }

    private void sendTelegramReport() {
        // 1. Recopilar métricas
        long hits = dataProvider.getCacheHits();
        long trades = tradesCount.get();
        double pnl = totalPotentialProfit.sum();
        // Usamos hora local del servidor
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        // 2. Construir mensaje bonito (Markdown seguro)
        // Nota: Telegram Markdown a veces da problemas con caracteres como '_',
        // pero para este template simple funcionará bien.
        String report = """
            📊 *REPORTE DE ESTADO* (%s)
            
            💰 *PnL Estimado:* $%.2f
            🔫 *Trades Detectados:* %d
            ⚡ *Cache Hits:* %d
            
            🛡️ _Sistema Operativo y Vigilando..._
            """.formatted(time, pnl, trades, hits);

        BotLogger.info("📨 Enviando reporte periódico...");
        // MÉTODO CON FORMATO
        BotLogger.sendMarkdown(report);

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
                /*
                if (opp.grossSpreadPct() < BotConfig.getMinScanSpread()) {
                    dashboard.registrarTraza(new ArbitrageTrace(asset, ArbitrageTrace.AuditStage.SCAN_IGNORED, "Spread bajo", opp.grossSpreadPct()));
                    continue;
                }
*/

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
                } else {
                    dashboard.registrarTrazaDecision(
                            asset,
                            ArbitrageTrace.AuditStage.ADVISOR_REJECTED,
                            "Fees > Spread",
                            opp.grossSpreadPct()
                    );
                }
            }
        }
    }
    // =========================================================
    // 🛠️ MÉTODOS DE COMPATIBILIDAD (PARA GOLDRUSHORCHESTRATOR)
    // ========================================================



    /**
     *      Obtiene el PnL total acumulado.
     */
    public double getTotalPotentialProfit() {
        return totalPotentialProfit.sum();
    }

    /**
     *      Obtiene el contador de trades.
     */
    public long getTradesCount() {
        return tradesCount.get();
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
        // 1. Recolectar datos frescos (SIN CALCULAR NADA, SOLO LEER)
        // Suponiendo que tienes acceso a metricsService aquí
        var recentLatencies = metricsService.getRecentLatencyHistory();

        // Obtener el último desglose (o el promedio de los últimos 10)
        LatencyBreakdown lastTrade = recentLatencies.isEmpty() ? null : recentLatencies.getLast();

        // 2. SILENCIO: Actualizamos el HTML en segundo plano
        dashboard.updateNetwork(networkLatencies);
        dashboard.updateStats(cyclesCount.get(), totalPotentialProfit.sum());
        dashboard.updateNetwork(networkLatencies);
        dashboard.updateStats(cyclesCount.get(), totalPotentialProfit.sum());
        if (lastTrade != null) {
            dashboard.updateTelemetry(
                    lastTrade.netInUs(),
                    lastTrade.logicUs(),
                    lastTrade.netOutUs()
            );
        }
        dashboard.generate(); // Escribe dashboard.html
    }

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
