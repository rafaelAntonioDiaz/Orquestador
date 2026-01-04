package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy; // Importante
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ProfitEstimator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;

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
@DisplayName("📐 Deep Market Scanner - Triangular Execution Force Test")
class DeepMarketScannerTriangularTest {

    private static final String EXCHANGE = "binance";
    private static final String ASSET = "SOL";

    // Pares esperados
    private static final String P1 = "SOLUSDT";
    private static final String P2 = "SOLBTC";
    private static final String P3 = "BTCUSDT";

    @Mock private ExchangeConnector mockConnector;
    @Mock private ExecutionCoordinator mockCoordinator;
    @Mock private FeeManager mockFeeManager;
    @Mock private ProfitEstimator mockProfitEstimator;
    @Mock private ArbitrageStrategy mockStrategy; // 🔥 ESTRATEGIA FALSA

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

        // 1. CONFIG
        mockedConfig.when(BotConfig::isSpatialStrategy).thenReturn(false);
        mockedConfig.when(BotConfig::getActiveExchanges).thenReturn(List.of(EXCHANGE));
        // Nota: Ya no nos importa getBridgeAssets porque usaremos estrategia mockeada

        // 2. Mocks Base
        lenient().when(mockConnector.getStepSize(anyString(), anyString())).thenReturn(0.01);
        lenient().when(mockFeeManager.getTradingFee(anyString(), anyString(), anyString())).thenReturn(0.001);

        // 3. Instanciar Scanner
        scanner = new DeepMarketScanner(mockConnector, mockCoordinator);

        // 4. INYECCIONES (BYPASS TOTAL)
        injectField(scanner, "feeManager", mockFeeManager);
        injectField(scanner, "profitEstimator", mockProfitEstimator);

        // 🔥 INYECTAR LA ESTRATEGIA FALSA EN LA LISTA DEL SCANNER
        List<ArbitrageStrategy> strategiesList = new ArrayList<>();
        strategiesList.add(mockStrategy);
        injectField(scanner, "strategies", strategiesList);

        // 5. Configurar Bypass Financiero
        when(mockProfitEstimator.estimateProfitability(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0)); // Pasa todo

        // 6. Setup Inicial
        updateHuntingGrounds(scanner, List.of(ASSET));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scanner != null) scanner.shutdown();
        mocks.close();
    }

    @Test
    @Order(1)
    @DisplayName("🔫 FUEGO TRIPARTITO: Dispara secuencia con oportunidad inyectada")
    void testTriangularLoopExecution() throws Exception {
        // --- 1. PREPARAR OPORTUNIDAD PERFECTA ---
        // Creamos la oportunidad manualmente para no depender de cálculos de precios
        ArbitrageOpportunity forcedOpp = new ArbitrageOpportunity(
                "TRIANGULAR_LOOP_V1", // Debe contener "TRIANGULAR" para activar el executor
                ASSET,          // "SOL"
                EXCHANGE,       // "binance"
                "BTC",          // Bridge
                100.0,          // Precio Entrada
                0.0,            // Exit irrelevante aquí
                0.10,           // 10% Profit
                0.0,            // Qty
                10.0,           // Expected Profit
                System.currentTimeMillis()
        );

        // Cuando la estrategia busque, devuelve nuestra oportunidad forzada
        when(mockStrategy.findOpportunities(eq(ASSET), any())).thenReturn(List.of(forcedOpp));

        // --- 2. CONFIGURAR RESPUESTAS DE LA API (MOCK) ---
        // Necesitamos que connector.fetchAllPrices devuelva algo para que el scanner no aborte antes
        Map<String, Double> dummyPrices = new HashMap<>();
        dummyPrices.put(P1, 100.0);
        lenient().when(mockConnector.fetchAllPrices(eq(EXCHANGE))).thenReturn(dummyPrices);

        // Preparamos las respuestas de éxito para las órdenes
        OrderResult r1 = new OrderResult("ord1", "FILLED", 10.0, 10.0, 1000.0, 100.0, 0.1, "BNB");
        when(mockConnector.placeOrder(eq(EXCHANGE), eq(P1), eq("BUY"), contains("LIMIT"), anyDouble(), anyDouble()))
                .thenReturn(r1);

        OrderResult r2 = new OrderResult("ord2", "FILLED", 10.0, 10.0, 0.022, 0.0022, 0.0001, "BNB");
        when(mockConnector.placeOrder(eq(EXCHANGE), eq(P2), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble()))
                .thenReturn(r2);

        OrderResult r3 = new OrderResult("ord3", "FILLED", 0.022, 0.022, 1100.0, 50000.0, 0.1, "BNB");
        when(mockConnector.placeOrder(eq(EXCHANGE), eq(P3), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble()))
                .thenReturn(r3);

        // --- 3. ESTADO DEL SCANNER ---
        scanner.setDryRun(false);
        when(mockCoordinator.tryAcquireLock(eq(EXCHANGE))).thenReturn(true);
        when(mockCoordinator.isSnapshotStale(anyString(), anyLong())).thenReturn(false);

        // Inyectar Saldo
        Map<String, Map<String, Double>> wallet = new HashMap<>();
        wallet.put(EXCHANGE, Map.of("USDT", 1000.0));
        BalanceSnapshot manualSnapshot = new BalanceSnapshot(wallet, System.currentTimeMillis());
        injectField(scanner, "currentSnapshot", manualSnapshot);

        // --- 4. EJECUCIÓN ---
        System.out.println("🔥 INICIANDO EJECUCIÓN FORZADA...");
        invokeScanCycle(scanner);

        // --- 5. VERIFICACIÓN ---
        System.out.println("🔍 Verificando disparos...");

        // Verificamos que la estrategia fue consultada
        verify(mockStrategy).findOpportunities(eq(ASSET), any());

        // Verificamos la secuencia de disparo
        verify(mockConnector).placeOrder(eq(EXCHANGE), eq(P1), eq("BUY"), contains("LIMIT"), anyDouble(), anyDouble());
        System.out.println("✅ PASO 1: Buy SOL/USDT");

        verify(mockConnector).placeOrder(eq(EXCHANGE), eq(P2), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble());
        System.out.println("✅ PASO 2: Sell SOL/BTC");

        verify(mockConnector).placeOrder(eq(EXCHANGE), eq(P3), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble());
        System.out.println("✅ PASO 3: Sell BTC/USDT");
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
}