package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 🧠 CEREBRO "OPPORTUNITY HUNTER" (MULTI-FACTOR)
 * Evolución: Ya no solo busca volatilidad. Busca la "Tormenta Perfecta":
 * Volatilidad (ATR) + Spread Ajustado + Liquidez Profunda.
 * Ejecución: Paralela (Virtual Threads) y Respetuosa con API Limits.
 */
public class DynamicPairSelector {

    private final ExchangeConnector connector;
    private final MarketListener marketListener;
    private final FeeManager feeManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // ⚡ Executor de Hilos Virtuales para I/O masivo sin bloqueo
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private static final double WEIGHT_LIQUIDITY = 0.2;

    // [NUEVO] 🛡️ REGLA DE ORO DEL ADVISOR
    private static final double MIN_NET_SPREAD_PERCENT = 0.5;
    private static final double ESTIMATED_TOTAL_FEE_PERCENT = 0.2;

    // 🌌 UNIVERSO EXPANDIDO (CANDIDATE PAIRS)
    // Lista amplia para filtrar. En el futuro, esto podría venir de un fetch "All Tickers".
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

    // Configuración de Pesos (Tuning del Advisor)
    private static final double WEIGHT_ATR = 0.4;
    private static final double WEIGHT_SPREAD = 0.4;

    public DynamicPairSelector(ExchangeConnector connector, MarketListener marketListener, FeeManager feeManager) {
        this.connector = connector;
        this.marketListener = marketListener;
        this.feeManager = feeManager;
    }

