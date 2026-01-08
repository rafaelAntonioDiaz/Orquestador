package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic.MarketCortex;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic.ProbabilisticOracle;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic.Verdict;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy; // IMPORTANTE
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ProfitEstimator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.DashboardService;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
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
@DisplayName("🛰️ Deep Market Scanner v4.5 - Integration Test (Oracle & Dashboard Bypass)")
class DeepMarketScannerSpatialTest {

    private static final String ASSET = "SOL";
    private static final String PAIR = "SOLUSDT";
    private static final String BUY_EXCHANGE = "binance";
    private static final String SELL_EXCHANGE = "bybit_sub1";

    // Componentes Base
    @Mock private ExchangeConnector mockConnector;
    @Mock private ExecutionCoordinator mockCoordinator;
    @Mock private CrossTradeExecutor mockCrossExecutor;
    @Mock private FeeManager mockFeeManager;
    @Mock private PortfolioHealthManager mockCFO;
    @Mock private ProfitEstimator mockProfitEstimator;

    // 🔥 NUEVOS COMPONENTES
    @Mock private MarketCortex mockCortex;
    @Mock private ProbabilisticOracle mockOracle;
    @Mock private DashboardService mockDashboard;
    @Mock private DynamicPairSelector mockPairSelector;

    // 🛡️ MOCK ESTRATEGIA (LA SOLUCIÓN)
    @Mock private ArbitrageStrategy mockStrategy;

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

        // 1. CONFIGURACIÓN DEL ENTORNO
        mockedConfig.when(BotConfig::isSpatialStrategy).
                thenReturn(true);
        mockedConfig.when(BotConfig::getActiveExchanges).
                thenReturn(List.of(BUY_EXCHANGE, SELL_EXCHANGE));
        mockedConfig.when(BotConfig::getAdvisorRefExchange).
                thenReturn(BUY_EXCHANGE);
        mockedConfig.when(BotConfig::getMaxLatencyMs).
                thenReturn(100.0);

        // 2. Mocks del Connector
        lenient().when(mockConnector.getStepSize(anyString(), anyString())).thenReturn(0.01);
        lenient().when(mockConnector.fetchOrderBook(anyString(), anyString(), anyInt()))
                .thenReturn(new ExchangeConnector.OrderBook(Collections.emptyList(), Collections.emptyList()));

        // 3. INSTANCIACIÓN REAL
        scanner = new DeepMarketScanner(mockConnector, mockCoordinator);

        // 4. 💉 INYECCIÓN QUIRÚRGICA
        injectField(scanner, "feeManager", mockFeeManager);
        injectField(scanner, "cfo", mockCFO);
        injectField(scanner, "pairSelector", mockPairSelector);
        injectField(scanner, "cortex", mockCortex);
        injectField(scanner, "oracle", mockOracle);
        injectField(scanner, "dashboard", mockDashboard);
        injectField(scanner, "profitEstimator", mockProfitEstimator);
        injectField(scanner, "crossExecutor", mockCrossExecutor);

        // 🔥 CRÍTICO: REEMPLAZAR LA ESTRATEGIA REAL POR EL MOCK
        // Accedemos a la lista privada 'strategies' y metemos nuestro mock
        Field strategiesField = DeepMarketScanner.class.getDeclaredField("strategies");
        strategiesField.setAccessible(true);
        List<ArbitrageStrategy> strategyList = (List<ArbitrageStrategy>) strategiesField.get(scanner);
        strategyList.clear(); // Borramos la real (AdaptiveSpatialStrategy)
        strategyList.add(mockStrategy); // Metemos el Mock

        // 5. CONFIGURACIÓN DE LOS "PASSTHROUGHS"
        when(mockProfitEstimator.estimateProfitability(any(ArbitrageOpportunity.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(mockOracle.getVerdict(anyString(), anyDouble(), anyString()))
                .thenReturn(new Verdict("TEST_SIGNAL", 1.0, 0.5));

        // 6. Setup de Objetivos
        scanner.updateTargets(List.of(ASSET));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scanner != null) scanner.shutdown();
        mocks.close();
    }

    @Test
    @Order(1)
    @DisplayName("🚀 INTEGRACIÓN: ScanCycle -> Oracle -> Profit -> Ejecución")
    void testScanCycle_ExecutesSpatialArbitrage() throws Exception {
        // --- 1. DATOS DE MERCADO ---
        Map<String, Double> pricesBinance = new HashMap<>(); pricesBinance.put(PAIR, 200.0);
        // Nota: Solo necesitamos que fetchGlobalPrices no devuelva mapa vacío para que entre al ciclo.
        when(mockConnector.fetchAllPrices(eq(BUY_EXCHANGE))).thenReturn(pricesBinance);
        when(mockConnector.fetchAllPrices(eq(SELL_EXCHANGE))).thenReturn(pricesBinance);

        // --- 2. SIMULAR HALLAZGO DE ESTRATEGIA ---
        // Aquí forzamos que la estrategia encuentre algo, sin depender de precios reales
        ArbitrageOpportunity testOpp = new ArbitrageOpportunity(
                "SPATIAL", ASSET, BUY_EXCHANGE, SELL_EXCHANGE,
                200.0, 210.0, 0.05, 1.0, 10.0, System.currentTimeMillis()
        );
        // Cuando el scanner pregunte a la estrategia, respondemos con la oportunidad prefabricada
        when(mockStrategy.findOpportunities(eq(ASSET), anyMap())).thenReturn(List.of(testOpp));

        // --- 3. ESTADO DEL COORDINADOR ---
        scanner.setDryRun(false);
        when(mockCoordinator.tryAcquireDualLock(anyString(), anyString())).thenReturn(true);
        when(mockCoordinator.isSnapshotStale(anyString(), anyLong())).thenReturn(false);

        // --- 4. INYECCIÓN SALDO ---
        Map<String, Map<String, Double>> wallet = new HashMap<>();
        wallet.put(BUY_EXCHANGE, Map.of("USDT", 10000.0));
        wallet.put(SELL_EXCHANGE, Map.of("SOL", 50.0));
        BalanceSnapshot manualSnapshot = new BalanceSnapshot(wallet, System.currentTimeMillis());
        injectField(scanner, "currentSnapshot", manualSnapshot);

        System.out.println("DEBUG: Entorno listo. Ejecutando ciclo de escaneo...");

        // --- 5. EJECUCIÓN ---
        invokeScanCycle(scanner);

        // --- 6. VERIFICACIÓN ---
        // A. Verificar que se llamó a la estrategia
        verify(mockStrategy, times(1)).findOpportunities(eq(ASSET), anyMap());

        // B. Verificar que se consultó al Oráculo (AHORA SÍ DEBERÍA PASAR)
        verify(mockOracle, times(1)).getVerdict(eq(ASSET), anyDouble(), anyString());

        // C. Verificar Ejecución
        verify(mockCrossExecutor, times(1)).executeCrossTrade(
                eq(BUY_EXCHANGE),
                eq(SELL_EXCHANGE),
                contains("SOL"),
                anyDouble(),
                anyDouble(),
                anyDouble()
        );

        System.out.println("✅ TEST COMPLETADO: El Scanner orquestó todo el flujo correctamente.");
    }

    // =========================================================================
    // 🛠️ HELPERS
    // =========================================================================

    private void invokeScanCycle(Object target) throws Exception {
        Method method = target.getClass().getDeclaredMethod("scanCycle");
        method.setAccessible(true);
        method.invoke(target);
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}