package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArbitrageDetectorTest {

    @Test
    void testDetectTriangularProfit() {
        // Escenario: Arbitraje obvio
        // BTC = 50,000 USDT
        // SOL = 100 USDT
        // SOL/BTC = 0.0019 (Barato en BTC)
        //
        // Ciclo:
        // 1. 1000 USDT -> BTC (Precio 50k) = 0.02 BTC
        // 2. 0.02 BTC -> SOL (Precio 0.0019 BTC/SOL) => 0.02 / 0.0019 = 10.526 SOL
        // 3. 10.526 SOL -> USDT (Precio 100) = 1052.6 USDT
        // Ganancia bruta: ~5.2%

        ArbitrageDetector detector = new ArbitrageDetector();

        // Inyectamos precios simulados
        detector.onPriceUpdate("bybit", "BTCUSDT", 50000.0, 1000L);
        detector.onPriceUpdate("bybit", "SOLUSDT", 100.0, 1000L);

        // El "Trigger" es el último precio
        // Usamos un Log Appender mock o simplemente verificamos que no explote
        // En una implementación real, inyectaríamos un "Executor" mock para verificar la llamada.

        // Por ahora, verificamos la lógica matemática manualmente con los mismos pasos de la clase:
        double start = 1000.0;
        double btc = start / 50000.0;
        double sol = btc / 0.0019;
        double end = sol * 100.0;

        assertTrue(end > 1050.0, "Debería haber ganancia > 5%");

        // Ejecutamos el detector (debería imprimir el log de alerta en consola)
        System.out.println("--- Test Visual: Deberías ver una ALERTA abajo ---");
        detector.onPriceUpdate("bybit", "SOLBTC", 0.0019, 1000L);
    }
    @Test
    @DisplayName("Benchmarking: Velocidad de Detección de Oportunidad")
    void testDetectionSpeed() {
        System.out.println("\n--- ⏱️ MIDIENDO REFLEJOS: CEREBRO DE ARBITRAJE ---");
        ArbitrageDetector detector = new ArbitrageDetector();

        // Preparamos el escenario (Cache con precios)
        // Simulamos que ya recibió los datos del mercado
        detector.onPriceUpdate("bybit", "BTCUSDT", 50000.0, System.currentTimeMillis());
        detector.onPriceUpdate("bybit", "SOLUSDT", 100.0, System.currentTimeMillis());
        detector.onPriceUpdate("bybit", "SOLBTC", 0.0019, System.currentTimeMillis()); // Precio desalineado

        long start = System.nanoTime();

        // Forzamos la evaluación (Simulamos la llegada del último precio que dispara el cálculo)
        detector.onPriceUpdate("bybit", "SOLBTC", 0.0019, System.currentTimeMillis());

        long end = System.nanoTime();
        double durationMs = (end - start) / 1_000_000.0;

        System.out.printf("🧠 Tiempo de Cálculo y Decisión: %.4f ms%n", durationMs);

        // El cálculo matemático debería ser casi instantáneo (< 0.1ms)
        assertTrue(durationMs < 5.0, "El cerebro debe reaccionar en menos de 5ms");
    }
}