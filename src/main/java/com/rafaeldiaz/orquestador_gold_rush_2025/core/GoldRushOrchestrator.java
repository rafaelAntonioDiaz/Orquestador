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
 * 🎼 DIRECTOR DE ORQUESTA (VERSIÓN FINAL)
 * Coordina: Radar (Selector) -> Motores (Exchanges) -> Financiera (ProfitCalc).
 * Capacidad: Multi-Par, Multi-Exchange, Hilos Virtuales.
 */
public class GoldRushOrchestrator implements MarketListener {

    private final List<ExchangeStrategy> strategies;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // ⚡ Hilos Virtuales: Ideales para I/O intensivo (4 exchanges x N monedas)
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final FeeManager feeManager;
    private final ProfitCalculator profitCalculator;
    private final DynamicPairSelector pairSelector; // El Radar

    // 🎯 OBJETIVOS ACTIVOS (Dinámicos)
    // Usamos CopyOnWriteArrayList para que sea seguro leer mientras el radar escribe
    private final List<String> activeTargets = new CopyOnWriteArrayList<>();

    private final double capital = 300.0; // Capital operativo simulado ($300)
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // 📊 ESTADÍSTICAS
    private final LongAdder totalScans = new LongAdder();
    private final LongAdder positiveSpreads = new LongAdder();
    private double bestNetProfit = -9999.0;
    private String bestPairLog = "N/A";

    public GoldRushOrchestrator(List<ExchangeStrategy> strategies, ExchangeConnector connector) {
        this.strategies = strategies;
        this.feeManager = new FeeManager(connector);
        this.profitCalculator = new ProfitCalculator();

        // Inicializamos el Radar pasándole "this" (el orquestador) como oyente
        this.pairSelector = new DynamicPairSelector(connector, this);

        // Objetivo por defecto para no arrancar vacíos
        this.activeTargets.add("SOLUSDT");
    }

    /**
     * 👂 Callback del Radar: Actualiza la lista de caza
     */
    @Override
    public void updateTargets(List<String> newTargets) {
        if (!newTargets.isEmpty()) {
            activeTargets.clear();
            activeTargets.addAll(newTargets);
            BotLogger.info("🎯 ORQUESTADOR: Objetivos actualizados -> " + activeTargets);
        }
    }

    public void startSurveillance() {
        BotLogger.info("🔭 ORQUESTADOR INICIANDO SISTEMAS...");
        BotLogger.info("   ↳ Capital Base: $" + capital);
        BotLogger.info("   ↳ Exchanges: " + strategies.size() + " (Binance, Bybit, Mexc, Kucoin)");

        // 1. Encender el Radar (Busca volatilidad cada 60s)
        pairSelector.start();

        // 2. Encender el Escáner de Precios (Pulso cada 3 segundos)
        // Le damos un poco más de tiempo porque ahora consultamos 4 exchanges
        scheduler.scheduleAtFixedRate(this::scanMarkets, 1, 3, TimeUnit.SECONDS);
    }

    private void scanMarkets() {
        try {
            if (activeTargets.isEmpty()) return;

            totalScans.increment();
            String timestamp = LocalTime.now().format(timeFmt);
            List<Callable<MarketSnapshot>> tasks = new ArrayList<>();

            // 🌪️ GENERACIÓN DE TAREAS MASIVAS
            // Para cada Moneda Caliente... en cada Exchange... lanzamos un hilo.
            for (String pair : activeTargets) {
                for (ExchangeStrategy strategy : strategies) {
                    tasks.add(() -> fetchMarketData(strategy, pair));
                }
            }

            // Ejecución Paralela (Scatter)
            List<Future<MarketSnapshot>> futures = virtualExecutor.invokeAll(tasks);

            // Análisis (Gather)
            analyzeAndReport(futures, timestamp);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            BotLogger.error("🔥 Error en ciclo de escaneo: " + e.getMessage());
        }
    }

    // Record de datos puros
    record MarketSnapshot(String exchange, String pair, double bid, double ask, double fee) {}

    private MarketSnapshot fetchMarketData(ExchangeStrategy strategy, String pair) {
        try {
            // Kucoin usa guión, otros no. El Strategy ya lo maneja internamente.
            return new MarketSnapshot(
                    strategy.getName(),
                    pair,
                    strategy.fetchBid(pair),
                    strategy.fetchAsk(pair),
                    strategy.getTradingFee(pair)
            );
        } catch (Exception e) {
            return new MarketSnapshot(strategy.getName(), pair, 0, 0, 0);
        }
    }

