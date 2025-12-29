package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.GlobalBalanceReporter;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.MetricsService;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.TradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.TriangularExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * 🛰️ DEEP MARKET SCANNER (EDICIÓN CIENTÍFICA: MULTI-CAPITAL STRESS TEST)
 * Realiza simulaciones paralelas con capitales escalonados para medir
 * la profundidad real del mercado y la escalabilidad de la estrategia.
 */
public class DeepMarketScanner implements MarketListener {

    private final ExchangeConnector connector;
    private final FeeManager feeManager;
    private final GlobalBalanceReporter balanceReporter;
    private ExecutionCoordinator coordinator;
    // El Ejecutor Espacial
    private final CrossTradeExecutor crossExecutor;
    private static final boolean AUTO_EXECUTE_ENABLED = false;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final TriangularExecutor triangularExecutor;
    // 🔧 CONFIGURACIÓN CIENTÍFICA
    // Simularemos todos estos escenarios simultáneamente con el mismo Order Book
    private final List<Double> testCapitals;

    private boolean dryRun = true;

    private final List<String> exchanges = BotConfig.ACTIVE_EXCHANGES;

    private final DynamicPairSelector pairSelector;


    private final List<String> huntingGrounds = new CopyOnWriteArrayList<>(BotConfig.HUNTING_GROUNDS_SEED);
    private com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager cfo; // ✅ NUEVO
    //  🌉 PIVOTES PARA TRIANGULAR
    private final List<String> BRIDGE_ASSETS = BotConfig.BRIDGE_ASSETS;
    // METRICS
    private final DoubleAdder totalPotentialProfit = new DoubleAdder();
    private final AtomicLong tradesCount = new AtomicLong(0);
    private final Map<String, AtomicLong> rejectionReasons = new ConcurrentHashMap<>();
    // 💾 CACHÉ DE SALDOS (Persistente entre ciclos)
    private Map<String, Map<String, Double>> cachedBalances = new ConcurrentHashMap<>();
    private long lastBalanceUpdate = 0;
    // 📚 CACHÉ DE ORDERBOOKS (2 segundos de vida)
    private final Cache<String, CachedOrderBook> orderBookCache = Caffeine.newBuilder()
            .maximumSize(1000) // Suficiente para 200 pares x 5 exchanges
            .expireAfterWrite(5000, TimeUnit.MILLISECONDS) // 5 segundos de vida (TTL)
            .recordStats() // Útil para el reporte de telemetría
            .build();    // Aumentamos a 5s para que los datos del Prefetch no caduquen antes de ser usados
    private record CachedOrderBook(ExchangeConnector.OrderBook book, long timestamp) {}
    // ✈️ CONTROL DE TRÁFICO AÉREO: Evita peticiones duplicadas en el mismo milisegundo
    private final Map<String, CompletableFuture<ExchangeConnector.OrderBook>>
            inflightBookRequests = new ConcurrentHashMap<>();
    // ⚡ OPTIMIZACIÓN HFT: Refresco de saldos cada 5 segundos.
    // Garantiza que el Scanner nunca trabaje con "dinero fantasma".
    private static final long BALANCE_TTL_MS = 5_000;

    // Bandera para forzar actualización inmediata (post-trade)
    private volatile boolean forceBalanceUpdate = true;
    private final DoubleAdder totalSlippageLoss = new DoubleAdder();
    // 🚀 FORMATOS THREAD-SAFE (Cero bloqueos entre hilos)
    private static final ThreadLocal<DecimalFormat>
            dfUsdt = ThreadLocal.withInitial(() -> new DecimalFormat("0.00")); //Money
    private static final ThreadLocal<DecimalFormat>
            dfPct = ThreadLocal.withInitial(() -> new DecimalFormat("0.0000")); //Fee

