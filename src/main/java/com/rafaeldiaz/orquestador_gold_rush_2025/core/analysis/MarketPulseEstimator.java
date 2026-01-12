package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 🩺 MARKET PULSE ESTIMATOR (MODO BALLENA)
 * Incluye cálculo de "Capital Mínimo Viable" para filtrar oportunidades reales.
 */
public class MarketPulseEstimator {
    private final ExchangeConnector connector;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final FeeManager feeManager;

    // 🔧 CONFIGURACIÓN DE VUELO
    private final double capital = 224.0;
    private final String BUY_EXCHANGE = "mexc";   // Origen barato
    private final String SELL_EXCHANGE = "bybit"; // Destino líquido
    private final String PAIR = "SOLUSDT";
    private final String ASSET = "SOL";

    // 📊 ESTADÍSTICAS
    private final double[] thresholds = {0.05, 0.2, 0.5, 1.0};
    private final Map<Double, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final DoubleAdder totalNetProfit = new DoubleAdder();

    // FORMATOS VISUALES
    private final DecimalFormat dfPrice = new DecimalFormat("0.00");
    private final DecimalFormat dfMoney = new DecimalFormat("0.00");
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    public MarketPulseEstimator(ExchangeConnector connector) {
        this.connector = connector;
        this.feeManager = new FeeManager(connector);
        for (double t : thresholds) hits.put(t, new AtomicInteger(0));
    }

    public void start15MinuteDrill() {
        BotLogger.info("🚧 INICIANDO TELEMETRÍA AVANZADA: " + BUY_EXCHANGE.toUpperCase() + " -> " + SELL_EXCHANGE.toUpperCase());

        // Cabecera de la tabla (Ajustada para la nueva columna)
        System.out.println("\n╔══════════╦══════════════════════════╦══════════════╦══════════════╦══════════════╦══════════════╗");
        System.out.println("║   HORA   ║   PRECIOS (BUY -> SELL)  ║  GAP BRUTO   ║   COSTOS     ║  MIN CAP ($) ║   NETO ($)   ║");
        System.out.println("╠══════════╬══════════════════════════╬══════════════╬══════════════╬══════════════╬══════════════╣");

        scheduler.scheduleAtFixedRate(this::sendProgressReport, 5, 5, TimeUnit.MINUTES);

        Thread.ofVirtual().start(() -> {
            long endTime = System.currentTimeMillis() + (15 * 60 * 1000);
            while (System.currentTimeMillis() < endTime) {
                analyzeRealFlow();
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
            }
            finalizeDrill();
        });
    }

    private void analyzeRealFlow() {
        try {
            // 1. OBTENER PRECIOS
            double buyPrice = connector.fetchPrice(BUY_EXCHANGE, PAIR);
            double sellPrice = connector.fetchPrice(SELL_EXCHANGE, PAIR);

            if (buyPrice <= 0 || sellPrice <= 0) return;

            // 2. CÁLCULO DE COSTOS Y MARGEN
            double spreadBruto = (sellPrice - buyPrice);

            // Fee Red Fijo (Fallback a 0.0002 SOL ~= $0.02 si falla API)
            double withdrawFeeNative = feeManager.getTradingFee(BUY_EXCHANGE, ASSET, "MAKER");
            if (withdrawFeeNative < 0) withdrawFeeNative = 0.0002;
            double networkFeeUsd = withdrawFeeNative * sellPrice;

            // Fees Trading Variables (0.1% compra + 0.1% venta)
            // Margen por Dólar Invertido = (% Gap) - (% Trading Fees)
            // Formula exacta: (Sell/Buy - 1) - (0.001 * (1 + Sell/Buy))
            double ratio = sellPrice / buyPrice;
            double marginPerDollar = (ratio - 1) - (0.001 * (1 + ratio));

            // Ganancia Neta con NUESTRO capital
            double tradingFeesTotal = capital * 0.001 * (1 + ratio);
            double grossProfit = (ratio - 1) * capital;
            double netProfit = grossProfit - tradingFeesTotal - networkFeeUsd;

            // 3. CÁLCULO DE CAPITAL MÍNIMO (Break-Even Point)
            // 0 = (MarginPerDollar * MinCap) - NetworkFee
            // MinCap = NetworkFee / MarginPerDollar
            double minCapRequired = (marginPerDollar > 0) ? (networkFeeUsd / marginPerDollar) : -1;

            // 4. VISUALIZACIÓN
            String time = LocalTime.now().format(timeFmt);
            String color = "\u001B[31m"; // Rojo
            String icon = "📉";
            String minCapStr = "---";

            if (marginPerDollar > 0) {
                minCapStr = "$" + dfMoney.format(minCapRequired);
                // Si nuestro capital es mayor al mínimo, estamos en verde
                if (capital > minCapRequired) {
                    color = "\u001B[32m"; // Verde
                    icon = "💰";
                    totalNetProfit.add(netProfit);
                    for (double t : thresholds) if (netProfit >= t) hits.get(t).incrementAndGet();
                } else {
                    color = "\u001B[33m"; // Amarillo (Rentable pero falta capital)
                    icon = "⚠️";
                }
            }

            String reset = "\u001B[0m";
            double totalCosts = tradingFeesTotal + networkFeeUsd;

            // Imprimir Fila
            System.out.printf("║ %s ║ %s: %s -> %s: %s ║ %s$%6.2f %s ║  $%4.2f      ║ %12s ║ %s%s$%6.4f%s ║%n",
                    time,
                    BUY_EXCHANGE.substring(0,3).toUpperCase(), dfPrice.format(buyPrice),
                    SELL_EXCHANGE.substring(0,3).toUpperCase(), dfPrice.format(sellPrice),
                    (spreadBruto > 0 ? "+" : ""), spreadBruto, (spreadBruto > 0 ? " " : ""),
                    totalCosts,
                    minCapStr,
                    color, icon, netProfit, reset
            );

        } catch (Exception e) {
            System.out.println("❌ Error lectura: " + e.getMessage());
        }
    }

    private void sendProgressReport() {
        StringBuilder sb = new StringBuilder("\n📊 REPORTE TELEGRAM (5 MIN):\n");
        sb.append(String.format("ruta: %s -> %s\n", BUY_EXCHANGE, SELL_EXCHANGE));
        sb.append(String.format("💵 Acumulado Potencial: $%.4f USDT\n", totalNetProfit.sum()));
        new TreeMap<>(hits).forEach((t, count) -> {
            if (count.get() > 0) sb.append(String.format("🔹 Oportunidades >$%.2f: %d\n", t, count.get()));
        });
        BotLogger.sendTelegram(sb.toString());
    }

    private void finalizeDrill() {
        scheduler.shutdown();
        System.out.println("╚══════════╩══════════════════════════╩══════════════╩══════════════╩══════════════╩══════════════╝");
        String finalMsg = String.format("\n🏁 ATERRIZAJE COMPLETADO\n🏆 Total Neto Capturable: $%.4f USDT", totalNetProfit.sum());
        BotLogger.sendTelegram(finalMsg);
        System.out.println(finalMsg);
    }
}