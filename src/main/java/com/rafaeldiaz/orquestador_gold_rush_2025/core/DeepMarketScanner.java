package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 🛰️ DEEP MARKET SCANNER (VERSIÓN REALISTA 10/10)
 * Muestra el Capital Mínimo exacto o "IMPOSIBLE" si la matemática no cuadra.
 */
public class DeepMarketScanner {

    private final ExchangeConnector connector;
    private final FeeManager feeManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Virtual Threads para el cálculo matemático masivo
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // 🔧 CONFIGURACIÓN
    private final double capital = 224.0;
    private boolean dryRun = true;

    private final List<String> exchanges = List.of("binance", "bybit", "mexc", "kucoin");
    // Agregamos más activos para ver más acción
    private final List<String> huntingGrounds = List.of("SOL", "XRP", "DOGE", "AVAX", "PEPE", "USDT", "BTC", "ADA", "LINK"
            ,"SUI", "WIF", "FET", "RNDR", "APT", "SEI", "INJ","TIA","BONK","FLOKI");

    // METRICS
    private final DoubleAdder totalPotentialProfit = new DoubleAdder();
    private final AtomicLong tradesCount = new AtomicLong(0);
    private String bestOpportunityLog = "N/A";

    // FORMATOS
    private final DecimalFormat dfMoney = new DecimalFormat("#,##0.00"); // Formato con miles ($1,200.00)
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    public DeepMarketScanner(ExchangeConnector connector) {
        this.connector = connector;
        this.feeManager = new FeeManager(connector);
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public long getTradesCount() {
        return tradesCount.get();
    }

    public double getTotalPotentialProfit() {
        return totalPotentialProfit.sum();
    }

    public String getBestOpportunityLog() {
        return bestOpportunityLog.equals("N/A") ? "Buscando..." : bestOpportunityLog;
    }

    public void startOmniScan(int durationMinutes) {
        BotLogger.info("⚡ INICIANDO DEEP SCAN REALISTA (Batch Mode: ON)");
        BotLogger.info("💰 Capital: $" + capital + " | Exchanges: " + exchanges.size());

        printHeader();

        scheduler.scheduleAtFixedRate(this::sendTelegramReport, 5, 5, TimeUnit.MINUTES);

        Thread.ofVirtual().start(() -> {
            long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
            while (System.currentTimeMillis() < endTime) {
                scanFullMatrixBatchOptimized();
                try { Thread.sleep(3000); } catch (InterruptedException e) { break; }
            }
            finalizeScan();
        });
    }

    private void scanFullMatrixBatchOptimized() {
        // 1. PRE-FETCH BATCH
        Map<String, Map<String, Double>> marketData = new ConcurrentHashMap<>();

        exchanges.parallelStream().forEach(ex -> {
            try {
                Map<String, Double> prices = connector.fetchAllPrices(ex);
                if (!prices.isEmpty()) {
                    marketData.put(ex, prices);
                }
            } catch (Exception e) {
                // Silent fail for speed
            }
        });

        // 2. PROCESAMIENTO
        List<Callable<Void>> tasks = new ArrayList<>();
        for (String asset : huntingGrounds) {
            tasks.add(() -> {
                analyzeAssetInMemory(asset, marketData);
                return null;
            });
        }

        try {
            virtualExecutor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void analyzeAssetInMemory(String asset, Map<String, Map<String, Double>> marketData) {
        String pair = asset + "USDT";
        for (String buyEx : exchanges) {
            for (String sellEx : exchanges) {
                if (buyEx.equals(sellEx)) continue;

                double buyPrice = marketData.getOrDefault(buyEx, Map.of()).getOrDefault(pair, -1.0);
                double sellPrice = marketData.getOrDefault(sellEx, Map.of()).getOrDefault(pair, -1.0);

                if (buyPrice <= 0 || sellPrice <= 0) continue;

                calculateOpportunity(asset, buyEx, sellEx, buyPrice, sellPrice);
            }
        }
    }

    private void calculateOpportunity(String asset, String buyEx, String sellEx, double buyPrice, double sellPrice) {
        try {
            double spreadBruto = sellPrice - buyPrice;
            if (spreadBruto <= 0) return;

            // --- COSTOS ---
            double tradingFees = (capital * 0.001) + ((capital / buyPrice) * sellPrice * 0.001);

            double withdrawFeeNative = feeManager.getWithdrawalFee(buyEx, asset);
            if (withdrawFeeNative < 0) {
                withdrawFeeNative = switch (buyEx) {
                    case "mexc" -> 0.0002;
                    case "binance" -> 0.001;
                    default -> 0.01;
                };
            }
            double networkFeeUsd = withdrawFeeNative * sellPrice;
            double totalCosts = tradingFees + networkFeeUsd;

            // --- RESULTADOS ---
            double ratio = sellPrice / buyPrice;
            double grossProfit = (ratio - 1) * capital;
            double netProfit = grossProfit - totalCosts;

            // --- CÁLCULO DE MIN CAP (EL PUNTO CRÍTICO) ---
            // Margen operativo por dólar (después de pagar 0.2% de trading fees)
            // Formula: (PrecioVenta / PrecioCompra - 1) - (0.002 aprox)
            double marginPerDollar = (ratio - 1) - (0.001 * (1 + ratio));

            double minCapRequired = -1.0;
            if (marginPerDollar > 0) {
                // Si el margen operativo es positivo, calculamos cuánto capital cubre el Fee Fijo de Red
                minCapRequired = networkFeeUsd / marginPerDollar;
            } else {
                // Si el margen es negativo, es IMPOSIBLE ganar dinero (Trading Fees > Spread)
                minCapRequired = -1.0;
            }

            // --- FILTRADO VISUAL ---
            // Mostramos si es rentable o si perdemos poco (para ver cuán cerca estamos)
            if (netProfit > -0.50) {
                printRow(asset, buyEx, sellEx, spreadBruto, minCapRequired, netProfit);
            }

            if (netProfit > 0) {
                totalPotentialProfit.add(netProfit);
                tradesCount.incrementAndGet();
                if (netProfit > 1.0 && minCapRequired < capital) {
                    String hotMsg = String.format("🔥 HOT: %s (%s->%s) +$%s", asset, buyEx, sellEx, dfMoney.format(netProfit));
                    BotLogger.sendTelegram(hotMsg);
                    bestOpportunityLog = hotMsg;
                }
            }

        } catch (Exception e) { }
    }

    private synchronized void printRow(String asset, String buyEx, String sellEx, double gap, double minCap, double net) {
        String time = LocalTime.now().format(timeFmt);
        String color = "\u001B[33m"; // Amarillo (Cerca)
        String icon = "⚠️";
        String minCapStr;

        // LÓGICA DE VISUALIZACIÓN DE CAPITAL
        if (minCap < 0) {
            minCapStr = "IMPOSIBLE"; // Matemáticamente inviable (Fees > Spread)
            color = "\u001B[31m"; // Rojo
            icon = "⛔";
        } else {
            minCapStr = "$" + dfMoney.format(minCap);
            if (net > 0) {
                color = "\u001B[32m"; // Verde
                icon = "💰";
                if (minCap < capital) icon = "💎"; // Joya accesible
            } else {
                // Perdemos dinero, pero es matemáticamente posible si tuviéramos más capital
                color = "\u001B[33m";
                icon = "🤏";
            }
        }

        String reset = "\u001B[0m";

        // Tabla alineada (Min Cap ahora tiene 11 espacios)
        System.out.printf("║ %s ║ %-8s ║ %-16s ║ %s$%7.4f %s ║ %11s ║ %s%s$%6.2f%s ║%n",
                time, asset,
                buyEx.toUpperCase().substring(0,3) + "->" + sellEx.toUpperCase().substring(0,3),
                (gap > 0 ? "+" : ""), gap, (gap > 0 ? " " : ""),
                minCapStr, color, icon, net, reset
        );
    }

    private void printHeader() {
        System.out.println("\n╔══════════╦══════════╦══════════════════╦════════════════╦═════════════╦══════════════╗");
        System.out.println("║   HORA   ║  ACTIVO  ║    RUTA (A->B)   ║    GAP BRUTO   ║   MIN CAP   ║   NETO ($)   ║");
        System.out.println("╠══════════╬══════════╬══════════════════╬════════════════╬═════════════╬══════════════╣");
    }

    private void sendTelegramReport() {
        long count = tradesCount.get();
        double total = totalPotentialProfit.sum();
        if (count == 0) return;
        String msg = String.format("\n🐯 REPORTE REALISTA (5 MIN):\n📊 Trades Vistos: %d\n💵 Potencial: $%.2f\n🏆 Mejor: %s",
                count, total, bestOpportunityLog);
        BotLogger.sendTelegram(msg);
    }

    private void finalizeScan() {
        scheduler.shutdown();
        virtualExecutor.shutdown();
        System.out.println("╚══════════╩══════════╩══════════════════╩════════════════╩═════════════╩══════════════╝");
    }
}