    // ⚡ VARIABLES ATÓMICAS (Estado líquido)
    private final java.util.concurrent.atomic.AtomicReference<Double>
            maxProfitSeenRef = new java.util.concurrent.atomic.AtomicReference<>(-999.0);
    private final java.util.concurrent.atomic.AtomicReference<String>
            bestOpportunityLogRef = new java.util.concurrent.atomic.AtomicReference<>("Buscando...");
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    public DeepMarketScanner(ExchangeConnector connector, ExecutionCoordinator coordinator) {        this.connector = connector;
        this.coordinator = coordinator; // Guardamos referencia

        this.feeManager = new FeeManager(connector);
        PortfolioHealthManager cfo = new PortfolioHealthManager(connector);
        this.pairSelector = new DynamicPairSelector(connector, this, feeManager, cfo);
        this.balanceReporter = new GlobalBalanceReporter(connector);

        // ============================================================
        // 🛡️ ARQUITECTURA DE EJECUCIÓN ESPACIAL
        // ============================================================
        RiskManager riskPolice = new RiskManager(BotConfig.SEED_CAPITAL);

        // ⚠️ CORRECCIÓN CLAVE: Pasamos 'coordinator', NO 'snapshotTimestamp'
        this.crossExecutor = new CrossTradeExecutor(connector, riskPolice, coordinator);

        this.crossExecutor.setDryRun(BotConfig.DRY_RUN);
        this.triangularExecutor = new TriangularExecutor(connector);
        this.triangularExecutor.setDryRun(BotConfig.DRY_RUN);
        // -----------------------------------------------------------
        // 🧪 CONFIGURACIÓN CIENTÍFICA (MODO STRESS TEST)
        // -----------------------------------------------------------

        // ANTES (Solo probaba la punta del iceberg):
        // this.testCapitals = List.of(BotConfig.SEED_CAPITAL);

        // AHORA (Sondeo de Profundidad Completa):
        // Usamos la lista definida en .env (10, 50, 100, 150)
        this.testCapitals = BotConfig.TEST_CAPITALS;

        // NOTA: Si BotConfig.TEST_CAPITALS te da error en rojo (porque no está parseada en la clase Config),
        // usa esta línea temporalmente para forzar la prueba hoy mismo:
        // this.testCapitals = List.of(10.0, 50.0, 100.0, 150.0, 300.0);


        // ✅ SHUTDOWN HOOK: Si alguien da Ctrl+C o mata el proceso, se ejecuta esto.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            this.shutdown();
        }));
    }


    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public long getTradesCount() { return tradesCount.get(); }
    public double getTotalPotentialProfit() { return totalPotentialProfit.sum(); }
    public String getBestOpportunityLog() {
        String log = bestOpportunityLogRef.get();
        return log.equals("N/A") ? "Buscando..." : log;
    }
    public void startOmniScan(int durationMinutes) {
        BotLogger.info("⚡ INICIANDO DEEP SCAN: STRESS TEST MULTI-CAPITAL");
        printConfigurationSnapshot();
        //BotLogger.info("🧪 Escenarios Activos: " + testCapitals);
        //BotLogger.info("🛡️ Modo Fuego Real: " + (!BotConfig.DRY_RUN ? "ACTIVADO 🔥" : "DESACTIVADO (Simulación)"));

        printHeader();
        if (cfo != null) {
            List<String> autoTargets = cfo.discoverTradableAssets();
            this.updateTargets(autoTargets);
        } else {
            BotLogger.warn("⚠️ CFO no inyectado. Usando lista fija.");
        }
        pairSelector.start();  // Selección dinámica de pares
        // ✅ [CAMBIO 1] REPORTE INICIAL (Solo una vez al principio)
        BotLogger.info("🏁 SALDOS INICIALES:");
        balanceReporter.printReport();
        // Reporte de Telegram en segundo plano
        scheduler.scheduleAtFixedRate(
                this::sendTelegramReport,
                BotConfig.REPORT_INTERVAL_MIN,
                BotConfig.REPORT_INTERVAL_MIN,
                TimeUnit.MINUTES
        );

        Thread.ofVirtual().start(() -> {
            long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);

            while (System.currentTimeMillis() < endTime) {

                // 3. Ejecutamos el escaneo normal
                scanFullMatrixBatchOptimized();


                try {
                    // 5. Inyectamos el delay
                    Thread.sleep(BotConfig.MAX_LATENCY_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
            finalizeScan();
        });
    }
    // ⚡ SCANNER V2.2 (CON TELEMETRÍA DE RENDIMIENTO SEPARADA)
    // ⚡ SCANNER V2.3 (CON PREFETCH ESPECULATIVO)
    private void scanFullMatrixBatchOptimized() {
        // 🛑 PREVENCIÓN DE CRASH AL APAGAR
        if (virtualExecutor.isShutdown() || virtualExecutor.isTerminated()) {
            return;
        }
        // ┌─────────────────────────────────────────────────────────────┐
        // │ 🛑 [TELEMETRY START]                                        │
        // └─────────────────────────────────────────────────────────────┘
        long startCycle = System.nanoTime();
        // ───────────────────────────────────────────────────────────────

        refreshBalancesResult();

        Map<String, Map<String, Double>> marketData = new ConcurrentHashMap<>();
        long snapshotTimestamp = System.currentTimeMillis();

        // ┌─────────────────────────────────────────────────────────────┐
        // │ 🛑 [TELEMETRY START] - INICIO I/O (Precios)                 │
        // └─────────────────────────────────────────────────────────────┘
        long startIO = System.nanoTime();
        // ───────────────────────────────────────────────────────────────

        // ---------------------------------------------------------
        // 🚀 FASE 1: DESCARGA DE PRECIOS SIMPLE (TICKERS)
        // ---------------------------------------------------------
        try (var ioExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> fetchTasks = exchanges.stream()
                    .map(ex -> (Callable<Void>) () -> {
                        try {
                            Map<String, Double> prices = connector.fetchAllPrices(ex);
                            if (prices != null && !prices.isEmpty()) marketData.put(ex, prices);
                        } catch (Exception e) { /* Silent or Log */ }
                        return null;
                    }).toList();

            try {
                ioExecutor.invokeAll(fetchTasks, 4500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }


            // ---------------------------------------------------------
            // 🚀 FASE 1.5: PREFETCH "CARPET BOMBING" (SILENCIOSO)
            // ---------------------------------------------------------
            if (!marketData.isEmpty()) {
                List<Callable<Void>> prefetchTasks = new ArrayList<>();

                for (String asset : huntingGrounds) {
                    String basePair = asset + "USDT";
                    for (String ex : exchanges) {
                        Map<String, Double> prices = marketData.get(ex);
                        if (prices == null) continue;

                        if (prices.containsKey(basePair)) {
                            // 👇 AQUÍ ESTÁ EL CAMBIO: 'true' al final para activar SILENCIO
                            prefetchTasks.add(() -> { fetchOrderBookCachedInternal(ex, basePair, 20, true); return null; });

                            for (String bridge : BRIDGE_ASSETS) {
                                String crossPair = asset + bridge;
                                String bridgePair = bridge + "USDT";

                                if (prices.containsKey(crossPair)) {
                                    // 👇 SILENCIO
                                    prefetchTasks.add(() -> { fetchOrderBookCachedInternal(ex, crossPair, 20, true); return null; });
                                }
                                if (prices.containsKey(bridgePair)) {
                                    // 👇 SILENCIO
                                    prefetchTasks.add(() -> { fetchOrderBookCachedInternal(ex, bridgePair, 20, true); return null; });
                                }
                            }
                        }
                    }
                }

                // 🔥 DISPARO MASIVO (Timeout 2.5s)
                if (!prefetchTasks.isEmpty()) {
                    try {
                        ioExecutor.invokeAll(prefetchTasks, 2500, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }        }

        // ┌─────────────────────────────────────────────────────────────┐
        // │ 🛑 [TELEMETRY START] - FIN I/O                              │
        // └─────────────────────────────────────────────────────────────┘
        long endIO = System.nanoTime();
        // ───────────────────────────────────────────────────────────────

        if (marketData.isEmpty()) {
            BotLogger.warn("⚠️ ALERTA: Blackout total.");
            return;
        }

        // ┌─────────────────────────────────────────────────────────────┐
        // │ 🛑 [TELEMETRY START] - INICIO CPU                           │
        // └─────────────────────────────────────────────────────────────┘
        long startCPU = System.nanoTime();
        // ───────────────────────────────────────────────────────────────

        // ---------------------------------------------------------
        // 🚀 FASE 2: ANÁLISIS PROFUNDO (Ahora sí es pura CPU)
        // ---------------------------------------------------------
        // Como ya hicimos prefetch, las llamadas a 'fetchOrderBookCached' dentro de
        // 'analyzeAssetInMemory' devolverán el dato de RAM instantáneamente.

        List<Callable<Void>> analysisTasks = new ArrayList<>();
        for (String asset : huntingGrounds) {
            analysisTasks.add(() -> {
                analyzeAssetInMemory(asset, marketData, cachedBalances, snapshotTimestamp);
                return null;
            });
        }

        try {
            virtualExecutor.invokeAll(analysisTasks);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // ┌─────────────────────────────────────────────────────────────┐
        // │ 🛑 [TELEMETRY START] - REPORTE FINAL                        │
        // └─────────────────────────────────────────────────────────────┘
        long endCPU = System.nanoTime();
        long endCycle = System.nanoTime();

        double ioMs = (endIO - startIO) / 1_000_000.0;
        double cpuMs = (endCPU - startCPU) / 1_000_000.0;
        double totalMs = (endCycle - startCycle) / 1_000_000.0;

        String perfLog = String.format(java.util.Locale.US,
                "⚡ PERF: Ciclo: %6.2f ms | 🌐 I/O (Inc. Prefetch): %6.2f ms | 🧠 CPU: %6.2f ms | 📉 Objetivos: %d",
                totalMs, ioMs, cpuMs, huntingGrounds.size());

        BotLogger.info(perfLog);
        // └─────────────────────────────────────────────────────────────┘
    }
    // 🌡️ DETECTOR DE CALOR: Filtro rápido para decidir si hacemos Prefetch
    private boolean isHotCandidate(String asset, Map<String, Map<String, Double>> marketData) {
        String pair = asset + "USDT";
        double minPrice = Double.MAX_VALUE;
        double maxPrice = -1.0;
        int count = 0;

        for (String ex : exchanges) {
            Map<String, Double> prices = marketData.get(ex);
            if (prices != null && prices.containsKey(pair)) {
                double p = prices.get(pair);
                if (p < minPrice) minPrice = p;
                if (p > maxPrice) maxPrice = p;
                count++;
            }
        }

        if (count < 2) return false; // Necesitamos al menos 2 para comparar

        // Cálculo rápido de Spread Bruto
        double spread = (maxPrice - minPrice) / minPrice;

        // UMBRAL DE PREFETCH:
        // Si el spread bruto es mayor al 0.2% (0.002), asumimos que vale la pena ver el libro.
        // Es más bajo que el umbral de tradeo para ser "optimistas".
        return spread > 0.002;
    }
    private void analyzeAssetInMemory(String asset, Map<String, Map<String, Double>> marketData,
                                      Map<String, Map<String, Double>> balanceSnapshot, long snapshotTimestamp) {

        // 1. Estrategia Espacial (Mantiene tu configuración actual)
        if (BotConfig.isSpatialStrategy()) {
            analyzeSpatialSpread(asset, marketData, balanceSnapshot, snapshotTimestamp);
        }

        // 2. Estrategia Triangular (ACTIVADA DIRECTAMENTE)
        // Iteramos sobre cada exchange para buscar oportunidades internas
        marketData.forEach((exchange, prices) -> {
            // Solo analizamos si el exchange tiene precio para el par base (ej: BTCUSDT)
            if (prices.containsKey(asset + "USDT")) {
                analyzeTriangularLoop(exchange, asset, prices);
            }
        });
        MetricsService.get().recordOp();
    }
    // 🌍 LÓGICA DE DETECCIÓN ESPACIAL (NUEVO MOTOR)
    private void analyzeSpatialSpread(String asset, Map<String, Map<String, Double>> marketData,
                                      Map<String, Map<String, Double>> balanceSnapshot, long snapshotTimestamp) {

        // ✅ 1. CORRECCIÓN: Definimos la variable 'pair' que faltaba
        String pair = asset + "USDT";

        String bestBuyEx = null;
        double minAsk = Double.MAX_VALUE;

        String bestSellEx = null;
        double maxBid = -1.0;

        // 1. Barrido: Buscar precio mínimo (Ask) y máximo (Bid)
        for (String ex : exchanges) {
            Map<String, Double> prices = marketData.get(ex);

            // Ahora 'pair' ya existe y no dará error
            if (prices == null || !prices.containsKey(pair)) continue;

            double price = prices.get(pair);

            // Simulamos Ask/Bid (Refinamiento posterior con OrderBook)
            double estimatedAsk = price;
            double estimatedBid = price;

            if (estimatedAsk < minAsk) {
                minAsk = estimatedAsk;
                bestBuyEx = ex;
            }
            if (estimatedBid > maxBid) {
                maxBid = estimatedBid;
                bestSellEx = ex;
            }
        }

        // 2. Validación Básica
        if (bestBuyEx != null && bestSellEx != null && !bestBuyEx.equals(bestSellEx)) {
            // Diferencia Bruta
            double spread = (maxBid - minAsk) / minAsk;

            // Filtro Rápido (.env)
            if (spread > BotConfig.MIN_SCAN_SPREAD) {
                // ✅ 2. CORRECCIÓN: Pasamos el 6to argumento (snapshotTimestamp)
                validateSpatialOpportunity(asset, bestBuyEx, bestSellEx, minAsk, balanceSnapshot, snapshotTimestamp);
            }
        }
    }
    // -------------------------------------------------------------------------
    // Obtiene OrderBook desde caché o descarga si es necesario.
    // -------------------------------------------------------------------------
    private void validateSpatialOpportunity(String asset, String buyEx, String sellEx,
                                            double basePrice,
                                            Map<String, Map<String, Double>> balanceSnapshot,
                                            long snapshotTimestamp) {
        try {
            String pair = asset + "USDT";

            // 1. 🚀 CACHÉ I/O (Tu optimización actual)
            ExchangeConnector.OrderBook bookBuy = fetchOrderBookCached(buyEx, pair, 20);
            ExchangeConnector.OrderBook bookSell = fetchOrderBookCached(sellEx, pair, 20);

            if (bookBuy == null || bookSell == null) return;

            // 2. ⚡ PRE-CÁLCULO VECTORIAL (Nueva optimización)
            // Calculamos lo que es común para TODOS los capitales una sola vez.

            // A. Latencia
            long rttA = connector.getRTT(buyEx);
            long rttB = connector.getRTT(sellEx);
            if (rttA > BotConfig.MAX_LATENCY_MS || rttB > BotConfig.MAX_LATENCY_MS) {
                // Registramos rechazo una vez y salimos, ahorrando 4 iteraciones
                rejectionReasons.computeIfAbsent("LATENCIA_ALTA", k -> new AtomicLong()).incrementAndGet();
                return;
            }

            // B. Fees
            double feeBuy = feeManager.getTradingFee(buyEx, pair, "TAKER");
            double feeSell = feeManager.getTradingFee(sellEx, pair, "TAKER");

            // 3. 🔥 BUCLE PURO (Solo Slippage y Profit)
            // Usamos un bucle for clásico que es nanosegundos más rápido que el stream overhead para listas pequeñas
            for (Double testCap : testCapitals) {
                // Sobrecargamos simulateSpatialScenario para aceptar fees y rtt pre-calculados
                simulateSpatialScenarioOptimized(asset, buyEx, sellEx, testCap,
                        bookBuy, bookSell, basePrice,
                        balanceSnapshot, snapshotTimestamp,
                        feeBuy, feeSell); // <--- Pasamos los datos ya masticados
            }

        } catch (Exception e) { /* Silent fail */ }
    }

    // 🧠 MOTOR DE SIMULACIÓN ESPACIAL (FINAL v5.5)
    private void simulateSpatialScenario(String asset, String buyEx, String sellEx, double cap,
                                         ExchangeConnector.OrderBook bookBuy, ExchangeConnector.OrderBook bookSell,
                                         double tickerPrice,
                                         Map<String, Map<String, Double>> balanceSnapshot,
                                         long snapshotTimestamp) {
        // =====================================================================
        // 1. 👮 CONSULTA AL CFO (Inteligencia de Enjambre)
        // =====================================================================
        double requiredProfit = BotConfig.NORMAL_MIN_PROFIT;
        boolean isEmergencyMove = false;

        if (cfo != null) {
            var directive = cfo.getAssetHealth(asset);
            boolean helpsRefillStock = directive.preferredBuyers().contains(buyEx);
            boolean helpsRefillCash = directive.preferredSellers().contains(sellEx);

            if (helpsRefillStock || helpsRefillCash) {
                requiredProfit = directive.minProfitPercent(); // Baja la vara (0.05%)
                isEmergencyMove = true;
            }
        }

        // =====================================================================
        // 2. ⛽ CHEQUEO DE COMBUSTIBLE EN RAM (Zero-Latency)
        // =====================================================================
        double realBalanceUsdt = 0.0;
        if (balanceSnapshot != null && balanceSnapshot.containsKey(buyEx)) {
            realBalanceUsdt = balanceSnapshot.get(buyEx).getOrDefault("USDT", 0.0);
        }

        // Filtro rápido de pobreza
        if (realBalanceUsdt < BotConfig.MIN_ASSET_VALUE_USDT) return;

        // Ajuste de capital (Stress Test vs Realidad)
        double effectiveCap = Math.min(cap, realBalanceUsdt);

        // =====================================================================
        // 3. 📉 SIMULACIÓN FÍSICA (Latencia, Slippage, Fees)
        // =====================================================================
        // A. Latencia
        long rttA = connector.getRTT(buyEx);
        long rttB = connector.getRTT(sellEx);
        if (rttA > BotConfig.MAX_LATENCY_MS || rttB > BotConfig.MAX_LATENCY_MS) {
            rejectionReasons.computeIfAbsent("LATENCIA_ALTA", k -> new AtomicLong()).incrementAndGet();
            return;
        }

        // B. Slippage Compra
        double qtyAsset = effectiveCap / tickerPrice;
        double realBuyPrice = connector.calculateWeightedPrice(bookBuy, "BUY", qtyAsset);
        if (realBuyPrice == 0 || (realBuyPrice/tickerPrice) > (1.0 + BotConfig.MAX_SLIPPAGE)) {
            rejectionReasons.computeIfAbsent("SLIPPAGE_BUY", k -> new AtomicLong()).incrementAndGet();
            return;
        }

        // C. Slippage Venta
        double realSellPrice = connector.calculateWeightedPrice(bookSell, "SELL", qtyAsset);
        if (realSellPrice == 0 || (realSellPrice/tickerPrice) < (1.0 - BotConfig.MAX_SLIPPAGE)) {
            rejectionReasons.computeIfAbsent("SLIPPAGE_SELL", k -> new AtomicLong()).incrementAndGet();
            return;
        }

        // D. Finanzas (Fees Reales)
        double feeBuy = feeManager.getTradingFee(buyEx, asset + "USDT", "TAKER");
        double feeSell = feeManager.getTradingFee(sellEx, asset + "USDT", "TAKER");

        double costBuy = effectiveCap * feeBuy;
        double revenue = (qtyAsset * realSellPrice) * (1 - feeSell);
        double netProfit = revenue - effectiveCap;
        double totalFees = costBuy + (qtyAsset * realSellPrice * feeSell);

        // =====================================================================
        // 4. ⚖️ VEREDICTO FINAL & EJECUCIÓN COORDINADA
        // =====================================================================

        if (netProfit > requiredProfit) {
            if (isEmergencyMove) {
                BotLogger.warn("🚑 OPORTUNIDAD DE REBALANCEO (" + buyEx + "->" + sellEx + ") | Profit: " + dfUsdt.get().format(netProfit));
            }
            updateBestOpportunity(buyEx + "->" + sellEx, asset, "SPATIAL", netProfit);
            printTriangularRow(buyEx + "->" + sellEx, asset, "DIRECT", effectiveCap, (netProfit + totalFees), totalFees, netProfit);
            // 🔥 FUEGO REAL CONTROLADO
            // Usamos compareAndSet para asegurar que SOLO UN hilo gane la carrera en este ciclo si hay concurrencia
            if (!BotConfig.DRY_RUN && tradesCount.get() == 0) {

                // 🚦 SEMÁFORO: Pedimos permiso al Coordinador
                if (coordinator != null && coordinator.tryAcquireDualLock(buyEx, sellEx)) {
                    try {
                        BotLogger.warn("🚀 EJECUTANDO SECUENCIA ESPACIAL [Cap: $" + effectiveCap + "]");

                        // Pasamos Snapshot Y Timestamp para validación final de 'stale data'
                        crossExecutor.executeCrossTrade(buyEx, sellEx, asset + "USDT",
                                qtyAsset, realBuyPrice, realSellPrice);

                        // Forzamos actualización inmediata de saldos
                        this.forceBalanceUpdate = true;

                        // Incrementamos contador para frenar otros trades en este ciclo de escaneo
                        tradesCount.incrementAndGet();

                        BotLogger.logOpportunity("SPATIAL", asset, buyEx + "->" + sellEx,
                                (realSellPrice - realBuyPrice)/realBuyPrice * 100,
                                netProfit, "EXECUTED", "PROFITABLE");

                    } catch (Exception e) {
                        BotLogger.error("❌ ERROR CRÍTICO EN EJECUCIÓN: " + e.getMessage());
                    } finally {
                        // SIEMPRE liberamos candados
                        coordinator.releaseLock(buyEx);
                        coordinator.releaseLock(sellEx);
                    }
                } else {
                    BotLogger.warn("🔒 BLOQUEO ACTIVO: Otro agente está operando en " + buyEx + " o " + sellEx);
                }
            } else {
                // Registro de oportunidad vista pero no ejecutada (DryRun o ya operamos)
                if (netProfit > -1.0) { // Filtro para no llenar log de basura
                    BotLogger.logOpportunity("SPATIAL", asset, buyEx + "->" + sellEx,
                            (realSellPrice - realBuyPrice)/realBuyPrice * 100,
                            netProfit, BotConfig.DRY_RUN ? "SIMULATED" : "SKIPPED_LIMIT", "PROFITABLE");
                }
            }

            // Métricas
            if (Double.compare(cap, testCapitals.get(0)) == 0) {
                totalPotentialProfit.add(netProfit);
            }
        }
    }
    // 📐 LÓGICA DE DETECCIÓN TRIANGULAR (CON TELEMETRÍA)
    private void analyzeTriangularLoop(String exchange, String asset, Map<String, Double> prices) {
        String pair1 = asset + "USDT"; // ✅ SE CREA AQUÍ
        Double price1 = prices.get(pair1);

        if (price1 == null) return;

        for (String bridge : BRIDGE_ASSETS) {
            if (bridge.equals(asset)) continue;

            String pair2 = asset + bridge; // ✅ SE CREA AQUÍ
            Double price2 = prices.get(pair2);

            String pair3 = bridge + "USDT"; // ✅ SE CREA AQUÍ
            Double price3 = prices.get(pair3);

            if (price2 != null && price3 != null) {
                double crossRate = (1.0 / price1) * price2 * price3;

                if (crossRate > (1.0 + BotConfig.MIN_SCAN_SPREAD)) {
                    // ✅ OPTIMIZACIÓN: Pasamos los Strings p1, p2, p3 hacia abajo
                    validateTriangularOpportunity(exchange, asset, bridge, price1, pair1, pair2, pair3);
                }
            }
        }
    }
    // [ACTUALIZADO] 🧪 VALIDACIÓN CIENTÍFICA (Ahora sí usa la Caché)
    private void validateTriangularOpportunity(String exchange, String asset, String bridge, double p1Ticker,
                                               String p1, String p2, String p3) {
        try {
            // ✅ CORRECCIÓN: Usamos fetchOrderBookCached en lugar de connector.fetchOrderBook
            // Esto recuperará instantáneamente lo que el Prefetch descargó en la fase anterior.
            ExchangeConnector.OrderBook book1 = fetchOrderBookCached(exchange, p1, 20);
            ExchangeConnector.OrderBook book2 = fetchOrderBookCached(exchange, p2, 20);
            ExchangeConnector.OrderBook book3 = fetchOrderBookCached(exchange, p3, 20);

            // Si alguno falló en el prefetch (null), abortamos rápido para no perder tiempo
            if (book1 == null || book2 == null || book3 == null) return;

            for (Double testCap : testCapitals) {
                simulateScenario(exchange, asset, bridge, testCap, book1, book2, book3, p1Ticker, p1, p2, p3);
            }

        } catch (Exception e) { }
    }

    // [NUEVO] 🧠 MOTOR DE SIMULACIÓN
    private void simulateScenario(String exchange, String asset, String bridge, double cap,
                                  ExchangeConnector.OrderBook b1, ExchangeConnector.OrderBook b2, ExchangeConnector.OrderBook b3, double p1Ticker,
                                  String p1, String p2, String p3) {
        // 1. 🛡️ FILTRO DE LATENCIA (RTT)
        long rtt = connector.getRTT(exchange);

        if (rtt > BotConfig.MAX_LATENCY_MS) {
            rejectionReasons.computeIfAbsent("LATENCIA_ALTA (>" + BotConfig.MAX_LATENCY_MS + "ms)", k -> new AtomicLong()).incrementAndGet();
            return;
        }

        // 2. 📉 CÁLCULO DE SLIPPAGE (Profundidad Real)
        double qtyAsset = cap / p1Ticker;
        double realP1 = connector.calculateWeightedPrice(b1, "BUY", qtyAsset);

        // Usa valor de slippage de la configuración en .env
        double slippageThreshold = 1.0 + BotConfig.MAX_SLIPPAGE;

        if (realP1 == 0 || (realP1 / p1Ticker) > slippageThreshold) {
            rejectionReasons.computeIfAbsent("SLIPPAGE_EXCESIVO (>" + (BotConfig.MAX_SLIPPAGE * 100) + "%)", k -> new AtomicLong()).incrementAndGet();
            return;
        }

        // 3. 🧮 CÁLCULOS FINANCIEROS
        double feeRate1 = feeManager.getTradingFee(exchange, asset + "USDT", "TAKER");
        double cost1 = cap * feeRate1;
        double assetGot = (cap / realP1) * (1 - feeRate1);

        double realP2 = connector.calculateWeightedPrice(b2, "SELL", assetGot);
        if (realP2 == 0) return; // Sin liquidez en paso 2

        double feeRate2 = feeManager.getTradingFee(exchange, asset + bridge, "TAKER");
        double cost2 = (assetGot * realP1) * feeRate2;
        double bridgeGot = (assetGot * realP2) * (1 - feeRate2);

        double realP3 = connector.calculateWeightedPrice(b3, "SELL", bridgeGot);
        if (realP3 == 0) return; // Sin liquidez en paso 3

        double feeRate3 = feeManager.getTradingFee(exchange, bridge + "USDT", "TAKER");
        double grossFinalUsdt = bridgeGot * realP3;
        double cost3 = grossFinalUsdt * feeRate3;

        double finalUsdt = grossFinalUsdt - cost3;
        double netProfit = finalUsdt - cap;
        double totalFees = cost1 + cost2 + cost3;
        double grossGap = netProfit + totalFees;

        // 4. 🕵️ DIAGNÓSTICO DE FEES
        // Si perdemos dinero, pero sin fees hubiéramos ganado, culpamos a los fees
        if (netProfit <= 0 && (netProfit + totalFees) > 0) {
            rejectionReasons.computeIfAbsent("FEES_MATAN_PROFIT", k -> new AtomicLong()).incrementAndGet();
        }

        // 5. ✅ ZONA DE ÉXITO Y EJECUCIÓN
        if (netProfit > BotConfig.MIN_PROFIT_USDT) {

            // A. Registrar la mejor oportunidad para Telegram
            updateBestOpportunity(exchange, asset, bridge, netProfit);

            // B. Imprimir en consola (Fila Visual)
            printTriangularRow(exchange, asset, bridge, cap, grossGap, totalFees, netProfit);

            // C. EJECUCIÓN DE FUEGO REAL (Solo si es el capital maestro y no es simulacro)
            // 🚦 SEMÁFORO
            if (coordinator != null && coordinator.tryAcquireLock(exchange)) {
                try {
                    // 🔥 DISPARO: Pasamos 'exchange' y los pares 'p1, p2, p3' directos
                    triangularExecutor.executeSequence(exchange, asset, bridge, p1, p2, p3, cap, p1Ticker);

                    this.forceBalanceUpdate = true;
                    tradesCount.incrementAndGet(); // Bloqueo temporal
                    // TODO: Comentar Log en producción
                    BotLogger.warn("🚀 EJECUTANDO TRIANGULAR en " + exchange);
                } catch (Exception e) {
                    BotLogger.error("❌ FAIL TRIANGULAR: " + e.getMessage());
                } finally {
                    coordinator.releaseLock(exchange);
                }
            } else {
                // TODO: Comentar en producción para reducir ruido
                BotLogger.warn("🔒 LOCK BUSY: " + exchange);
            }
            // D. Actualización de Métricas Globales (Solo sumamos UNA vez por ciclo, usando el capital base)
            // Esto evita que sumemos el profit de $10, $100 y $1000 al mismo tiempo en el total
            if (Double.compare(cap, testCapitals.get(0)) == 0) {
                totalPotentialProfit.add(netProfit);
                tradesCount.incrementAndGet();
            }
        }
    }

    // ==================================================================================
    // 🚀 VISUALIZACIÓN OPTIMIZADA (BARE METAL LOGGING)
    // Estrategia: String.format con Locale.US.
    // Ventaja: Stateless, Thread-Safe y maneja padding + decimales en una sola operación atomic.
    // ==================================================================================

    private void printTriangularRow(String exchange, String asset, String bridge, double cap, double gap, double fees, double net) {
        String time = LocalTime.now().format(timeFmt);
        String route = "⚡ " + asset + "-" + bridge;

        // Símbolo visual rápido para el operador
        String symbol = net > 0 ? "💎 $" : "🔻 $";

        // FORMATO:
        // %-3s  : Exchange (3 letras, aling izq)
        // %5.0f : Capital (Sin decimales, ancho 5)
        // %6.2f : Gap y Fees (2 decimales, ancho 6 para alinear columnas)
        // %.2f  : Neto (2 decimales)
        String logLine = String.format(java.util.Locale.US,
                "║ %s ║ %-3s ║ %-13s ║ %5.0f ║ %6.2f ║ %6.2f ║ %s%6.2f ║",
                time, exchange.substring(0,3).toUpperCase(), route, cap,
                gap, fees, symbol, net);

        BotLogger.info(logLine);
    }

    private void printRow(String asset, String buyEx, String sellEx, double gap, double tradeFee, double netFee, double net) {
        String time = LocalTime.now().format(timeFmt);
        String route = buyEx.substring(0,3).toUpperCase() + "->" + sellEx.substring(0,3).toUpperCase();

        String symbol = net > 0 ? "💎 " : "🔻 ";

        // FORMATO SPATIAL:
        // Usamos 4 decimales (%6.4f) para las tasas (%) para ver el detalle fino de los fees
        String logLine = String.format(java.util.Locale.US,
                "║ %s ║ %-6s ║ %-13s ║ %5s ║ %6.2f ║ %6.4f ║ %6.4f ║ %s%6.2f ║",
                time, asset, route, "224", gap, tradeFee, netFee, symbol, net);

        BotLogger.info(logLine);
    }
    private void printHeader() {
        // Encabezado expandido
        System.out.println("\n╔══════════╦════════╦═══════════════╦═══════╦════════╦════════╦════════╦════════════╗");
        System.out.println("║   HORA   ║ ACTIVO ║     RUTA      ║ CAP($)║ GAP($) ║ T.FEES ║ RED($) ║  NETO($)   ║");
        System.out.println("╠══════════╬════════╬═══════════════╬═══════╬════════╬════════╬════════╬════════════╣");
    }


    /**
     * 📸 SNAPSHOT DE CONFIGURACIÓN (CYBERPUNK EDITION)
     */
    /**
     * 📸 SNAPSHOT DE CONFIGURACIÓN (FULL SPECTRUM)
     * Documenta exhaustivamente todos los parámetros del .env para auditoría forense.
     */
    /**
     * 📸 SNAPSHOT DE CONFIGURACIÓN (FULL SPECTRUM - REALITY CHECK)
     */
    private void printConfigurationSnapshot() {
        DecimalFormat money = dfUsdt.get();
        DecimalFormat pct = dfPct.get();

        // Paleta Cyberpunk
        String C = BotLogger.CYAN;
        String G = BotLogger.GREEN;
        String W = BotLogger.WHITE_BOLD;
        String R = BotLogger.RESET;
        String Y = BotLogger.YELLOW;
        String M = BotLogger.PURPLE;

        BotLogger.info("\n" + C + "╔════════════════════════════════════════════════════════════╗" + R);
        BotLogger.info(C + "║ ⚙️  CONFIGURACIÓN DE MISIÓN: AGENTE TOKIO (V.1.1)           ║" + R);
        BotLogger.info(C + "╠════════════════════════════════════════════════════════════╣" + R);

        // --- 1. MODO Y ESTRATEGIA (CORREGIDO) ---
        String modeColor = BotConfig.DRY_RUN ? Y : BotLogger.RED;
        BotLogger.info(String.format(C + "║ 🛡️  MODO:             " + W + "%-35s " + C + "║" + R,
                modeColor + (BotConfig.DRY_RUN ? "SIMULACIÓN (DRY-RUN)" : "FUEGO REAL 🔥")));

        // 🧠 LÓGICA DE DISPLAY: Reconocemos las centrífugas
        String realStrategy = BotConfig.STRATEGY_TYPE;
        if (realStrategy.equalsIgnoreCase("SPATIAL")) {
            realStrategy = "HÍBRIDA (SPATIAL + TRIANGULAR)";
        }

        BotLogger.info(String.format(C + "║ 🧠  ESTRATEGIA:       " + W + "%-35s " + C + "║" + R, realStrategy));
        BotLogger.info(String.format(C + "║ 🔎  AUTO-DISCOVERY:   " + W + "%-35s " + C + "║" + R, BotConfig.AUTO_DISCOVERY ? "ACTIVADO" : "MANUAL (Fijo)"));

        // --- 2. CAPITAL Y CIENCIA ---
        BotLogger.info(C + "╠════════════════════════════════════════════════════════════╣" + R);
        BotLogger.info(String.format(C + "║ 💰  CAPITAL BASE:     " + G + "%-35s " + C + "║" + R, money.format(BotConfig.SEED_CAPITAL) + " USDT"));
        String capList = testCapitals != null ? testCapitals.toString() : "N/A";
        if (capList.length() > 30) capList = capList.substring(0, 27) + "...";
        BotLogger.info(String.format(C + "║ 🧪  STRESS TEST:      " + W + "%-35s " + C + "║" + R, capList));
        BotLogger.info(String.format(C + "║ ⚖️  TRADE SIZE:       " + W + "%-35s " + C + "║" + R, pct.format(BotConfig.TRADE_SIZE_PERCENT) + " del Saldo"));
        BotLogger.info(String.format(C + "║ 📚  BOOK DEPTH:       " + W + "%-35s " + C + "║" + R, BotConfig.BOOK_DEPTH + " niveles"));

        // --- 3. UMBRALES DE GANANCIA ---
        BotLogger.info(C + "╠════════════════════════════════════════════════════════════╣" + R);
        BotLogger.info(String.format(C + "║ 🎯  META PROFIT:      " + G + "%-35s " + C + "║" + R, pct.format(BotConfig.NORMAL_MIN_PROFIT)));
        BotLogger.info(String.format(C + "║ 🚑  CRISIS PROFIT:    " + Y + "%-35s " + C + "║" + R, pct.format(BotConfig.EMERGENCY_MIN_PROFIT)));
        BotLogger.info(String.format(C + "║ 💵  MIN NETO (ABS):   " + G + "%-35s " + C + "║" + R, "$" + BotConfig.MIN_PROFIT_USDT + " USDT"));
        BotLogger.info(String.format(C + "║ 🧹  MIN ASSET VAL:    " + W + "%-35s " + C + "║" + R, "$" + BotConfig.MIN_ASSET_VALUE_USDT + " USDT"));

        // --- 4. FÍSICA Y RED ---
        BotLogger.info(C + "╠════════════════════════════════════════════════════════════╣" + R);
        BotLogger.info(String.format(C + "║ 📡  MAX LATENCIA:     " + W + "%-35s " + C + "║" + R, BotConfig.MAX_LATENCY_MS + " ms"));
        BotLogger.info(String.format(C + "║ 📉  MAX SLIPPAGE:     " + W + "%-35s " + C + "║" + R, pct.format(BotConfig.MAX_SLIPPAGE)));
        BotLogger.info(String.format(C + "║ ⏱️  SCAN INTERVAL:    " + W + "%-35s " + C + "║" + R, BotConfig.MAX_LATENCY_MS + " ms"));
        BotLogger.info(String.format(C + "║ 🔒  LOCK TIMEOUT:     " + W + "%-35s " + C + "║" + R, BotConfig.EXECUTION_LOCK_TIMEOUT_MS + " ms"));
        BotLogger.info(String.format(C + "║ 😷  CUARENTENA CB:    " + Y + "%-35s " + C + "║" + R, (BotConfig.CB_QUARANTINE_DURATION_MS / 1000) + " seg"));

        // --- 5. CEREBRO Y MERCADO ---
        BotLogger.info(C + "╠════════════════════════════════════════════════════════════╣" + R);
        BotLogger.info(String.format(C + "║ 👨‍🏫  ADVISOR REF:      " + M + "%-35s " + C + "║" + R, BotConfig.ADVISOR_REF_EXCHANGE.toUpperCase()));
        BotLogger.info(String.format(C + "║ 🔍  MIN SPREAD (Adv): " + W + "%-35s " + C + "║" + R, pct.format(BotConfig.ADVISOR_MIN_SPREAD)));
        BotLogger.info(String.format(C + "║ 📈  TREND EMA:        " + W + "%-35s " + C + "║" + R, "EMA(" + BotConfig.TREND_EMA_PERIOD + ") " + BotConfig.TREND_TIMEFRAME));

        // --- 6. ARQUITECTURA ---
        BotLogger.info(C + "╠════════════════════════════════════════════════════════════╣" + R);
        String exList = String.join(",", BotConfig.ACTIVE_EXCHANGES);
        if (exList.length() > 30) exList = exList.substring(0, 27) + "...";
        BotLogger.info(String.format(C + "║ 🏦  EXCHANGES:        " + W + "%-35s " + C + "║" + R, exList));

        BotLogger.info(String.format(C + "║ 🌉  PUENTES (Tri):    " + W + "%-35s " + C + "║" + R, BotConfig.BRIDGE_ASSETS.size() + " Activos (" + String.join(",", BotConfig.BRIDGE_ASSETS) + ")"));

        // Cuentas
        String spatialAcc = String.join(",", BotConfig.SPATIAL_ACCOUNTS);
        if (spatialAcc.length() > 30) spatialAcc = spatialAcc.substring(0, 27) + "...";
        BotLogger.info(String.format(C + "║ 👥  CTAS SPATIAL:     " + W + "%-35s " + C + "║" + R, spatialAcc));
        BotLogger.info(String.format(C + "║ ⚖️  TOLERANCIA IMB:   " + Y + "%-35s " + C + "║" + R, pct.format(BotConfig.IMBALANCE_TOLERANCE)));

        BotLogger.info(C + "╚════════════════════════════════════════════════════════════╝" + R + "\n");
    }
    private void sendTelegramReport() {
        try {
            logCacheStats();
            StringBuilder sb = new StringBuilder();
            sb.append("🛰️ *DASHBOARD DE TELEMETRÍA*\n");
            sb.append("━━━━━━━━━━━━━━━━━━\n");


            // 📶 Salud de la Red (RTT)
            sb.append("📶 *Latencia (RTT):*\n");
            sb.append("· Bin: `").append(connector.getRTT("binance")).append("ms` | ");
            sb.append("Byb: `").append(connector.getRTT("bybit")).append("ms`\n");
            sb.append("· Mex: `").append(connector.getRTT("mexc")).append("ms` | ");
            sb.append("Kuc: `").append(connector.getRTT("kucoin")).append("ms`\n\n");

            // 🚫 Análisis de Rechazos
            sb.append("🚫 *Causas de No-Trade:*\n");
            rejectionReasons.forEach((reason, count) ->
                    sb.append("· ").append(reason).append(": `").append(count.get()).append("`\n")
            );

            // 💰 Rendimiento y Mejor Presa (Leídos de Atómicos)
            sb.append("\n📈 *Mejor Presa:* \n`").append(bestOpportunityLogRef.get()).append("`\n");
            sb.append("💵 *PnL Acumulado:* `$").append(String.format("%.4f", totalPotentialProfit.sum())).append("`\n");

            BotLogger.sendTelegram(sb.toString());
            // Limpiamos razones para el próximo reporte
            rejectionReasons.clear();
        } catch (Exception e) {
            BotLogger.error("Error Dashboard: " + e.getMessage());
        }
    }
    public void logCacheStats() {
        var stats = orderBookCache.stats();
        BotLogger.info(String.format("🧠 CACHE STATS: HitRate=%.2f%% | Evictions=%d | LoadCount=%d",
                stats.hitRate() * 100,
                stats.evictionCount(),
                stats.loadCount()));
    }
    private void updateBestOpportunity(String ex, String asset, String bridge, double profit) {
        maxProfitSeenRef.updateAndGet(currentMax -> {
            if (profit > currentMax) {
                bestOpportunityLogRef.set(String.format("[%s] %s-%s (Neto: $%s)", ex.toUpperCase(), asset, bridge, dfUsdt.get().format(profit)));
                return profit;
            }
            return currentMax;
        });
    }
    private void finalizeScan() {
        scheduler.shutdown();
        virtualExecutor.shutdown();
        System.out.println("╚══════════╩════════╩═══════════════╩═══════╩════════╩════════╩════════╩════════════╝");
    }

    // ✅ IMPLEMENTACIÓN DE MARKET LISTENER (Callback del Cerebro)
    @Override
    public void updateTargets(List<String> newTargets) {
        if (newTargets != null && !newTargets.isEmpty()) {
            // Limpiamos la semilla (SOL, etc.) para que no se mezcle basura con la élite
            // Opcional: Podrías querer mantener algunos fijos. Por ahora reemplazamos todo.
            List<String> cleanTargets = new ArrayList<>();
            for (String t : newTargets) {
                // El selector devuelve "SOLUSDT", nosotros necesitamos "SOL"
                cleanTargets.add(t.replace("USDT", ""));
            }

            huntingGrounds.clear();
            huntingGrounds.addAll(cleanTargets);
            BotLogger.info("🎯 OBJETIVOS ACTUALIZADOS POR CEREBRO (" + huntingGrounds.size() + "): " + huntingGrounds);
        }
    }

    public void injectCFO(com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager cfo) {
        this.cfo = cfo;
    }
    /**
     * 🔄 GESTIÓN DE SALDOS
     * REFRESH PARALELO CON RATE LIMITING INTELIGENTE
     */

    private void refreshBalancesResult() {
        long now = System.currentTimeMillis();
        // 1. TTL Check: Si la caché está fresca, ahorramos CPU y Red
        if (!forceBalanceUpdate && (now - lastBalanceUpdate) < BALANCE_TTL_MS) return;

        // ⚡ JAVA 25: PATRÓN "SCATTER-GATHER" CON TIMEOUT ESTRICTO
        // Usamos el Executor de Hilos Virtuales (Project Loom)
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // 2. PREPARAR TAREAS (Callable)
            // Convertimos cada llamada a la API en una tarea que retorna el mapa de saldos
            List<Callable<Map<String, Double>>> tasks = exchanges.stream()
                    .map(ex -> (Callable<Map<String, Double>>) () -> {
                        return connector.fetchBalances(ex); // Esto bloquea el hilo virtual, no el OS
                    })
                    .toList();

            try {
                // 3. LA MAGIA: INVOKE ALL CON DEADLINE
                // "Lanza todo, espera máximo 1500ms. Si alguien no termina, MÁTALO."
                // Esto reemplaza al antiguo joinUntil().
                List<Future<Map<String, Double>>> results = executor.invokeAll(tasks, 1500, TimeUnit.MILLISECONDS);

                // 4. PROCESAR RESULTADOS (Solo los que llegaron a tiempo)
// 4. PROCESAR RESULTADOS (Dentro del try de invokeAll)
                for (int i = 0; i < results.size(); i++) {
                    Future<Map<String, Double>> future = results.get(i);
                    String exchangeName = exchanges.get(i);

                    try {
                        // El compilador exige este try-catch aunque sepamos que está "done"
                        if (future.state() == Future.State.SUCCESS) {
                            Map<String, Double> balances = future.get(); // Ahora sí seguro
                            if (balances != null && !balances.isEmpty()) {
                                cachedBalances.put(exchangeName, balances);
                            }
                        } else if (future.state() == Future.State.CANCELLED) {
                            // Timeout silencioso
                        } else if (future.state() == Future.State.FAILED) {
                            // Logueamos la excepción interna sin detener el sistema
                            // BotLogger.warn("Fallo balance " + exchangeName + ": " + future.exceptionNow().getMessage());
                        }
                    } catch (ExecutionException e) {
                        // Esto atrapa si la tarea lanzó una excepción no controlada
                        BotLogger.error("Error ejecución en " + exchangeName + ": " + e.getCause().getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Buena práctica
                    }
                }
            } catch (InterruptedException e) {
                // Manejo si el hilo principal es interrumpido
                Thread.currentThread().interrupt();
            }

        } // El try-with-resources cierra el executor y limpia hilos zombies automáticamente

        lastBalanceUpdate = System.currentTimeMillis();
        forceBalanceUpdate = false;
    }
    public void injectCoordinator(ExecutionCoordinator coordinator) {
        this.coordinator = coordinator;
    }
    /**
     * 🛑 PROTOCOLO DE APAGADO
     * Mata todos los hilos y cierra conexiones limpiamente.
     */
    /**
     * Obtiene OrderBook desde caché o descarga si es necesario.
     * @param exchange Exchange objetivo
     * @param pair Par de trading (ej: SOLUSDT)
     * @param depth Profundidad del libro (20 niveles recomendado)
     * @return OrderBook fresco o cacheado
     */
// ✅ Versión Standard (Usada por el Análisis): Si descarga aquí, ES LAG -> Grita.
    private ExchangeConnector.OrderBook fetchOrderBookCached(String exchange, String pair, int depth) {
        return fetchOrderBookCachedInternal(exchange, pair, depth, false); // false = NO SILENT (Gritar)
    }

    // ✅ Versión Interna (Lógica Real)
// ✅ Versión Interna OPTIMIZADA (Request Coalescing)
    private ExchangeConnector.OrderBook fetchOrderBookCachedInternal(String exchange, String pair, int depth, boolean silent) {
        String key = exchange + "_" + pair;

        // 1. 🚀 FAST PATH: Consultamos Caffeine
        // getIfPresent devuelve null si no existe O si ya expiró (adiós lógica manual de timestamps)
        CachedOrderBook cached = orderBookCache.getIfPresent(key);

        if (cached != null) {
            // Caffeine ya validó el TTL internamente, así que es seguro retornar
            return cached.book();
        }

        // 2. 🐢 SLOW PATH: "Request Coalescing" (Tu lógica de vuelo original se mantiene intacta)
        // Esto es crucial: Caffeine tiene un método .get(key, loader) que hace esto mismo (bloqueo atómico),
        // PERO tu implementación con CompletableFuture + VirtualExecutor es superior para este caso de uso
        // porque permite timeouts asíncronos y control de excepciones específico sin bloquear hilos de plataforma.
        CompletableFuture<ExchangeConnector.OrderBook> future = inflightBookRequests.computeIfAbsent(key, k -> {

            if (!silent) {
                BotLogger.warn("🐢 LAG REAL (Single-Flight): Descargando -> " + exchange + " | " + pair);
            }

            return CompletableFuture.supplyAsync(() -> {
                try {
                    return connector.fetchOrderBook(exchange, pair, depth);
                } catch (Exception e) {
                    BotLogger.error("❌ Error fetching book " + key + ": " + e.getMessage());
                    return null;
                }
            }, virtualExecutor);
        });

        try {
            // 3. ⏳ ESPERA COLABORATIVA
            ExchangeConnector.OrderBook freshBook = future.join();

            // 4. ACTUALIZACIÓN EN CAFFEINE
            if (freshBook != null) {
                // Simplemente ponemos en la caché. Caffeine iniciará el conteo de 5s desde AHORA.
                orderBookCache.put(key, new CachedOrderBook(freshBook, System.currentTimeMillis()));
            }

            // 🔥 IMPORTANTE: Limpiamos el vuelo
            inflightBookRequests.remove(key, future);

            return freshBook;

        } catch (Exception e) {
            inflightBookRequests.remove(key, future);
            return null;
        }
    }
    private void simulateSpatialScenarioOptimized(String asset, String buyEx, String sellEx, double cap,
                                                  ExchangeConnector.OrderBook bookBuy, ExchangeConnector.OrderBook bookSell,
                                                  double tickerPrice,
                                                  Map<String, Map<String, Double>> balanceSnapshot,
                                                  long snapshotTimestamp,
                                                  double feeBuy, double feeSell) {
        // 1. 👮 CONSULTA AL CFO
        double requiredProfit = BotConfig.NORMAL_MIN_PROFIT;
        boolean isEmergencyMove = false;

        if (cfo != null) {
            var directive = cfo.getAssetHealth(asset);
            if (directive.preferredBuyers().contains(buyEx) || directive.preferredSellers().contains(sellEx)) {
                requiredProfit = directive.minProfitPercent();
                isEmergencyMove = true;
            }
        }

        // 2. ⛽ CHEQUEO DE COMBUSTIBLE
        double realBalanceUsdt = balanceSnapshot != null && balanceSnapshot.containsKey(buyEx)
                ? balanceSnapshot.get(buyEx).getOrDefault("USDT", 0.0) : 0.0;

        if (realBalanceUsdt < BotConfig.MIN_ASSET_VALUE_USDT) return;
        double effectiveCap = Math.min(cap, realBalanceUsdt);

        // =====================================================================
        // 3. 📉 SIMULACIÓN FÍSICA Y FINANCIERA (CORREGIDA)
        // =====================================================================

        // A. Slippage Compra (Estimación Inicial)
        double estimatedQty = effectiveCap / tickerPrice;

        // Obtenemos el precio REAL ponderado para ese volumen
        double realBuyPrice = connector.calculateWeightedPrice(bookBuy, "BUY", estimatedQty);

        // 🚨 VALIDACIÓN CRÍTICA: Si el precio real dispara el slippage, abortamos
        if (realBuyPrice == 0 || (realBuyPrice / tickerPrice) > (1.0 + BotConfig.MAX_SLIPPAGE)) {
            rejectionReasons.computeIfAbsent("SLIPPAGE_BUY", k -> new AtomicLong()).incrementAndGet();
            return;
        }

        // 🔧 CORRECCIÓN MATEMÁTICA: Ajustamos la cantidad a lo que REALMENTE podemos pagar
        // No podemos comprar 'estimatedQty' si el precio real subió.
        double realQtyAsset = effectiveCap / realBuyPrice;

        // B. Slippage Venta (Con la cantidad real ajustada)
        double realSellPrice = connector.calculateWeightedPrice(bookSell, "SELL", realQtyAsset);

        if (realSellPrice == 0 || (realSellPrice / tickerPrice) < (1.0 - BotConfig.MAX_SLIPPAGE)) {
            rejectionReasons.computeIfAbsent("SLIPPAGE_SELL", k -> new AtomicLong()).incrementAndGet();
            return;
        }

        // C. Finanzas (Realistas)
        // Costo Compra = effectiveCap (Ya incluye fees implícitos o se restan según exchange, aquí simplificado)
        // Usamos modelo: Tienes 100 USDT, pagas fee sobre eso, te dan Asset.
        double costBuyFees = effectiveCap * feeBuy;

        // Ingreso Venta = (Cantidad * PrecioVenta) - FeesVenta
        double grossRevenue = realQtyAsset * realSellPrice;
        double costSellFees = grossRevenue * feeSell;

        double netRevenue = grossRevenue - costSellFees;
        double netProfit = netRevenue - effectiveCap; // Lo que entra menos lo que salió

        // Spread Bruto Real (Para el Log)
        double grossSpreadPct = ((realSellPrice - realBuyPrice) / realBuyPrice) * 100.0;

        // =====================================================================
        // 4. ⚖️ VEREDICTO FINAL
        // =====================================================================

// =====================================================================
        // 4. ⚖️ VEREDICTO FINAL
        // =====================================================================

        if (netProfit > requiredProfit) {

            // ┌─────────────────────────────────────────────────────────────────────────────┐
            // │ 🧪 [DEBUG ZONE] TELEMETRÍA MATEMÁTICA DETALLADA (SOLO PARA CALIBRACIÓN)     │
            // │ ⚠️ ADVERTENCIA: La creación de Strings es costosa.                          │
            // │ 📝 TODO: Comentar o Eliminar este bloque entero antes de pasar a Producción.  │
            // └─────────────────────────────────────────────────────────────────────────────┘
            String tradeTerms = "N/A"; // Valor por defecto para no romper el Logger
            //eliminar en producción comentando el if {}
            // Solo gastamos CPU formateando el string si estamos en modo Simulación (DryRun)
            if (BotConfig.DRY_RUN) {
                tradeTerms = String.format(java.util.Locale.US,
                        "In:%.5f|Out:%.5f|Fees:%.5f|Vol:%.2f",
                        realBuyPrice,   // Precio ponderado real de compra (con slippage)
                        realSellPrice,  // Precio ponderado real de venta (con slippage)
                        (costBuyFees + costSellFees), // Total pagado en comisiones
                        realQtyAsset    // Cantidad real del activo movida
                );
            }

            // └─────────────────────────────────────────────────────────────────────────────┘

            // Log Visual Consola (Esto se mantiene, es ligero)
            double totalFees = costBuyFees + costSellFees;
            if (isEmergencyMove) {
                BotLogger.warn("🚑 REBALANCEO (" + buyEx + "->" + sellEx + ") | Profit: " + dfUsdt.get().format(netProfit));
            }

            updateBestOpportunity(buyEx + "->" + sellEx, asset, "SPATIAL", netProfit);
            printRow(asset, buyEx, sellEx, grossSpreadPct, totalFees, 0.0, netProfit);

            // 🔥 EJECUCIÓN O SIMULACIÓN
            if (!BotConfig.DRY_RUN && tradesCount.get() == 0) {
                // ... (Lógica de Ejecución Real - SIN CAMBIOS) ...
                if (coordinator != null && coordinator.tryAcquireDualLock(buyEx, sellEx)) {
                    try {
                        BotLogger.warn("🚀 EJECUTANDO SECUENCIA ESPACIAL [Cap: $" + effectiveCap + "]");
                        crossExecutor.executeCrossTrade(buyEx, sellEx, asset + "USDT", realQtyAsset, realBuyPrice, realSellPrice);
                        this.forceBalanceUpdate = true;
                        tradesCount.incrementAndGet();

                        // En prod, tradeTerms será "N/A" (ligero), o puedes pasar null si el logger lo aguanta.
                        BotLogger.logOpportunity("SPATIAL", asset, buyEx + "->" + sellEx,
                                grossSpreadPct, netProfit, "EXECUTED", tradeTerms);

                    } catch (Exception e) {
                        BotLogger.error("❌ ERROR CRÍTICO: " + e.getMessage());
                    } finally {
                        coordinator.releaseLock(buyEx);
                        coordinator.releaseLock(sellEx);
                    }
                } else {
                    BotLogger.warn("🔒 BLOQUEO ACTIVO EN " + buyEx + "/" + sellEx);
                }
            } else {
                // 🧪 REGISTRO DE SIMULACIÓN (AQUÍ ES DONDE QUEREMOS VER LOS DATOS)
                if (netProfit > -1.0) {
                    BotLogger.logOpportunity("SPATIAL", asset, buyEx + "->" + sellEx,
                            grossSpreadPct, netProfit,
                            BotConfig.DRY_RUN ? "SIMULATED" : "SKIPPED",
                            tradeTerms // <--- AQUÍ PASAMOS LA EVIDENCIA FORENSE
                    );
                }
            }

            if (Double.compare(cap, testCapitals.get(0)) == 0) {
                totalPotentialProfit.add(netProfit);
            }
        }
    }

    public void shutdown() {
        BotLogger.warn("🛑 INICIANDO SECUENCIA DE APAGADO...");

        // 1. Detener Cerebro
        scheduler.shutdownNow();

        // 2. Detener Hilos de Fuerza
        virtualExecutor.shutdownNow();

        // 3. Imprimir Reporte Final
        BotLogger.info("📊 REPORTE FINAL DE SESIÓN:");
        BotLogger.info("   Trades Totales: " + tradesCount.get());
        BotLogger.info("   Profit Potencial: $" + dfUsdt.get().format(totalPotentialProfit.sum()));

        BotLogger.info("👋 Agente Tokio Desconectado. Sayonara.");
    }
}