package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.ArbitrageStrategy;
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
@DisplayName("💥 Deep Market Scanner - Triangular Emergency Exit Test")
class DeepMarketScannerTriangularFailureTest {

    private static final String EXCHANGE = "binance";
    private static final String ASSET = "SOL";

    // Pares del Triángulo
    private static final String P1_USDT = "SOLUSDT"; // Entrada y SALIDA DE EMERGENCIA
    private static final String P2_BRIDGE = "SOLBTC";  // El que va a fallar
    private static final String P3_EXIT = "BTCUSDT"; // No debería llegarse aquí

    @Mock private ExchangeConnector mockConnector;
    @Mock private ExecutionCoordinator mockCoordinator;
    @Mock private FeeManager mockFeeManager;
    @Mock private ProfitEstimator mockProfitEstimator;
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

        // 1. CONFIGURACIÓN
        mockedConfig.when(BotConfig::isSpatialStrategy).thenReturn(false);
        mockedConfig.when(BotConfig::getActiveExchanges).thenReturn(List.of(EXCHANGE));
        mockedConfig.when(BotConfig::getAdvisorRefExchange).thenReturn(EXCHANGE);
        // 2. Mocks Base
        lenient().when(mockConnector.getStepSize(anyString(), anyString())).thenReturn(0.01);
        lenient().when(mockFeeManager.getTradingFee(anyString(), anyString(), anyString())).thenReturn(0.001);

        // 3. Instanciar Scanner
        scanner = new DeepMarketScanner(mockConnector, mockCoordinator);

        // 4. INYECCIONES (Bypass para llegar directo al ejecutor)
        injectField(scanner, "feeManager", mockFeeManager);
        injectField(scanner, "profitEstimator", mockProfitEstimator);

        // Inyectar Estrategia Falsa
        List<ArbitrageStrategy> strategiesList = new ArrayList<>();
        strategiesList.add(mockStrategy);
        injectField(scanner, "strategies", strategiesList);

        // Bypass Financiero
        when(mockProfitEstimator.estimateProfitability(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        updateHuntingGrounds(scanner, List.of(ASSET));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scanner != null) scanner.shutdown();
        mocks.close();
    }

    @Test
    @Order(1)
    @DisplayName("🚑 EMERGENCIA: Paso 2 falla -> Vende Asset contra USDT inmediatamente")
    void testEmergencyExit_WhenStep2Fails() throws Exception {
        // --- 1. PREPARAR OPORTUNIDAD ---
        ArbitrageOpportunity forcedOpp = new ArbitrageOpportunity(
                "TRIANGULAR_LOOP_V1",
                ASSET, EXCHANGE, "BTC",
                100.0, 0.0, 0.10, 0.0, 10.0, System.currentTimeMillis()
        );
        when(mockStrategy.findOpportunities(eq(ASSET), any())).thenReturn(List.of(forcedOpp));

        // Mock de Precios (Necesario para evitar NPEs)
        Map<String, Double> dummyPrices = new HashMap<>();
        dummyPrices.put(P1_USDT, 100.0);
        lenient().when(mockConnector.fetchAllPrices(eq(EXCHANGE))).thenReturn(dummyPrices);

        // --- 2. CONFIGURAR DRAMA EN LAS ÓRDENES ---
        // CORRECCIÓN: Usamos doReturn().when() para evitar que se ejecute la implementación real
        // que internamente llamaba a fetchBalance() causando el error.

        // PASO 1: ÉXITO (Compramos 10 SOL)
        OrderResult r1 = new OrderResult("ord1", "FILLED", 10.0, 10.0, 1000.0, 100.0, 0.1, "BNB");
        doReturn(r1).when(mockConnector).placeOrder(
                eq(EXCHANGE),
                eq(P1_USDT),
                eq("BUY"),
                contains("LIMIT"),
                anyDouble(),
                anyDouble()
        );

        // PASO 2: FALLO TOTAL (Intentamos vender SOL por BTC y nos rechazan)
        // Simulamos un "REJECTED" o un Timeout que deja el estado en no-filled
        OrderResult r2 = new OrderResult("ord2", "REJECTED", 10.0, 0.0, 0.0, 0.0, 0.0, "NONE");
        doReturn(r2).when(mockConnector).placeOrder(
                eq(EXCHANGE),
                eq(P2_BRIDGE),
                eq("SELL"),
                eq("MARKET"),
                anyDouble(),
                anyDouble()
        );

        // SALIDA DE EMERGENCIA: Venta de Pánico (SOL -> USDT)
        // El bot debería intentar vender los 10 SOL adquiridos en r1 de vuelta a USDT
        OrderResult rEmergency = new OrderResult("ordEmg", "FILLED", 10.0, 10.0, 990.0, 99.0, 0.1, "BNB");
        doReturn(rEmergency).when(mockConnector).placeOrder(
                eq(EXCHANGE),
                eq(P1_USDT),
                eq("SELL"),
                eq("MARKET"),
                eq(10.0),
                anyDouble()
        );

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
        System.out.println("🔥 INICIANDO ESCENARIO DE FALLO EN PASO 2...");
        invokeScanCycle(scanner);

        // --- 5. VERIFICACIÓN FORENSE ---
        System.out.println("🔍 Analizando respuesta ante el desastre...");

        // Verificamos Paso 1 (Compra)
        verify(mockConnector).placeOrder(eq(EXCHANGE), eq(P1_USDT), eq("BUY"), contains("LIMIT"), anyDouble(), anyDouble());
        System.out.println("✅ PASO 1: Compra inicial OK.");

        // Verificamos Paso 2 (Intento fallido)
        verify(mockConnector).placeOrder(eq(EXCHANGE), eq(P2_BRIDGE), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble());
        System.out.println("✅ PASO 2: Intento de venta Bridge (y fallo simulado) OK.");

        // Verificamos que EL PASO 3 (BTC->USDT) NUNCA OCURRIÓ
        verify(mockConnector, never()).placeOrder(eq(EXCHANGE), eq(P3_EXIT), anyString(), anyString(), anyDouble(), anyDouble());
        System.out.println("✅ PASO 3: Correctamente abortado (No vendimos aire).");

        // 🔥 VERIFICACIÓN CRÍTICA: LA SALIDA DE EMERGENCIA
        // Debe haber vendido SOL (P1_USDT) usando MARKET y la cantidad adquirida (10.0)
        verify(mockConnector).placeOrder(eq(EXCHANGE), eq(P1_USDT), eq("SELL"), eq("MARKET"), eq(10.0), anyDouble());
        System.out.println("🚑 SALIDA DE EMERGENCIA: ¡EJECUTADA! Se vendió SOL contra USDT para salvar capital.");
    }

    // --- Helpers (Igual que antes) ---
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