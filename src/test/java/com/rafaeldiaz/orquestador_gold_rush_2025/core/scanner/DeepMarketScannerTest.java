package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.SymbolCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DeepMarketScannerTest {

    @Mock
    private ExchangeConnector mockConnector;
    @Mock
    private ExecutionCoordinator mockCoordinator;

    private DeepMarketScanner scanner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        scanner = new DeepMarketScanner(mockConnector, mockCoordinator);
        scanner.setDryRun(true);
    }


    @Test
    @DisplayName("📐 MATH: Detecta Arbitraje Triangular (>1.0 + Spread)")
    void shouldDetectTriangularOpportunity() throws Exception {
        // 1. Limpieza y Sincronización de Símbolos
        String p1 = SymbolCache.get("BTC", "USDT");
        String p2 = SymbolCache.get("BTC", "ETH");
        String p3 = SymbolCache.get("ETH", "USDT");

        Map<String, Double> prices = new HashMap<>();
        prices.put(p1, 50000.0);
        prices.put(p2, 20.0);
        prices.put(p3, 2525.0);

        // 2. Configuración de Mocks (Leniency para evitar ruidos de hilos)
        // Forzamos el RTT para que pase el filtro inicial
        lenient().when(mockConnector.getRTT(anyString())).thenReturn(50L);

        mockOrderBook(p1, 50000.0);
        mockOrderBook(p2, 20.0);
        mockOrderBook(p3, 2525.0);

        // 3. Invocación por Reflexión
        Method method = DeepMarketScanner.class.getDeclaredMethod("analyzeTriangularLoop",
                String.class, String.class, Map.class);
        method.setAccessible(true);

        // 4. Capturamos estado inicial
        double initialProfit = scanner.getTotalPotentialProfit();

        // 5. Ejecución (El hot path)
        method.invoke(scanner, "binance", "BTC", prices);

        // 6. VALIDACIÓN POR ESTADO (Más robusta que verify en hilos virtuales)
        // Usamos un pequeño delay o esperamos a que el contador suba
        long deadline = System.currentTimeMillis() + 1000;
        while (scanner.getTotalPotentialProfit() <= initialProfit && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }

        assertThat(scanner.getTotalPotentialProfit())
                .as("El bot debería haber detectado y sumado el profit de la oportunidad triangular")
                .isGreaterThan(initialProfit);

        System.out.println("✅ MATH TRIANGULAR: Validado por incremento de PnL Potencial.");
    }
    @Test
    @DisplayName("💸 MATH: Spread Neto (Profit - Fees > MinProfit)")
    void shouldCalculateNetProfitCorrectly() throws Exception {
        // ✅ REPARACIÓN 2: Nueva firma de método (BalanceSnapshot en lugar de Map)
        Method method = DeepMarketScanner.class.getDeclaredMethod("simulateSpatialScenarioOptimized",
                String.class, String.class, String.class, double.class,
                ExchangeConnector.OrderBook.class, ExchangeConnector.OrderBook.class,
                double.class, BalanceSnapshot.class, long.class, double.class, double.class);
        method.setAccessible(true);

        ExchangeConnector.OrderBook bookBuy = mock(ExchangeConnector.OrderBook.class);
        ExchangeConnector.OrderBook bookSell = mock(ExchangeConnector.OrderBook.class);

        when(mockConnector.calculateWeightedPrice(eq(bookBuy), eq("BUY"), anyDouble())).thenReturn(100.0);
        when(mockConnector.calculateWeightedPrice(eq(bookSell), eq("SELL"), anyDouble())).thenReturn(101.0);

        // ✅ REPARACIÓN 3: Crear Snapshot real para evitar NullPointerException
        Map<String, Map<String, Double>> balData = new HashMap<>();
        balData.put("binance", Map.of("USDT", 1000.0));
        BalanceSnapshot fakeSnapshot = new BalanceSnapshot(balData, System.currentTimeMillis());

        // Ejecutamos (SOLUSDT: Buy 100, Sell 101, Fees 0.1% cada uno)
        method.invoke(scanner, "SOL", "binance", "bybit", 100.0,
                bookBuy, bookSell, 100.0, fakeSnapshot, System.currentTimeMillis(),
                0.001, 0.001);

        // Verificamos que no hubo errores y el profit potencial aumentó (ya que $0.799 > threshold)
        assertThat(scanner.getTotalPotentialProfit()).isGreaterThan(0);
        System.out.println("✅ FEE MATH: Cálculo neto validado con BalanceSnapshot.");
    }

    @Test
    @DisplayName("🤐 SILENCE: Ignora oportunidades sin margen suficiente")
    void shouldIgnoreLowProfitLoops() throws Exception {
        String p1 = SymbolCache.get("A", "USDT");
        String p2 = SymbolCache.get("A", "B");
        String p3 = SymbolCache.get("B", "USDT");

        Map<String, Double> prices = new HashMap<>();
        prices.put(p1, 100.0);
        prices.put(p2, 1.0);
        prices.put(p3, 100.0);

        Method method = DeepMarketScanner.class.getDeclaredMethod("analyzeTriangularLoop", String.class, String.class, Map.class);
        method.setAccessible(true);

        method.invoke(scanner, "binance", "A", prices);

        // No debe haber llamadas a red si el profit matemático es 1.0 (0%)
        verify(mockConnector, never()).getRTT(anyString());
    }

    private void mockOrderBook(String pair, double price) {
        ExchangeConnector.OrderBook book = new ExchangeConnector.OrderBook(
                java.util.List.of(new double[]{price, 100.0}), // Más liquidez
                java.util.List.of(new double[]{price, 100.0})
        );
        try {
            // Usamos leniency para evitar advertencias de Mockito innecesarias
            lenient().when(mockConnector.fetchOrderBook(anyString(), eq(pair), anyInt())).thenReturn(book);
            lenient().when(mockConnector.calculateWeightedPrice(any(), anyString(), anyDouble())).thenReturn(price);
        } catch(Exception e) { }
    }
}