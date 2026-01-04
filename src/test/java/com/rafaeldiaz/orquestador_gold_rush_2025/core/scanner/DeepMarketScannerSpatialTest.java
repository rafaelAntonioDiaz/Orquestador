package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ProfitEstimator; // IMPORTANTE
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("🛰️ Deep Market Scanner v4.3 - Integration Test (Profit Bypass)")
class DeepMarketScannerSpatialTest {

    private static final String ASSET = "SOL";
    private static final String PAIR = "SOLUSDT";
    private static final String BUY_EXCHANGE = "binance";
    private static final String SELL_EXCHANGE = "bybit_sub1";

    @Mock private ExchangeConnector mockConnector;
    @Mock private ExecutionCoordinator mockCoordinator;
    @Mock private CrossTradeExecutor mockCrossExecutor;
    @Mock private FeeManager mockFeeManager;
    @Mock private ProfitEstimator mockProfitEstimator; // 🔥 NUEVO MOCK

    private DeepMarketScanner scanner;
    private AutoCloseable mocks;

    private static MockedStatic<BotConfig> mockedConfig;

    @BeforeAll
    static void initStaticMocks() {
        mockedConfig = mockStatic(BotConfig.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        mockedConfig.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // 1. CONFIGURACIÓN
        mockedConfig.when(BotConfig::isSpatialStrategy).thenReturn(true);
        mockedConfig.when(BotConfig::getActiveExchanges).thenReturn(List.of(BUY_EXCHANGE, SELL_EXCHANGE));
        mockedConfig.when(BotConfig::getMinScanSpread).thenReturn(0.001);

        // 2. Mocks Base
        lenient().when(mockConnector.getStepSize(anyString(), anyString())).thenReturn(0.01);

        // 3. Instanciar
        scanner = new DeepMarketScanner(mockConnector, mockCoordinator);

        // 4. INYECCIONES QUIRÚRGICAS 🔥
        injectField(scanner, "feeManager", mockFeeManager);
        injectField(scanner, "crossExecutor", mockCrossExecutor);
        injectField(scanner, "profitEstimator", mockProfitEstimator); // 🔥 INYECTAMOS EL CEREBRO FINANCIERO MOCKEADO

        // 5. Configurar el "Pass-Through" del Estimador
        // "Si encuentras una oportunidad, dila que es válida (devuélvela tal cual)"
        when(mockProfitEstimator.estimateProfitability(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 6. Setup de Objetivos
        updateHuntingGrounds(scanner, List.of(ASSET));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scanner != null) scanner.shutdown();
        mocks.close();
    }

    @Test
    @Order(1)
    @DisplayName("🚀 INTEGRACIÓN: ScanCycle detecta spread y ejecuta CrossTrade")
    void testScanCycle_ExecutesSpatialArbitrage() throws Exception {
        // --- 1. MERCADO ---
        mockMarketPrices(BUY_EXCHANGE, PAIR, 200.0);
        mockMarketPrices(SELL_EXCHANGE, PAIR, 210.0);
        mockOrderBook(BUY_EXCHANGE, PAIR, 200.0);
        mockOrderBook(SELL_EXCHANGE, PAIR, 210.0);

        // --- 2. ESTADO ---
        scanner.setDryRun(false);
        when(mockCoordinator.tryAcquireDualLock(anyString(), anyString())).thenReturn(true);
        when(mockCoordinator.isSnapshotStale(anyString(), anyLong())).thenReturn(false);

        // --- 3. INYECCIÓN SALDO ---
        Map<String, Map<String, Double>> wallet = new HashMap<>();
        wallet.put(BUY_EXCHANGE, Map.of("USDT", 10000.0));
        wallet.put(SELL_EXCHANGE, Map.of("SOL", 50.0));
        BalanceSnapshot manualSnapshot = new BalanceSnapshot(wallet, System.currentTimeMillis());
        injectField(scanner, "currentSnapshot", manualSnapshot);

        System.out.println("DEBUG: Snapshot inyectado. Saldo USDT: " + manualSnapshot.getAvailableBalance(BUY_EXCHANGE, "USDT"));

        // --- 4. EJECUCIÓN ---
        invokeScanCycle(scanner);

        // --- 5. VERIFICACIÓN ---
        verify(mockCrossExecutor, times(1)).executeCrossTrade(
                eq(BUY_EXCHANGE), eq(SELL_EXCHANGE), contains("SOL"),
                anyDouble(), anyDouble(), anyDouble()
        );

        System.out.println("✅ TEST INTEGRACIÓN: ¡Estrategia Activada y Ejecutada!");
    }

    // =========================================================================
    // 🛠️ HELPERS
    // =========================================================================

    private void invokeScanCycle(Object target) throws Exception {
        Method method = target.getClass().getDeclaredMethod("scanCycle");
        method.setAccessible(true);
        method.invoke(target);
    }

    private void updateHuntingGrounds(Object target, List<String> assets) throws Exception {
        ((DeepMarketScanner) target).updateTargets(assets);
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void mockMarketPrices(String exchange, String pair, double price) {
        Map<String, Double> prices = new HashMap<>();
        prices.put(pair, price);
        lenient().when(mockConnector.fetchAllPrices(eq(exchange))).thenReturn(prices);
    }

    private void mockOrderBook(String exchange, String pair, double price) {
        ExchangeConnector.OrderBook book = new ExchangeConnector.OrderBook(
                List.of(new double[]{price, 10000.0}),
                List.of(new double[]{price, 10000.0})
        );
        lenient().when(mockConnector.fetchOrderBook(eq(exchange), eq(pair), anyInt())).thenReturn(book);
        lenient().when(mockConnector.calculateWeightedPrice(any(), anyString(), anyDouble())).thenReturn(price);
    }
}