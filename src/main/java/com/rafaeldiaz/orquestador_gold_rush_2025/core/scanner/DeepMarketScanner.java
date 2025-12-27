package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.GlobalBalanceReporter;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.TradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.text.DecimalFormat;
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
    private final ExecutorService balanceExecutor = Executors.newFixedThreadPool(4);
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
    private final Map<String, CachedOrderBook> orderBookCache = new ConcurrentHashMap<>();
    private static final long ORDERBOOK_TTL_MS = 2000; // 2 segundos por meter en botlogger
    private record CachedOrderBook(ExchangeConnector.OrderBook book, long timestamp) {}
    // Configuración: Refrescar saldos solo cada 60 segundos si no hay trades
    private static final long BALANCE_TTL_MS = 60_000;

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

    public DeepMarketScanner(ExchangeConnector connector, ExecutionCoordinator coordinator) {
        this.connector = connector;
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
// this.crossExecutor.setDryRun(BotConfig.DRY_RUN); <---- Esta línea es la de Producción !!!

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
                    Thread.sleep(BotConfig.SCAN_DELAY);
                } catch (InterruptedException e) {
                    break;
                }
            }
            finalizeScan();
        });
    }
    // ✅ 2. ACTUALIZAR ESTE MÉTODO (Aquí nace el timestamp)
    private void scanFullMatrixBatchOptimized() {
        refreshBalancesResult(); // 1. Actualizar caché

        Map<String, Map<String, Double>> marketData = new ConcurrentHashMap<>();

        // ⏰ TIMESTAMP DE NACIMIENTO (Runtime)
        long snapshotTimestamp = System.currentTimeMillis();

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

        List<Callable<Void>> tasks = new ArrayList<>();
        for (String asset : huntingGrounds) {
            tasks.add(() -> {
                // ➡️ Pasamos el timestamp hacia abajo
                analyzeAssetInMemory(asset, marketData, cachedBalances, snapshotTimestamp);
                return null;
            });
        }
        try { virtualExecutor.invokeAll(tasks); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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
        String pair1 = asset + "USDT";
        Double price1 = prices.get(pair1);

        // Si no hay precio base en USDT, no podemos empezar
        if (price1 == null) return;

        for (String bridge : BRIDGE_ASSETS) {
            if (bridge.equals(asset)) continue;

            String pair2 = asset + bridge; // Ej: WIFBTC
            Double price2 = prices.get(pair2);

            // Si no existe directo (WIFBTC), probamos inverso (BTCWIF) si el exchange lo usa
            // (Nota: Por simplicidad, asumimos convención estándar Base+Quote primero)

            String pair3 = bridge + "USDT"; // Ej: BTCUSDT
            Double price3 = prices.get(pair3);

            if (price2 != null && price3 != null) {
                // Cálculo Teórico
                double crossRate = (1.0 / price1) * price2 * price3;

                /* 🔇 COMENTAMOS LA SONDA PARA RECUPERAR VELOCIDAD
                double theoreticalSpread = (crossRate - 1.0) * 100.0; // En porcentaje

                // 🔍 SONDA DE VIDA: Si el spread bruto es positivo (aunque sea 0.01%), avísanos
                // Esto es solo para depurar, lo quitaremos después.
                if (theoreticalSpread > 0.05) { // Si hay al menos 0.05% de luz
                    BotLogger.info(String.format("📐 RASTRO: %s | %s-%s | Gap Bruto: %.4f%% (Fees est: 0.30%%)",
                            exchange, asset, bridge, theoreticalSpread));
                }
                */
                // Filtro "Portero" Original
                if (crossRate > (1.0 + BotConfig.MIN_SCAN_SPREAD)) {
                    validateTriangularOpportunity(exchange, asset, bridge, price1);
                }
            } else {
                // 🕵️‍♂️ DIAGNÓSTICO DE PARES FANTASMA
                // Solo logueamos esto una vez cada tanto para no saturar, o activalo si no ves NADA.
                // BotLogger.warn("👻 Par faltante en " + exchange + ": " + pair2 + " o " + pair3);
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
            ExchangeConnector.OrderBook book1 = connector.fetchOrderBook(exchange, pair1, 20);
            ExchangeConnector.OrderBook book2 = connector.fetchOrderBook(exchange, pair2, 20);
            ExchangeConnector.OrderBook book3 = connector.fetchOrderBook(exchange, pair3, 20);

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
        if (netProfit > BotConfig.MIN_PROFIT_THRESHOLD) {

            // A. Registrar la mejor oportunidad para Telegram
            updateBestOpportunity(exchange, asset, bridge, netProfit);

            // B. Imprimir en consola (Fila Visual)
            printTriangularRow(exchange, asset, bridge, cap, grossGap, totalFees, netProfit);

            // C. EJECUCIÓN DE FUEGO REAL (Solo si es el capital maestro y no es simulacro)
            // Se usa 'Double.compare' para evitar errores de precisión flotante
            if (!BotConfig.DRY_RUN && Double.compare(cap, BotConfig.SEED_CAPITAL) == 0 && tradesCount.get() == 0) {
                // ⛔ COMENTADO POR SEGURIDAD (tradeExecutor es null en arquitectura Espacial)
                // BotLogger.warn("🚀 OPORTUNIDAD REAL DETECTADA. EJECUTANDO...");
                // tradeExecutor.executeTriangular(exchange, asset, bridge, cap);

                BotLogger.warn("⚠️ Oportunidad Triangular detectada pero ignorada (Modo Espacial Activo).");
            }

            // D. Actualización de Métricas Globales (Solo sumamos UNA vez por ciclo, usando el capital base)
            // Esto evita que sumemos el profit de $10, $100 y $1000 al mismo tiempo en el total
            if (Double.compare(cap, testCapitals.get(0)) == 0) {
                totalPotentialProfit.add(netProfit);
                tradesCount.incrementAndGet();
            }
        }
    }

   private void printTriangularRow(String exchange, String asset, String bridge, double cap, double gap, double fees, double net) {
        // Construimos el mensaje en el stack del hilo local
        String time = LocalTime.now().format(timeFmt);
        String route = "⚡ " + asset + "-" + bridge;
        String sNet = (net > 0 ? "💎 $" : "🔻 $") + dfUsdt.get().format(net);

        String logLine = String.format("║ %s ║ %-3s ║ %-13s ║ %5.0f ║ %6s ║ %6s ║ %s ║",
                time, exchange.substring(0,3).toUpperCase(), route, cap,
                dfUsdt.get().format(gap), dfUsdt.get().format(fees), sNet);

        // 🔥 FUEGO ASÍNCRONO: Delegamos al Logger sin bloquear el Scanner
        BotLogger.info(logLine);
    }

    // Método auxiliar Spatial Print (para compatibilidad)
    // [VISUALIZACIÓN] 🎨 TABLA SPATIAL (Legacy Support Optimizado)
    private void printRow(String asset, String buyEx, String sellEx, double gap, double tradeFee, double netFee, double net) {
        String time = LocalTime.now().format(timeFmt);
        String route = buyEx.substring(0,3).toUpperCase() + "->" + sellEx.substring(0,3).toUpperCase();

        // ✅ VERIFICACIÓN DE USO:
        String sGap = dfUsdt.get().format(gap);    // Dinero bruto
        String sTFee = dfPct.get().format(tradeFee); // Tasa de comisión (%)
        String sNFee = dfPct.get().format(netFee);   // Tasa neta (%)
        String sNet = (net > 0 ? "💎 " : "🔻 ") + dfUsdt.get().format(net); // Dinero neto

        String logLine = String.format("║ %s ║ %-6s ║ %-13s ║ %5s ║ %6s ║ %6s ║ %6s ║ %s ║",
                time, asset, route, "224", sGap, sTFee, sNFee, sNet);

        BotLogger.info(logLine);
    }
    private void printHeader() {
        // Encabezado expandido
        System.out.println("\n╔══════════╦════════╦═══════════════╦═══════╦════════╦════════╦════════╦════════════╗");
        System.out.println("║   HORA   ║ ACTIVO ║     RUTA      ║ CAP($)║ GAP($) ║ T.FEES ║ RED($) ║  NETO($)   ║");
        System.out.println("╠══════════╬════════╬═══════════════╬═══════╬════════╬════════╬════════╬════════════╣");
    }

    /**
     * 📸 SNAPSHOT DE CONFIGURACIÓN (CAJA NEGRA)
     * Documenta el estado inicial de todas los parámetros
     * para análisis forense posterior.
     */
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
        BotLogger.info(String.format(C + "║ ⏱️  SCAN INTERVAL:    " + W + "%-35s " + C + "║" + R, BotConfig.SCAN_DELAY + " ms"));
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
// ✅
    private void refreshBalancesResult() {
        long now = System.currentTimeMillis();

        // Solo entramos si toca actualizar
        if (!forceBalanceUpdate && (now - lastBalanceUpdate) < BALANCE_TTL_MS) {
            return;
        }

        BotLogger.info("🌐 Refrescando saldos (Modo Paralelo Real)...");

        List<CompletableFuture<Void>> futures = exchanges.stream()
                .map(ex -> CompletableFuture.runAsync(() -> {
                            try {
                                // 1. Sin synchronized. El conector ya es thread-safe.
                                // 2. Sin sleep artificial. Si queremos velocidad, pedimos velocidad.
                                //    El ExchangeConnector ya tiene lógica de reintentos si falla.

                                Map<String, Double> balances = connector.fetchBalances(ex);

                                if (balances != null && !balances.isEmpty()) {
                                    cachedBalances.put(ex, balances);
                                    // Opcional: BotLogger.info("💰 " + ex + " actualizado.");
                                }
                            } catch (Exception e) {
                                // Silencioso: Si falla, el Scanner usará el saldo viejo de la caché.
                                // No detenemos el show.
                            }
                        }, balanceExecutor)
                        .orTimeout(3, TimeUnit.SECONDS) // ⏱️ TIMEOUT DURO: Si tarda > 3s, abortar ese hilo.
                        .exceptionally(e -> {
                            // Si ocurre timeout, no imprimimos stacktrace gigante, solo aviso
                            return null;
                        }))
                .toList();

        // Esperamos a todos, pero con un límite máximo global de seguridad
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .join(); // Espera a que terminen los 4 hilos (o den timeout)
        } catch (Exception e) {
            BotLogger.warn("⚠️ Actualización de saldos incompleta (Continuando scan...)");
        }

        lastBalanceUpdate = now;
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
    private ExchangeConnector.OrderBook fetchOrderBookCached(String exchange, String pair, int depth) {
        String key = exchange + "_" + pair;
        CachedOrderBook cached = orderBookCache.get(key);

        long now = System.currentTimeMillis();

        // ✅ Si el caché es fresco (< 2 segundos), reutilizamos
        if (cached != null && (now - cached.timestamp) < ORDERBOOK_TTL_MS) {
            return cached.book;
        }

        // ⚡ Descarga nueva (blocking, pero solo si es necesario)
        ExchangeConnector.OrderBook fresh = connector.fetchOrderBook(exchange, pair, depth);

        if (fresh != null) {
            orderBookCache.put(key, new CachedOrderBook(fresh, now));
        }

        return fresh;
    }
    // 🧠 MOTOR DE SIMULACIÓN ESPACIAL OPTIMIZADO (v6.1 - Math Fix)
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

        if (netProfit > requiredProfit) {

            // Log Visual Consola
            // Calculamos total fees para mostrar en la tabla
            double totalFees = costBuyFees + costSellFees;

            if (isEmergencyMove) {
                BotLogger.warn("🚑 REBALANCEO (" + buyEx + "->" + sellEx + ") | Profit: " + dfUsdt.get().format(netProfit));
            }

            updateBestOpportunity(buyEx + "->" + sellEx, asset, "SPATIAL", netProfit);
            printRow(asset, buyEx, sellEx, grossSpreadPct, totalFees, 0.0, netProfit);

            // 🔥 EJECUCIÓN
            if (!BotConfig.DRY_RUN && tradesCount.get() == 0) {
                if (coordinator != null && coordinator.tryAcquireDualLock(buyEx, sellEx)) {
                    try {
                        BotLogger.warn("🚀 EJECUTANDO SECUENCIA ESPACIAL [Cap: $" + effectiveCap + "]");

                        crossExecutor.executeCrossTrade(buyEx, sellEx, asset + "USDT"
                                , realQtyAsset, realBuyPrice, realSellPrice);

                        this.forceBalanceUpdate = true;
                        tradesCount.incrementAndGet();

                        BotLogger.logOpportunity("SPATIAL", asset, buyEx + "->" + sellEx,
                                grossSpreadPct, netProfit, "EXECUTED", "PROFITABLE");

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
                // Registro DRY-RUN
                // 🧹 Filtro de limpieza: Solo loguear si es matemáticamente coherente
                if (netProfit > -1.0) {
                    BotLogger.logOpportunity("SPATIAL", asset, buyEx + "->" + sellEx,
                            grossSpreadPct, netProfit,
                            BotConfig.DRY_RUN ? "SIMULATED" : "SKIPPED", "PROFITABLE");
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
        balanceExecutor.shutdownNow();

        // 3. Imprimir Reporte Final
        BotLogger.info("📊 REPORTE FINAL DE SESIÓN:");
        BotLogger.info("   Trades Totales: " + tradesCount.get());
        BotLogger.info("   Profit Potencial: $" + dfUsdt.get().format(totalPotentialProfit.sum()));

        BotLogger.info("👋 Agente Tokio Desconectado. Sayonara.");
    }
}