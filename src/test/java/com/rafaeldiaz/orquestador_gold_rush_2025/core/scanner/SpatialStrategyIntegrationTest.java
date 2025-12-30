package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpatialStrategyIntegrationTest {

    @Mock
    private ExchangeConnector mockConnector;
    @Mock
    private ExecutionCoordinator mockCoordinator;

    private DeepMarketScanner scanner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Configuramos valores por defecto seguros
        when(mockConnector.getRTT(anyString())).thenReturn(50L);
        when(mockCoordinator.tryAcquireDualLock(anyString(), anyString())).thenReturn(true);
        // Por defecto fees bajos (0%) para que no interfieran en tests que no son de fees
        when(mockConnector.fetchDynamicTradingFee(anyString(), anyString())).thenReturn(new double[]{0.0, 0.0});

        scanner = new DeepMarketScanner(mockConnector, mockCoordinator);
        scanner.setDryRun(false);
    }

    @Test
    @DisplayName("🚀 ESTRATEGIA ESPACIAL: Flujo Completo (Detect -> Execute)")
    void shouldTriggerExecution_WhenOpportunityIsReal() throws Exception {
        String asset = "SOL";
        String buyEx = "binance";
        String sellEx = "bybit";
        double cheapPrice = 100.0;
        double expensivePrice = 102.0; // 2% de ganancia bruta

        // 1. Mock de Precios
        Map<String, Map<String, Double>> marketData = new HashMap<>();
        marketData.put(buyEx, Map.of("SOLUSDT", cheapPrice));
        marketData.put(sellEx, Map.of("SOLUSDT", expensivePrice));

        // 2. Mock OrderBooks
        mockOrderBook(buyEx, "SOLUSDT", cheapPrice);
        mockOrderBook(sellEx, "SOLUSDT", expensivePrice);

        // 3. BalanceSnapshot Real
        Map<String, Map<String, Double>> balData = new HashMap<>();
        balData.put(buyEx, Map.of("USDT", 1000.0));
        BalanceSnapshot snapshot = new BalanceSnapshot(balData, System.currentTimeMillis());

        // 4. Mock Ejecución Exitosa
        OrderResult success = new OrderResult("1", "FILLED", 1.0, 1.0, 100.0, 100.0, 0.1, "BNB");
        when(mockConnector.placeOrder(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(success);

        // 5. Asegurar que estamos en modo SPATIAL
        try (var mockedConfig = mockStatic(BotConfig.class)) {
            mockedConfig.when(BotConfig::isSpatialStrategy).thenReturn(true);

            // --- EJECUCIÓN ---
            Method method = DeepMarketScanner.class.getDeclaredMethod("analyzeAssetInMemory",
                    String.class, Map.class, BalanceSnapshot.class, long.class);
            method.setAccessible(true);
            method.invoke(scanner, asset, marketData, snapshot, System.currentTimeMillis());
        }

        // --- VERIFICACIÓN ---
        verify(mockConnector, timeout(1000).atLeastOnce()).placeOrder(
                eq(buyEx), eq("SOLUSDT"), eq("BUY"), anyString(), anyDouble(), anyDouble()
        );
    }

    @Test
    @DisplayName("🛑 FILTRO FEES: Spread insuficiente no dispara nada")
    void shouldHoldFire_WhenFeesEatProfit() throws Exception {
        // --- 1. CONFIGURACIÓN DEL ESCENARIO ---
        String asset = "SOL";
        double price = 100.0;
        double priceHigh = 100.15; // Spread bruto de 0.15%

        Map<String, Map<String, Double>> marketData = new HashMap<>();
        marketData.put("binance", Map.of("SOLUSDT", price));
        marketData.put("bybit", Map.of("SOLUSDT", priceHigh));

        mockOrderBook("binance", "SOLUSDT", price);
        mockOrderBook("bybit", "SOLUSDT", priceHigh);

        Map<String, Map<String, Double>> balData = new HashMap<>();
        balData.put("binance", Map.of("USDT", 1000.0));
        BalanceSnapshot snapshot = new BalanceSnapshot(balData, System.currentTimeMillis());

        // --- 2. EL FIX CRÍTICO (CALLS_REAL_METHODS) ---
        // Usamos CALLS_REAL_METHODS para que Mockito NO anule las constantes (como NORMAL_MIN_PROFIT).
        // Si no hacemos esto, Mockito podría tratar las variables 'final' de forma extraña.
        try (var mockedConfig = mockStatic(BotConfig.class, CALLS_REAL_METHODS)) {

            // Solo sobreescribimos el método de estrategia
            mockedConfig.when(BotConfig::isSpatialStrategy).thenReturn(true);

            // --- 3. FORZAR PÉRDIDA MASIVA ---
            // Configuramos Fees del 10% (0.10).
            // Con $100 de capital, esto son $20 de fees. La pérdida será ~$19.85.
            // Esto es mucho menor que cualquier profit mínimo (0.15).
            when(mockConnector.fetchDynamicTradingFee(anyString(), anyString()))
                    .thenReturn(new double[]{0.10, 0.10});

            // --- 4. EJECUCIÓN ---
            Method method = DeepMarketScanner.class.getDeclaredMethod("analyzeAssetInMemory",
                    String.class, Map.class, BalanceSnapshot.class, long.class);
            method.setAccessible(true);

            method.invoke(scanner, asset, marketData, snapshot, System.currentTimeMillis());

            // --- 5. VERIFICACIÓN ---
            // Verificamos que NUNCA se llamó a placeOrder
            verify(mockConnector, after(500).never()).placeOrder(
                    anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble()
            );
        }

        System.out.println("✅ FILTRO FEES: Test pasó. El bot se abstuvo correctamente.");
    }

    private void mockOrderBook(String ex, String pair, double price) {
        ExchangeConnector.OrderBook book = new ExchangeConnector.OrderBook(
                Collections.singletonList(new double[]{price, 100.0}),
                Collections.singletonList(new double[]{price, 100.0})
        );
        when(mockConnector.fetchOrderBook(eq(ex), eq(pair), anyInt())).thenReturn(book);
        when(mockConnector.calculateWeightedPrice(eq(book), anyString(), anyDouble())).thenReturn(price);
    }
}