    public void start() {
        BotLogger.info("🧠 INICIANDO CEREBRO MULTI-FACTOR (Scan cada 5 min)...");
        // Ciclo de baja frecuencia (5 min) para análisis profundo
        scheduler.scheduleAtFixedRate(this::scanUniverse, 0, 5, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdown();
        virtualExecutor.shutdown();
        BotLogger.info("🧠 Cerebro detenido.");
    }

    private void scanUniverse() {
        BotLogger.info("🔭 ESCANEANDO UNIVERSO (" + CANDIDATE_PAIRS.size() + " pares)...");
        long startTime = System.currentTimeMillis();

        try {
            // 1. Lanzar tareas paralelas (Scatter)
            List<Callable<OpportunityScore>> tasks = CANDIDATE_PAIRS.stream()
                    .map(pair -> (Callable<OpportunityScore>) () -> calculateScore(pair))
                    .toList();

            // 2. Esperar resultados (Gather)
            List<Future<OpportunityScore>> futures = virtualExecutor.invokeAll(tasks);
            List<OpportunityScore> scores = new ArrayList<>();

            for (Future<OpportunityScore> f : futures) {
                try {
                    OpportunityScore result = f.get();
                    if (result != null && result.score > 0) {
                        scores.add(result);
                        // 🔥 ALERTA HOT PAIR
                        if (result.score > 0.8) {
                            BotLogger.sendTelegram("🔥 PAIR CALIENTE: "
                                    + result.pair + " (Score: "
                                    + String.format("%.2f", result.score) + ")");
                        }
                    }
                } catch (Exception ignored) {
                    // Fallo silencioso por par (Rate Limit o Red), no aborta el ciclo
                }
            }

            // 3. Selección de la Élite (Top 10)
            scores.sort(Comparator.comparingDouble(OpportunityScore::score).reversed());

            List<String> topTargets = scores.stream()
                    .limit(15) //  Top 15
                    .map(OpportunityScore::pair)
                    .collect(Collectors.toList());

            // Reporte de Inteligencia
            logAnalysis(scores, topTargets);

            // 4. Actualización Asíncrona
            if (!topTargets.isEmpty()) {
                marketListener.updateTargets(topTargets);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            BotLogger.error("🔥 Error crítico en Scan Universe: " + e.getMessage());
        } finally {
            BotLogger.info("⏱️ Scan completado en " + (System.currentTimeMillis() - startTime) + "ms");
        }
    }

    /**
     * Tarea atómica: Analiza un solo par con VISION DE RAYOS X (Order Book).
     * Cumple recomendación Advisor: ATR + AvgSpread + Liquidity Depth.
     */
    private OpportunityScore calculateScore(String pair) {
        try {
            String refExchange = "bybit_sub1";

            // 1. VOLATILIDAD (ATR) - Se mantiene igual
            List<double[]> candles = connector.fetchCandles(refExchange, pair, "1", 5);
            if (candles == null || candles.isEmpty()) return null;

            double atrSum = 0;
            double lastPrice = candles.get(candles.size() - 1)[2];
            for (double[] c : candles) atrSum += (c[0] - c[1]);

            double atrRaw = atrSum / candles.size();
            double atrPercent = (atrRaw / lastPrice) * 100.0;

            // 2. PROFUNDIDAD REAL (Advisor: "fetch order book bid/ask avg, liquidity sum")
            // Pedimos profundidad 10 para evaluar liquidez cercana
            ExchangeConnector.OrderBook book = connector.fetchOrderBook(refExchange, pair, 10);

            if (book.bids() == null || book.bids().isEmpty() || book.asks() == null || book.asks().isEmpty()) {
                return null; // Mercado vacío o error de lectura
            }

            // A. Spread Real & NETO
            double bestBid = book.bids().get(0)[0];
            double bestAsk = book.asks().get(0)[0];
            double spreadPercent = ((bestAsk - bestBid) / bestBid) * 100.0;

            // Consultamos el Fee REAL de Taker (Ya que el arbitraje agresivo suele ser Taker)
            // Multiplicamos por 2 (Entrada + Salida estimada)
            double realTakerFee = feeManager.getTradingFee(refExchange, pair, "TAKER");
            double totalRealFeePercent = (realTakerFee * 2) * 100.0; // Convertir a porcentaje

            // Cálculo preciso
            double netSpread = spreadPercent - totalRealFeePercent;

            // [FILTRO DE HIERRO]
            if (netSpread < MIN_NET_SPREAD_PERCENT) {
                return null;
            }

            // B. Liquidez (Depth USD sum bids/asks)
            // Sumamos cuánto dinero hay disponible para comprar/vender en los primeros niveles
            double liquidityUSD = 0.0;
            for (double[] b : book.bids()) liquidityUSD += (b[0] * b[1]); // Precio * Cantidad
            for (double[] a : book.asks()) liquidityUSD += (a[0] * a[1]);

            // 3. SCORING MULTI-FACTOR

            // Factor Liquidez: Normalizamos.
            // Si hay > $500k USD en el libro (top 10), es liquidez perfecta (Score 1.0).
            // Si hay $50k, es Score 0.1. Esto penaliza monedas "zombies".
            double liquidityScore = Math.min(liquidityUSD / 500_000.0, 1.0);

            // Factor Spread: Ahora premiamos el spread amplio (porque es arbitrage)
            // Si el spread neto es 2%, score es 1.0 (máximo).
            double spreadScore = Math.min(netSpread / 2.0, 1.0);
            if (spreadScore < 0) spreadScore = 0;

            // Factor Volatilidad: Buscamos movimiento pero no caos absoluto
            double volScore = Math.min(atrPercent, 5.0) / 5.0; // Cap en 5% para normalizar

            // FÓRMULA DEL ADVISOR
            double finalScore = (volScore * WEIGHT_ATR) +
                    (spreadScore * WEIGHT_SPREAD) +
                    (liquidityScore * WEIGHT_LIQUIDITY);

            // Filtros de Calidad Mínima (Safety Checks)
            if (atrPercent < 0.1) return null;      // Muy quieto
            if (liquidityUSD < 10_000) return null; // Peligrosamente ilíquido (<$10k en libro)

            return new OpportunityScore(pair, finalScore, atrPercent, spreadPercent);

        } catch (Exception e) {
            return null;
        }
    }

    private void logAnalysis(List<OpportunityScore> scores, List<String> top) {
        StringBuilder sb = new StringBuilder("\n📊 REPORTE DE INTELIGENCIA DE MERCADO:\n");
        sb.append("   Candidatos Analizados: ").append(scores.size()).append("\n");
        sb.append("   👑 ELITE SELECCIONADA (Top ").append(top.size()).append("):\n");

        for (int i = 0; i < Math.min(5, scores.size()); i++) {
            OpportunityScore s = scores.get(i);
            sb.append(String.format("   #%d %-8s | Score: %5.2f | ATR: %4.2f%% | Spread: %4.2f%%\n",
                    i+1, s.pair, s.score, s.atrPercent, s.spreadPercent));
        }
        BotLogger.info(sb.toString());
    }

    // Record interno para pasar datos
    private record OpportunityScore(String pair, double score, double atrPercent, double spreadPercent) {}
}