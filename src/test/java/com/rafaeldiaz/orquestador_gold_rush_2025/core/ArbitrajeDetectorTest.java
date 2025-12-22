package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner.ArbitrageDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArbitrageDetectorTest {

    @Mock
    private ExchangeConnector mockConnector;

    private ArbitrageDetector detector;

    @BeforeEach
    void setUp() {
        // ✅ CORRECCIÓN: Inyectamos el conector (Mock) al constructor
        detector = new ArbitrageDetector(mockConnector);
    }

    @Test
    @DisplayName("Triangular Profit: USDT -> SOL -> BTC -> USDT")
    void testDetectTriangularProfit() {
        // --- ESCENARIO DE ARBITRAJE ---
        // 1. Comprar SOL con USDT a $100.00
        // 2. Vender SOL por BTC a 0.0022 BTC/SOL (Precio inflado en BTC)
        // 3. Vender BTC por USDT a $50,000.00
        // Matemática: $100 -> 1 SOL -> 0.0022 BTC -> $110.00 (Profit Bruto: 10%)

        // Stubbing de Fees: Simulamos un fee de 0.1% por trade para Bybit
        when(mockConnector.fetchDynamicTradingFee(anyString(), anyString()))
                .thenReturn(new double[]{0.001, 0.001});

        // Cargamos los precios en el orden lógico de ArbitrageDetector.java
        detector.onPriceUpdate("bybit", "BTCUSDT", 50000.0, 1000L);
        detector.onPriceUpdate("bybit", "SOLBTC", 0.0022, 1000L);

        System.out.println("--- 🔺 DISPARANDO CÁLCULO TRIANGULAR ---");
        // Este último precio termina en "USDT", lo que dispara la lógica de detección
        detector.onPriceUpdate("bybit", "SOLUSDT", 100.0, 1000L);

        // Si el log imprime "🔺 TRIÁNGULO [SOL]: Bruto: 10.000%..." ¡VAMOS POR EL ORO!
    }

    @Test
    @DisplayName("⏱️ Benchmarking: Reflejos del Cerebro")
    void testDetectionSpeed() {
        System.out.println("\n--- ⏱️ MIDIENDO REFLEJOS ---");

        // Pablamos el caché para que el cálculo sea puro
        detector.onPriceUpdate("bybit", "BTCUSDT", 50000.0, System.currentTimeMillis());
        detector.onPriceUpdate("bybit", "SOLUSDT", 100.0, System.currentTimeMillis());

        long start = System.nanoTime();

        // La llegada de este precio dispara todo el análisis matemático y la decisión
        detector.onPriceUpdate("bybit", "SOLBTC", 0.0022, System.currentTimeMillis());

        long end = System.nanoTime();
        double durationMs = (end - start) / 1_000_000.0;

        System.out.printf("🧠 Tiempo de Reacción del Cerebro: %.4f ms%n", durationMs);

        // La meta es < 5ms para ser competitivos
        assertTrue(durationMs < 5.0, "El cerebro debe reaccionar en menos de 5ms");
    }
}