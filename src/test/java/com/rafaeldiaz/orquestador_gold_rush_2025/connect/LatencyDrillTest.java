package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector.OrderBook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🏆 PRUEBA DE LATENCIA TOTAL (GOLD RUSH 2025) 🏆
 * Mide tiempos reales de operaciones críticas.
 * Ejecuta múltiples iteraciones para promedios reales.
 */
public class LatencyDrillTest {

    private static final int ITERATIONS = 10; // Ajusta para más precisión
    private static final String TEST_PAIR = "SOLUSDT"; // Par líquido
    private static final String ASSET = "USDT";
    private static final DecimalFormat df = new DecimalFormat("0.00");

    private final String[] exchanges = {"binance", "bybit_sub1", "mexc", "kucoin"};

    @Test
    @DisplayName("🚀 LATENCY DRILL: Medición Completa de Operaciones")
    void testFullLatencyDrill() {
        System.out.println("\n=== 🚀 INICIANDO LATENCY DRILL (" + ITERATIONS + " iteraciones) ===");
        ExchangeConnector connector = new ExchangeConnector();

        for (String ex : exchanges) {
            System.out.println("\n--- EXCHANGE: " + ex.toUpperCase() + " ---");

            long[] balanceTimes = new long[ITERATIONS];
            long[] pricesTimes = new long[ITERATIONS];
            long[] bookTimes = new long[ITERATIONS];

            for (int i = 0; i < ITERATIONS; i++) {
                // 1. Balance (autenticado)
                long start = System.nanoTime();
                double balance = connector.fetchBalance(ex, ASSET);
                balanceTimes[i] = System.nanoTime() - start;

                // 2. Batch Prices (público, rápido)
                start = System.nanoTime();
                Map<String, Double> prices = connector.fetchAllPrices(ex);
                pricesTimes[i] = System.nanoTime() - start;

                // 3. OrderBook (crítico, profundidad 10)
                start = System.nanoTime();
                OrderBook book = connector.fetchOrderBook(ex, TEST_PAIR, 10);
                bookTimes[i] = System.nanoTime() - start;

                System.out.printf("Iter %d: Balance %.2fms | Prices %.2fms | Book %.2fms%n",
                        i+1,
                        balanceTimes[i]/1e6, pricesTimes[i]/1e6, bookTimes[i]/1e6);
            }

            // Promedios
            System.out.println("RESUMEN " + ex.toUpperCase() + ":");
            System.out.println("Balance Avg: " + df.format(avg(balanceTimes)/1e6) + " ms");
            System.out.println("Prices Batch Avg: " + df.format(avg(pricesTimes)/1e6) + " ms");
            System.out.println("OrderBook Avg: " + df.format(avg(bookTimes)/1e6) + " ms");
        }

        assertTrue(true); // Siempre pasa, es diagnóstico
    }

    private double avg(long[] times) {
        long sum = 0;
        for (long t : times) sum += t;
        return (double) sum / times.length;
    }
}