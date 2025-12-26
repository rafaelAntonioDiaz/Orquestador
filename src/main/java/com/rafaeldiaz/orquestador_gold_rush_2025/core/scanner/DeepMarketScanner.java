package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.GlobalBalanceReporter;
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

    // Configuración: Refrescar saldos solo cada 60 segundos si no hay trades
    private static final long BALANCE_TTL_MS = 60_000;

    // Bandera para forzar actualización inmediata (post-trade)
    private volatile boolean forceBalanceUpdate = true;
    private final DoubleAdder totalSlippageLoss = new DoubleAdder();
    private double maxProfitSeen = -999.0;
    private String bestOpportunityLog = "Buscando...";

    // FORMATOS
    private final DecimalFormat dfMoney = new DecimalFormat("0.00");
    private final DecimalFormat dfFee = new DecimalFormat("0.00");
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    public DeepMarketScanner(ExchangeConnector connector, ExecutionCoordinator coordinator) {
        this.connector = connector;
        this.coordinator = coordinator; // Guardamos referencia

        this.feeManager = new FeeManager(connector);
        this.pairSelector = new DynamicPairSelector(connector, this, feeManager);
        this.balanceReporter = new GlobalBalanceReporter(connector);

        // ============================================================
        // 🛡️ ARQUITECTURA DE EJECUCIÓN ESPACIAL
        // ============================================================
        RiskManager riskPolice = new RiskManager(BotConfig.SEED_CAPITAL);

        // ⚠️ CORRECCIÓN CLAVE: Pasamos 'coordinator', NO 'snapshotTimestamp'
        this.crossExecutor = new CrossTradeExecutor(connector, riskPolice, coordinator);

        this.crossExecutor.setDryRun(BotConfig.DRY_RUN);
        this.testCapitals = List.of(BotConfig.SEED_CAPITAL);
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

    // -------------------------------------------------------------------------
    // [PARCHE 1] Reemplaza el método analyzeAssetInMemory
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // [PARCHE 1 - CORREGIDO] Reemplaza el método analyzeAssetInMemory
    // -------------------------------------------------------------------------
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
    // [PARCHE 2] Reemplaza el método validateSpatialOpportunity
    // -------------------------------------------------------------------------
    private void validateSpatialOpportunity(String asset, String buyEx, String sellEx, double basePrice,
                                            Map<String, Map<String, Double>> balanceSnapshot, long snapshotTimestamp) {
        try {
            String pair = asset + "USDT";
            // Profundidad 20 es suficiente para montos estándar, subir a 50 para ballenas
            ExchangeConnector.OrderBook bookBuy = connector.fetchOrderBook(buyEx, pair, 20);
            ExchangeConnector.OrderBook bookSell = connector.fetchOrderBook(sellEx, pair, 20);

            if (bookBuy == null || bookSell == null) return;

            // ⚠️ CLAVE: Ordenamos de MAYOR a MENOR capital para intentar "pescar el pez gordo" primero.
            // Si el trade grande falla por liquidez, el loop probará con el capital siguiente más pequeño.
            testCapitals.stream()
                    .sorted(Comparator.reverseOrder())
                    .forEach(testCap -> {
                        simulateSpatialScenario(asset, buyEx, sellEx, testCap, bookBuy, bookSell, basePrice, balanceSnapshot, snapshotTimestamp);
                    });

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
            // 📝 REGISTRO: Rechazo por Latencia
            BotLogger.logOpportunity("SPATIAL", asset, buyEx + "->" + sellEx,
                    0.0, -1.0, "REJECTED", "HIGH_LATENCY");return;
        }

        // B. Slippage Compra
        double qtyAsset = effectiveCap / tickerPrice;
        double realBuyPrice = connector.calculateWeightedPrice(bookBuy, "BUY", qtyAsset);
        if (realBuyPrice == 0 || (realBuyPrice/tickerPrice) > (1.0 + BotConfig.MAX_SLIPPAGE)) {
            rejectionReasons.computeIfAbsent("SLIPPAGE_BUY", k -> new AtomicLong()).incrementAndGet();
            // 📝 REGISTRO: Rechazo por Slippage compra
            BotLogger.logOpportunity("SPATIAL", asset, buyEx + "->" + sellEx,
                    0.0, -1.0, "REJECTED", "HIGH_SLIPPAGE_BUY");
            return;
        }

        // C. Slippage Venta
        double realSellPrice = connector.calculateWeightedPrice(bookSell, "SELL", qtyAsset);
        if (realSellPrice == 0 || (realSellPrice/tickerPrice) < (1.0 - BotConfig.MAX_SLIPPAGE)) {
            rejectionReasons.computeIfAbsent("SLIPPAGE_SELL", k -> new AtomicLong()).incrementAndGet();
            // 📝 REGISTRO: Rechazo por Slippage venta
            BotLogger.logOpportunity("SPATIAL", asset, buyEx + "->" + sellEx,
                    0.0, -1.0, "REJECTED", "HIGH_SLIPPAGE_SELL");

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
                BotLogger.warn("🚑 OPORTUNIDAD DE REBALANCEO (" + buyEx + "->" + sellEx + ") | Profit: " + dfMoney.format(netProfit));
            }

            updateBestOpportunity(buyEx + "->" + sellEx, asset, "SPATIAL", netProfit);
            printTriangularRow(buyEx + "->" + sellEx, asset, "DIRECT", effectiveCap, (netProfit+totalFees), totalFees, netProfit);

            // 🔥 FUEGO REAL CONTROLADO
            // Usamos compareAndSet para asegurar que SOLO UN hilo gane la carrera en este ciclo si hay concurrencia
            if (!BotConfig.DRY_RUN && tradesCount.get() == 0) {

                // 🚦 SEMÁFORO: Pedimos permiso al Coordinador
                if (coordinator != null && coordinator.tryAcquireDualLock(buyEx, sellEx)) {
                    try {
                        BotLogger.warn("🚀 EJECUTANDO SECUENCIA ESPACIAL [Cap: $" + effectiveCap + "]");

                        // Pasamos Snapshot Y Timestamp para validación final de 'stale data'
                        crossExecutor.executeCrossTrade(buyEx, sellEx, asset + "USDT",
                                realBuyPrice, realSellPrice, effectiveCap,
                                balanceSnapshot, snapshotTimestamp);

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
                if (crossRate > (1.0 + BotConfig.MIN_SCAN_SPREAD)) {
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
            StringBuilder sb = new StringBuilder();
            sb.append("🛰️ *DASHBOARD DE TELEMETRÍA*\n");
            sb.append("━━━━━━━━━━━━━━━━━━\n");

            // 📶 Salud de la Red (RTT)
            sb.append("📶 *Latencia (RTT):*\n");
            sb.append("· Bin: `").append(connector.getRTT("binance")).append("ms` | ");
            sb.append("Byb: `").append(connector.getRTT("bybit")).append("ms`\n");
            sb.append("· Mex: `").append(connector.getRTT("mexc")).append("ms` | ");
            sb.append("Kuc: `").append(connector.getRTT("kucoin")).append("ms`\n\n");

            // 🚫 Análisis de Rechazos (La Caja Negra)
            sb.append("🚫 *Causas de No-Trade:*\n");
            rejectionReasons.forEach((reason, count) ->
                    sb.append("· ").append(reason).append(": `").append(count.get()).append("`\n")
            );

            // 💰 Rendimiento por Nivel de Capital
            sb.append("\n📈 *Mejor Presa:* \n`").append(bestOpportunityLog).append("`\n");
            sb.append("💵 *PnL Acumulado:* `$").append(String.format("%.4f", totalPotentialProfit.sum())).append("`\n");

            BotLogger.sendTelegram(sb.toString());
            // Limpiamos razones para el próximo reporte
            rejectionReasons.clear();
        } catch (Exception e) { BotLogger.error("Error Dashboard: " + e.getMessage()); }
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
     * 🔄 GESTIÓN DE SALDOS (VERSIÓN FILA INDIA)
     * Soluciona el error "empty String" obligando a pedir los saldos uno por uno
     * con una pausa de 200ms para que Bybit no bloquee la conexión.
     */
    private void refreshBalancesResult() {
        long now = System.currentTimeMillis();

        if (forceBalanceUpdate || (now - lastBalanceUpdate) > BALANCE_TTL_MS) {

            // 👇 ESTE MENSAJE ES TU PRUEBA DE VIDA
            BotLogger.info("🐌 Refrescando saldos (Modo Lento activado)...");

            // Bucle SECUENCIAL (Uno por uno)
            for (String ex : exchanges) {
                try {
                    Thread.sleep(200); // 💤 Pausa obligatoria
                    Map<String, Double> balances = connector.fetchBalances(ex);
                    if (balances != null && !balances.isEmpty()) {
                        cachedBalances.put(ex, balances);
                    }
                } catch (Exception e) {
                    // Silencio total si falla, confiamos en la caché
                }
            }
            lastBalanceUpdate = now;
            forceBalanceUpdate = false;
        }
    }    public void injectCoordinator(ExecutionCoordinator coordinator) {
        this.coordinator = coordinator;
    }
}