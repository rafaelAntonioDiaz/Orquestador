package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.*;
import java.util.concurrent.*;

/**
 * 🧠 CEREBRO "DUAL HEARTBEAT" (JAVA 25 OPTIMIZED EDITION)
 * Arquitectura:
 * 1. WATCHDOG (High-Freq): I/O Bloqueante delegada a Virtual Threads.
 * 2. RADAR (Low-Freq): Structured Concurrency para paralelismo masivo.
 * * Optimizaciones Java 25:
 * - Sequenced Collections (getFirst/getLast).
 * - Virtual Thread per Task Executor (Project Loom).
 * - ZGC Friendly (Short-lived Records).
 */
public class DynamicPairSelector {

    private final ExchangeConnector connector;
    private final MarketListener marketListener;
    private final FeeManager feeManager;
    private final PortfolioHealthManager cfo;

    // 🕒 SCHEDULER: Usa hilos de plataforma (OS Threads) solo para cronometrar.
    // Mantenemos el pool pequeño (2) porque su único trabajo es despertar y delegar.
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, Thread.ofPlatform().factory());

    // 🌌 UNIVERSO DE OBSERVACIÓN
    private static final List<String> CANDIDATE_PAIRS = List.of(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "BNBUSDT",
            "DOGEUSDT", "ADAUSDT", "AVAXUSDT", "SHIBUSDT", "DOTUSDT",
            "LINKUSDT", "TRXUSDT", "MATICUSDT", "LTCUSDT", "BCHUSDT",
            "UNIUSDT", "ATOMUSDT", "XLMUSDT", "NEARUSDT", "INJUSDT",
            "APTUSDT", "FILUSDT", "HBARUSDT", "LDOUSDT", "ARBUSDT",
            "VETUSDT", "QNTUSDT", "OPUSDT", "MKRUSDT", "GRTUSDT",
            "RNDRUSDT", "AAVEUSDT", "ALGOUSDT", "STXUSDT", "SANDUSDT",
            "IMXUSDT", "EOSUSDT", "THETAUSDT", "XTZUSDT", "AXSUSDT",
            "MANAUSDT", "FTMUSDT", "SUIUSDT", "PEPEUSDT", "WIFUSDT",
            "BONKUSDT", "SEIUSDT", "ORDIUSDT", "FETUSDT", "FLOKIUSDT"
    );

    // Pesos del Radar
    private static final double WEIGHT_ATR = 0.4;
    private static final double WEIGHT_SPREAD = 0.4;
    private static final double WEIGHT_LIQUIDITY = 0.2;

    public DynamicPairSelector(ExchangeConnector connector, MarketListener marketListener,
                               FeeManager feeManager, PortfolioHealthManager cfo) {
        this.connector = connector;
        this.marketListener = marketListener;
        this.feeManager = feeManager;
        this.cfo = cfo;
    }

    public void start() {
        BotLogger.info("🧠 CEREBRO DUAL ACTIVADO: Optimizaciones Java 25 cargadas.");

        // 💓 CICLO 1: WATCHDOG (30s)
        // Patrón: "Fire-and-Forget Virtual Thread".
        // El scheduler dispara el evento, pero el trabajo pesado se va a un hilo virtual.
        scheduler.scheduleWithFixedDelay(() ->
                        Thread.ofVirtual().name("Watchdog-Worker").start(this::executeWatchdogRoutine),
                0, 30, TimeUnit.SECONDS
        );

        // 🔭 CICLO 2: RADAR (5m)
        // Patrón: "Structured Concurrency Scope".
        scheduler.scheduleWithFixedDelay(() ->
                        Thread.ofVirtual().name("Radar-Worker").start(this::executeRadarRoutine),
                1, 5, TimeUnit.MINUTES
        );
    }

    public void stop() {
        scheduler.shutdownNow();
        BotLogger.info("🧠 Cerebro detenido.");
    }

    // =========================================================================
    // 🐕 RUTINA 1: WATCHDOG (Gestión de Inventario)
    // =========================================================================
    private void executeWatchdogRoutine() {
        try {
            // El CFO hace llamadas de red (fetchBalances). Al correr en un hilo virtual,
            // si la API tarda, el hilo se "desmonta" del carrier thread, permitiendo
            // que la CPU siga trabajando en otras cosas. 100% Non-Blocking I/O implícito.
            List<String> liveAssets = cfo.discoverTradableAssets();

            if (!liveAssets.isEmpty()) {
                marketListener.updateTargets(liveAssets);
            }
        } catch (Exception e) {
            BotLogger.error("🐕 Error en Watchdog: " + e.getMessage());
        }
    }

    // =========================================================================
    // 📡 RUTINA 2: RADAR (Inteligencia de Mercado)
    // =========================================================================
    private void executeRadarRoutine() {
        BotLogger.info("🔭 RADAR: Escaneando Universo (" + CANDIDATE_PAIRS.size() + " activos)...");
        long start = System.currentTimeMillis();

        // 🚀 STRUCTURED CONCURRENCY (Implicit Scope)
        // try-with-resources garantiza que el Executor se cierre y limpie al terminar el bloque.
        try (var scope = Executors.newVirtualThreadPerTaskExecutor()) {

            // 1. Scatter (Lanzamiento Masivo)
            List<Callable<OpportunityScore>> tasks = CANDIDATE_PAIRS.stream()
                    .map(pair -> (Callable<OpportunityScore>) () -> analyzeMarketCandidate(pair))
                    .toList();

            // 2. Gather (Recolección Bloqueante Virtual)
            // invokeAll bloquea el hilo virtual "Radar-Worker", pero NO un hilo del sistema.
            List<Future<OpportunityScore>> futures = scope.invokeAll(tasks);

            List<OpportunityScore> scores = new ArrayList<>();

            for (Future<OpportunityScore> f : futures) {
                try {
                    // get() lanzaría excepción si la tarea falló, lo capturamos abajo
                    OpportunityScore res = f.get();
                    if (res != null && res.score > 0) {
                        scores.add(res);
                        // 🔥 Hot Alert
                        if (res.score > 0.85) {
                            BotLogger.sendTelegram("🚀 RADAR DETECT: " + res.pair()
                                    + " | Score: " + String.format("%.2f", res.score()));
                        }
                    }
                } catch (ExecutionException | InterruptedException ignored) {
                    // Fail-Safe: Si un par falla (timeout/error), ignoramos y seguimos.
                }
            }

            // 3. Reporting
            // Usamos List.sort directo (Java 8+)
            scores.sort(Comparator.comparingDouble(OpportunityScore::score).reversed());

            // Stream con limit para reporte
            logIntelligenceReport(scores.stream().limit(10).toList());

        } catch (Exception e) {
            BotLogger.error("🔭 Error en Radar: " + e.getMessage());
        } finally {
            BotLogger.info("⏱️ Radar finalizado en " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    /**
     * 🔬 ANÁLISIS DE CANDIDATO
     */
    private OpportunityScore analyzeMarketCandidate(String pair) {
        try {
            String refExchange = BotConfig.ADVISOR_REF_EXCHANGE;

            // A. Volatilidad (ATR 1m)
            List<double[]> candles = connector.fetchCandles(refExchange, pair, "1m", 5);

            // ✅ JAVA 21+: Sequenced Collections check
            if (candles == null || candles.isEmpty()) return null;

            // Uso de getLast() en lugar de get(size-1) -> Más limpio y seguro
            double[] lastCandle = candles.getLast();

            // Fallback índice de cierre (4=Close standard, 2=High fallback)
            double lastPrice = lastCandle.length > 4 ? lastCandle[4] : lastCandle[2];

            double atrSum = 0;
            for(double[] c : candles) atrSum += (c[2] - c[3]); // High - Low
            double atrPercent = ((atrSum/candles.size()) / lastPrice) * 100.0;

            if (atrPercent < 0.15) return null; // Dead market check

            // B. OrderBook Analysis
            ExchangeConnector.OrderBook book = connector.fetchOrderBook(refExchange, pair, 10);
            if (book == null || book.bids().isEmpty()) return null;

            // ✅ Sequenced Collections: Acceso al mejor Bid/Ask
            double bestBid = book.bids().getFirst()[0];
            double bestAsk = book.asks().getFirst()[0];

            double spreadPercent = ((bestAsk - bestBid) / bestBid) * 100.0;

            double fee = feeManager.getTradingFee(refExchange, pair, "TAKER");
            double netSpread = spreadPercent - (fee * 200.0);

            // C. Liquidez
            double liqUSD = 0;
            for(double[] b : book.bids()) liqUSD += b[0]*b[1];

            if (liqUSD < 15_000) return null;

            // Scoring
            double sVol = Math.min(atrPercent, 5.0) / 5.0;
            double sSpread = Math.min(Math.max(netSpread, 0), 2.0) / 2.0;
            double sLiq = Math.min(liqUSD / 500_000.0, 1.0);

            double finalScore = (sVol * WEIGHT_ATR) + (sSpread * WEIGHT_SPREAD) + (sLiq * WEIGHT_LIQUIDITY);

            return new OpportunityScore(pair, finalScore, atrPercent, spreadPercent);

        } catch (Exception e) {
            return null; // El scope general maneja los nulos
        }
    }

    private void logIntelligenceReport(List<OpportunityScore> top) {
        StringBuilder sb = new StringBuilder("\n📡 RADAR DE OPORTUNIDADES (Para Humano):\n");
        if (top.isEmpty()) {
            sb.append("   (El mercado está dormido, sin candidatos claros)\n");
        } else {
            for (int i = 0; i < top.size(); i++) {
                OpportunityScore s = top.get(i);
                sb.append(String.format("   💡 #%d %-8s | Score: %4.2f | Spread: %5.2f%% | Vol: %4.2f%%\n",
                        i + 1, s.pair, s.score, s.spreadPercent, s.atrPercent));
            }
            sb.append("\n   👉 Si inyectas saldo en estos activos, el Watchdog los activará en 30s.\n");
        }
        BotLogger.info(sb.toString());
    }

    // ✅ JAVA 16+ RECORD: Inmutabilidad nativa para ZGC
    private record OpportunityScore(String pair, double score, double atrPercent, double spreadPercent) {}
}