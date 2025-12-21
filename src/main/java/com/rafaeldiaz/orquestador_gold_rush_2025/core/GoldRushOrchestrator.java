package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * 🎼 DIRECTOR DE ORQUESTA (VERSIÓN BLINDADA CON RISK MANAGER)
 */
public class GoldRushOrchestrator implements MarketListener {

    private final List<ExchangeStrategy> strategies;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final FeeManager feeManager;
    private final ProfitCalculator profitCalculator;
    private final DynamicPairSelector pairSelector;
    private final TradeExecutor executor; // <--- Necesitamos el Executor aquí
    private final RiskManager riskManager; // <--- El Gobernador

    private final List<String> activeTargets = new CopyOnWriteArrayList<>();
    private final double capital = 300.0;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Estadísticas
    private final LongAdder totalScans = new LongAdder();
    private final LongAdder positiveSpreads = new LongAdder();
    private double bestNetProfit = -9999.0;
    private String bestPairLog = "N/A";

    public GoldRushOrchestrator(List<ExchangeStrategy> strategies, ExchangeConnector connector) {
        this.strategies = strategies;
        this.feeManager = new FeeManager(connector);
        this.profitCalculator = new ProfitCalculator();

        // Inicializamos componentes críticos
        this.riskManager = new RiskManager(capital);
        this.executor = new TradeExecutor(connector);
        // Configurar DryRun según necesitemos (true = simulacro seguro)
        this.executor.setDryRun(true);

        this.pairSelector = new DynamicPairSelector(connector, this);
        this.activeTargets.add("SOLUSDT"); // Target inicial
    }

    @Override
    public void updateTargets(List<String> newTargets) {
        if (!newTargets.isEmpty()) {
            activeTargets.clear();
            activeTargets.addAll(newTargets);
            BotLogger.info("🎯 ORQUESTADOR: Objetivos -> " + activeTargets);
        }
    }

    public void startSurveillance() {
        BotLogger.info("🔭 ORQUESTADOR INICIANDO VIGILANCIA...");
        BotLogger.info("   ↳ RiskManager: ACTIVO (-2% Daily / -8% DD)");
        pairSelector.start();
        scheduler.scheduleAtFixedRate(this::scanMarkets, 1, 3, TimeUnit.SECONDS);
    }

    private void scanMarkets() {
        try {
            // Verificar primero si el Gobernador permite operar
            if (!riskManager.canExecuteTrade()) {
                BotLogger.warn("⛔ SISTEMA DETENIDO POR RISK MANAGER.");
                return; // No gastamos recursos escaneando si no podemos disparar
            }

            if (activeTargets.isEmpty()) return;
            totalScans.increment();
            String timestamp = LocalTime.now().format(timeFmt);
            List<Callable<MarketSnapshot>> tasks = new ArrayList<>();

            for (String pair : activeTargets) {
                for (ExchangeStrategy strategy : strategies) {
                    tasks.add(() -> fetchMarketData(strategy, pair));
                }
            }

            List<Future<MarketSnapshot>> futures = virtualExecutor.invokeAll(tasks);
            analyzeAndReport(futures, timestamp);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            BotLogger.error("🔥 Error en ciclo de escaneo: " + e.getMessage());
        }
    }

    record MarketSnapshot(String exchange, String pair, double bid, double ask, double fee) {}

    private MarketSnapshot fetchMarketData(ExchangeStrategy strategy, String pair) {
        try {
            return new MarketSnapshot(
                    strategy.getName(), pair,
                    strategy.fetchBid(pair), strategy.fetchAsk(pair),
                    strategy.getTradingFee(pair)
            );
        } catch (Exception e) {
            return new MarketSnapshot(strategy.getName(), pair, 0, 0, 0);
        }
    }

    private void analyzeAndReport(List<Future<MarketSnapshot>> futures, String time) {
        List<MarketSnapshot> snapshots = new ArrayList<>();
        for (Future<MarketSnapshot> f : futures) {
            try {
                MarketSnapshot s = f.get();
                if (s.bid() > 0 && s.ask() > 0) snapshots.add(s);
            } catch (Exception ignored) {}
        }

        for (String targetPair : activeTargets) {
            analyzeSinglePair(snapshots, targetPair, time);
        }
    }

    private void analyzeSinglePair(List<MarketSnapshot> allSnapshots, String pair, String time) {
        List<MarketSnapshot> pairData = allSnapshots.stream()
                .filter(s -> s.pair().equals(pair) || s.pair().equals(pair.replace("USDT", "-USDT")))
                .toList();

        if (pairData.size() < 2) return;

        StringBuilder logBatch = new StringBuilder();

        for (MarketSnapshot source : pairData) {
            for (MarketSnapshot target : pairData) {
                if (source.exchange().equals(target.exchange())) continue;

                double buyPrice = source.ask();
                double sellPrice = target.bid();
                double buyFeeRate = source.fee();
                double sellFeeRate = target.fee();

                String asset = pair.replace("USDT", "").replace("-", "");
                double withdrawQty = feeManager.getWithdrawalFee(source.exchange().toLowerCase(), asset);

                if (withdrawQty < 0) continue;

                ProfitCalculator.AnalysisResult result = profitCalculator.calculateCrossTrade(
                        capital, buyPrice, sellPrice, buyFeeRate, sellFeeRate, withdrawQty
                );

                double netProfit = result.netProfit();

                if (netProfit > 0) positiveSpreads.increment();
                if (netProfit > bestNetProfit) {
                    bestNetProfit = netProfit;
                    bestPairLog = String.format("[%s] %s->%s ($%.2f)", pair, source.exchange(), target.exchange(), netProfit);
                }

                if (netProfit > 0.05) { // Log solo si paga el café
                    logBatch.append(String.format(
                            "[%s] 💰 %-6s %-7s->%-7s | NETO:$%6.2f | ROI:%.2f%%\n",
                            time, pair, source.exchange(), target.exchange(), netProfit, result.roiPercent()
                    ));
                }

                // 🔥 ZONA DE DISPARO 🔥
                if (result.isProfitable()) {
                    // 1. Preguntamos al Gobernador
                    if (riskManager.canExecuteTrade()) {
                        BotLogger.warn("🚀 EJECUTANDO OPORTUNIDAD: " + pair + " (Neto: $" + netProfit + ")");

                        // 2. Disparamos al Músculo (Ahora retorna el PnL real/simulado)
                        // Calculamos la cantidad de asset basada en capital USDT
                        double amountAsset = capital / buyPrice;

                        double actualPnL = executor.executeSpatialArbitrage(
                                asset, source.exchange(), target.exchange(), amountAsset
                        );

                        // 3. Reportamos el resultado al Gobernador (Feedback Loop Cerrado)
                        riskManager.reportTradeResult(actualPnL);

                    } else {
                        BotLogger.warn("⛔ OPORTUNIDAD RECHAZADA POR RISK MANAGER.");
                    }
                }
            }
        }
        if (logBatch.length() > 0) System.out.print(logBatch);
    }

    public void stop() {
        scheduler.shutdown();
        pairSelector.stop();
        virtualExecutor.shutdown();
        BotLogger.info("🏁 REPORTE FINAL: Max Profit Visto: $" + bestNetProfit);
    }
}