    private void analyzeAndReport(List<Future<MarketSnapshot>> futures, String time) {
        List<MarketSnapshot> snapshots = new ArrayList<>();
        // Recolectar resultados exitosos
        for (Future<MarketSnapshot> f : futures) {
            try {
                MarketSnapshot s = f.get();
                if (s.bid() > 0 && s.ask() > 0) snapshots.add(s);
            } catch (Exception ignored) {}
        }

        // 🧠 LÓGICA DE CRUCE "TODOS CONTRA TODOS" POR PAR
        // Agrupamos snapshots por moneda para no cruzar SOL con PEPE
        for (String targetPair : activeTargets) {
            analyzeSinglePair(snapshots, targetPair, time);
        }
    }

    private void analyzeSinglePair(List<MarketSnapshot> allSnapshots, String pair, String time) {
        // Filtrar solo los datos de esta moneda
        List<MarketSnapshot> pairData = allSnapshots.stream()
                .filter(s -> s.pair().equals(pair) || s.pair().equals(pair.replace("USDT", "-USDT")))
                .toList();

        if (pairData.size() < 2) return; // Necesitamos al menos 2 exchanges para bailar

        StringBuilder logBatch = new StringBuilder();

        for (MarketSnapshot source : pairData) {
            for (MarketSnapshot target : pairData) {
                // No arbitrar contra uno mismo
                if (source.exchange().equals(target.exchange())) continue;

                // COMPRA en Source (Ask) -> VENTA en Target (Bid)
                double buyPrice = source.ask();
                double sellPrice = target.bid();

                // --- 💰 CÁLCULO DE COSTOS REALES ---
                double buyFeeRate = source.fee();
                double sellFeeRate = target.fee();

                // Fee de Red (Gas): El Manager ya sabe que Mexc es barato (0.000213)
                String asset = pair.replace("USDT", "").replace("-", "");
                double withdrawQty = feeManager.getWithdrawalFee(source.exchange().toLowerCase(), asset);

                // Si falla API y tabla pesimista (-1), evitamos operar
                if (withdrawQty < 0) continue;

                // --- 🧮 EL CEREBRO ---
                ProfitCalculator.AnalysisResult result = profitCalculator.calculateCrossTrade(
                        capital, buyPrice, sellPrice, buyFeeRate, sellFeeRate, withdrawQty
                );

                double netProfit = result.netProfit();

                // ACTUALIZAR ESTADÍSTICAS
                if (netProfit > 0) positiveSpreads.increment();
                if (netProfit > bestNetProfit) {
                    bestNetProfit = netProfit;
                    bestPairLog = String.format("[%s] %s->%s ($%.2f)", pair, source.exchange(), target.exchange(), netProfit);
                }

                // VISUALIZACIÓN
                // Filtramos logs: Solo mostramos si perdemos poco (>-1.0) o ganamos.
                // Si perdemos $50, no llenamos la consola.
                if (netProfit > -1.5) {
                    String status = "💤";
                    if (netProfit > 0.05) status = "💰"; // Ganancia técnica (paga el café)
                    if (netProfit > 1.0) status = "🚀";  // Ganancia real

                    // Formato compacto para ver múltiples líneas
                    logBatch.append(String.format(
                            "[%s] %s %-6s %-7s->%-7s | Gap:$%6.2f | FeeRed:%.5f %s | NETO:$%6.2f | ROI:%.2f%%\n",
                            time, status, pair, source.exchange(), target.exchange(),
                            (sellPrice - buyPrice), withdrawQty, asset, netProfit, result.roiPercent()
                    ));
                }

                // 🔥 ALERTA DE EJECUCIÓN (Aquí iría la orden de compra real)
                if (result.isProfitable()) {
                    BotLogger.warn("🤑 OPORTUNIDAD VÁLIDA: " + pair + " en " + source.exchange() + " -> " + target.exchange());
                }
            }
        }

        if (!logBatch.isEmpty()) {
            System.out.print(logBatch.toString());
        }
    }

    public void stop() {
        scheduler.shutdown();
        pairSelector.stop();
        virtualExecutor.shutdown();

        StringBuilder finalReport = new StringBuilder();
        finalReport.append("\n==========================================\n");
        finalReport.append("🏁  R E S U M E N   O P E R A T I V O  🏁\n");
        finalReport.append("==========================================\n");
        finalReport.append(String.format("⏱️  Ciclos Escaneados : %d\n", totalScans.sum()));
        finalReport.append(String.format("📈  Spreads Positivos : %d\n", positiveSpreads.sum()));
        finalReport.append(String.format("🏆  Mejor Oportunidad : %s\n", bestPairLog));
        finalReport.append(String.format("💵  Máximo Neto Visto : $%.4f\n", bestNetProfit));
        finalReport.append("==========================================\n");

        BotLogger.info(finalReport.toString());
    }
}