package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.test.fixtures.ArbitrageTestFixtures;
import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 🧪 SUITE DE PRUEBAS: ESTRATEGIA ESPACIAL DE ARBITRAJE
 *
 * Objetivo: Validar el flujo completo desde detección hasta ejecución
 * con mocks realistas que simulan condiciones reales de mercado.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("🛰️ Deep Market Scanner - Estrategia Espacial")
class DeepMarketScannerSpatialTest {

    // Constantes de testing realistas
    private static final String ASSET = "SOL";
    private static final String PAIR = "SOLUSDT";
    private static final String BUY_EXCHANGE = "binance";

    // ✅ CORRECCIÓN CRÍTICA: Usamos "bybit" para coincidir con BotConfig.ACTIVE_EXCHANGES
    // Si usamos "bybit_sub1", el scanner lo ignorará porque no está en su lista de configuración por defecto.
    private static final String SELL_EXCHANGE = "bybit";

    // Precios realistas (spread del 0.8% = $1.60 en SOL)
    private static final double BINANCE_PRICE = 200.00;  // Más barato
    private static final double BYBIT_PRICE = 201.60;    // Más caro
    private static final double REALISTIC_FEE = 0.001;   // 0.1%

    @Mock private ExchangeConnector mockConnector;
    @Mock private ExecutionCoordinator mockCoordinator;
    @Mock private CrossTradeExecutor mockCrossExecutor;
    @Mock private FeeManager mockFeeManager;

    private DeepMarketScanner scanner;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        scanner = new DeepMarketScanner(mockConnector, mockCoordinator);
        injectMockExecutor();

        when(mockFeeManager.getTradingFee(anyString(), anyString(), eq("TAKER")))
                .thenReturn(REALISTIC_FEE);

        injectMockFeeManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scanner != null) scanner.shutdown();
        mocks.close();
    }

    // =========================================================================
    // ✅ CASO FELIZ: EJECUCIÓN EXITOSA
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("✅ Spread rentable detectado → Ejecución en modo LIVE")
    void testSuccessfulSpatialArbitrage_LiveMode() throws Exception {
        scanner.setDryRun(false);

        Map<String, Map<String, Double>> marketData = ArbitrageTestFixtures.buildProfitableSpread(
                PAIR, BUY_EXCHANGE, SELL_EXCHANGE, BINANCE_PRICE, 0.8
        );
        BalanceSnapshot snapshot = ArbitrageTestFixtures.buildSingleExchangeSnapshot(BUY_EXCHANGE, "USDT", 1000.0);
        injectSnapshot(snapshot);

        // ✅ CORRECCIÓN SEMÁNTICA: buyBook usa precio bajo, sellBook usa precio alto
        ExchangeConnector.OrderBook buyBook = ArbitrageTestFixtures.buildHighLiquidityBook(BINANCE_PRICE);
        ExchangeConnector.OrderBook sellBook = ArbitrageTestFixtures.buildHighLiquidityBook(BYBIT_PRICE);

        when(mockConnector.fetchOrderBook(eq(BUY_EXCHANGE), eq(PAIR), anyInt())).thenReturn(buyBook);
        when(mockConnector.fetchOrderBook(eq(SELL_EXCHANGE), eq(PAIR), anyInt())).thenReturn(sellBook);

        when(mockConnector.calculateWeightedPrice(eq(buyBook), eq("BUY"), anyDouble()))
                .thenReturn(BINANCE_PRICE * 1.0005);
        when(mockConnector.calculateWeightedPrice(eq(sellBook), eq("SELL"), anyDouble()))
                .thenReturn(BYBIT_PRICE * 0.9995);

        when(mockCoordinator.tryAcquireDualLock(BUY_EXCHANGE, SELL_EXCHANGE)).thenReturn(true);
        when(mockCoordinator.isSnapshotStale(anyString(), anyLong())).thenReturn(false);

        invokeAnalyzeSpatialSpread(ASSET, marketData, snapshot, System.currentTimeMillis());

        verify(mockCrossExecutor, times(1)).executeCrossTrade(
                eq(BUY_EXCHANGE), eq(SELL_EXCHANGE), eq(PAIR),
                doubleThat(qty -> qty > 0 && qty < 10),
                doubleThat(price -> price > 199 && price < 202),
                doubleThat(price -> price > 199 && price < 202)
        );

        verify(mockCoordinator, times(1)).releaseLock(BUY_EXCHANGE);
        verify(mockCoordinator, times(1)).releaseLock(SELL_EXCHANGE);
        assertThat(scanner.getTradesCount()).isEqualTo(1);
    }

    // =========================================================================
    // ❌ CASOS DE FALLO
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("❌ Balance insuficiente → Trade rechazado")
    void testInsufficientBalance_TradeRejected() throws Exception {
        scanner.setDryRun(false);

        Map<String, Map<String, Double>> marketData = ArbitrageTestFixtures.buildProfitableSpread(
                PAIR, BUY_EXCHANGE, SELL_EXCHANGE, BINANCE_PRICE, 0.8
        );

        BalanceSnapshot snapshot = ArbitrageTestFixtures.buildSingleExchangeSnapshot(BUY_EXCHANGE, "USDT", 2.0);
        injectSnapshot(snapshot);

        // Corregido semánticamente
        ExchangeConnector.OrderBook buyBook = ArbitrageTestFixtures.buildHighLiquidityBook(BINANCE_PRICE);
        ExchangeConnector.OrderBook sellBook = ArbitrageTestFixtures.buildHighLiquidityBook(BYBIT_PRICE);

        when(mockConnector.fetchOrderBook(anyString(), eq(PAIR), anyInt())).thenReturn(buyBook, sellBook);
        when(mockConnector.calculateWeightedPrice(any(), anyString(), anyDouble())).thenReturn(BINANCE_PRICE, BYBIT_PRICE);

        invokeAnalyzeSpatialSpread(ASSET, marketData, snapshot, System.currentTimeMillis());

        verify(mockCrossExecutor, never()).executeCrossTrade(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble());
        assertThat(scanner.getTradesCount()).isZero();
    }

    @Test
    @Order(3)
    @DisplayName("❌ OrderBook nulo → Trade rechazado silenciosamente")
    void testNullOrderBook_TradeSkipped() throws Exception {
        scanner.setDryRun(false);
        Map<String, Map<String, Double>> marketData = ArbitrageTestFixtures.buildProfitableSpread(
                PAIR, BUY_EXCHANGE, SELL_EXCHANGE, BINANCE_PRICE, 0.8
        );
        BalanceSnapshot snapshot = ArbitrageTestFixtures.buildSingleExchangeSnapshot(BUY_EXCHANGE, "USDT", 1000.0);
        injectSnapshot(snapshot);

        when(mockConnector.fetchOrderBook(eq(BUY_EXCHANGE), eq(PAIR), anyInt())).thenReturn(null);

        invokeAnalyzeSpatialSpread(ASSET, marketData, snapshot, System.currentTimeMillis());

        verify(mockCrossExecutor, never()).executeCrossTrade(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @Order(4)
    @DisplayName("❌ Lock fallido → Trade rechazado (exchange ocupado)")
    void testLockAcquisitionFailed_TradeSkipped() throws Exception {
        scanner.setDryRun(false);
        Map<String, Map<String, Double>> marketData = ArbitrageTestFixtures.buildProfitableSpread(
                PAIR, BUY_EXCHANGE, SELL_EXCHANGE, BINANCE_PRICE, 0.8
        );
        BalanceSnapshot snapshot = ArbitrageTestFixtures.buildSingleExchangeSnapshot(BUY_EXCHANGE, "USDT", 1000.0);
        injectSnapshot(snapshot);

        ExchangeConnector.OrderBook buyBook = ArbitrageTestFixtures.buildHighLiquidityBook(BINANCE_PRICE);
        ExchangeConnector.OrderBook sellBook = ArbitrageTestFixtures.buildHighLiquidityBook(BYBIT_PRICE);

        when(mockConnector.fetchOrderBook(anyString(), eq(PAIR), anyInt())).thenReturn(buyBook, sellBook);
        when(mockConnector.calculateWeightedPrice(any(), anyString(), anyDouble())).thenReturn(BINANCE_PRICE, BYBIT_PRICE);

        when(mockCoordinator.tryAcquireDualLock(BUY_EXCHANGE, SELL_EXCHANGE)).thenReturn(false);

        invokeAnalyzeSpatialSpread(ASSET, marketData, snapshot, System.currentTimeMillis());

        verify(mockCrossExecutor, never()).executeCrossTrade(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble());
        verify(mockCoordinator, never()).releaseLock(anyString());
    }

    @Test
    @Order(5)
    @DisplayName("❌ Snapshot stale → Trade rechazado (datos obsoletos)")
    void testStaleSnapshot_TradeAborted() throws Exception {
        scanner.setDryRun(false);
        Map<String, Map<String, Double>> marketData = ArbitrageTestFixtures.buildProfitableSpread(
                PAIR, BUY_EXCHANGE, SELL_EXCHANGE, BINANCE_PRICE, 0.8
        );
        BalanceSnapshot snapshot = ArbitrageTestFixtures.buildSingleExchangeSnapshot(BUY_EXCHANGE, "USDT", 1000.0);
        injectSnapshot(snapshot);

        ExchangeConnector.OrderBook buyBook = ArbitrageTestFixtures.buildHighLiquidityBook(BINANCE_PRICE);
        ExchangeConnector.OrderBook sellBook = ArbitrageTestFixtures.buildHighLiquidityBook(BYBIT_PRICE);

        when(mockConnector.fetchOrderBook(anyString(), eq(PAIR), anyInt())).thenReturn(buyBook, sellBook);
        when(mockConnector.calculateWeightedPrice(any(), anyString(), anyDouble())).thenReturn(BINANCE_PRICE, BYBIT_PRICE);

        when(mockCoordinator.tryAcquireDualLock(BUY_EXCHANGE, SELL_EXCHANGE)).thenReturn(true);
        when(mockCoordinator.isSnapshotStale(eq(BUY_EXCHANGE), anyLong())).thenReturn(true);

        invokeAnalyzeSpatialSpread(ASSET, marketData, snapshot, System.currentTimeMillis());

        verify(mockCrossExecutor, never()).executeCrossTrade(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble());
        // Ahora sí se llamará porque los exchanges coinciden y el scanner avanza hasta el lock
        verify(mockCoordinator, times(1)).releaseLock(BUY_EXCHANGE);
        verify(mockCoordinator, times(1)).releaseLock(SELL_EXCHANGE);
    }

    // =========================================================================
    // 🔄 MODO DRY RUN
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("🔄 Modo DRY_RUN → Contabiliza profit sin ejecutar")
    void testDryRunMode_CountsProfitWithoutExecution() throws Exception {
        scanner.setDryRun(true);

        Map<String, Map<String, Double>> marketData = ArbitrageTestFixtures.buildProfitableSpread(
                PAIR, BUY_EXCHANGE, SELL_EXCHANGE, BINANCE_PRICE, 0.8
        );
        BalanceSnapshot snapshot = ArbitrageTestFixtures.buildSingleExchangeSnapshot(BUY_EXCHANGE, "USDT", 1000.0);
        injectSnapshot(snapshot);

        ExchangeConnector.OrderBook buyBook = ArbitrageTestFixtures.buildHighLiquidityBook(BINANCE_PRICE);
        ExchangeConnector.OrderBook sellBook = ArbitrageTestFixtures.buildHighLiquidityBook(BYBIT_PRICE);

        when(mockConnector.fetchOrderBook(anyString(), eq(PAIR), anyInt())).thenReturn(buyBook, sellBook);
        when(mockConnector.calculateWeightedPrice(any(), anyString(), anyDouble())).thenReturn(BINANCE_PRICE, BYBIT_PRICE);

        invokeAnalyzeSpatialSpread(ASSET, marketData, snapshot, System.currentTimeMillis());

        verify(mockCrossExecutor, never()).executeCrossTrade(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble());
        verify(mockCoordinator, never()).tryAcquireDualLock(anyString(), anyString());

        assertThat(scanner.getTradesCount()).isPositive();
        assertThat(scanner.getTotalPotentialProfit()).isPositive();
    }

    // =========================================================================
    // ⚡ CACHÉ DE ORDERBOOK
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("⚡ Caché OrderBook → Segunda llamada usa caché (no red)")
    void testOrderBookCache_ReducesNetworkCalls() throws Exception {
        scanner.setDryRun(true);
        ExchangeConnector.OrderBook book = ArbitrageTestFixtures.buildHighLiquidityBook(BINANCE_PRICE);
        when(mockConnector.fetchOrderBook(eq(BUY_EXCHANGE), eq(PAIR), anyInt())).thenReturn(book);

        Method fetchMethod = DeepMarketScanner.class.getDeclaredMethod("fetchOrderBookCached", String.class, String.class, int.class);
        fetchMethod.setAccessible(true);

        ExchangeConnector.OrderBook result1 = (ExchangeConnector.OrderBook) fetchMethod.invoke(scanner, BUY_EXCHANGE, PAIR, 20);
        ExchangeConnector.OrderBook result2 = (ExchangeConnector.OrderBook) fetchMethod.invoke(scanner, BUY_EXCHANGE, PAIR, 20);

        assertThat(result1).isSameAs(result2);
        verify(mockConnector, times(1)).fetchOrderBook(eq(BUY_EXCHANGE), eq(PAIR), anyInt());
    }

    @Test
    @Order(8)
    @DisplayName("⚡ Caché expirado → Nueva consulta a red tras TTL")
    void testOrderBookCache_ExpiresAfterTTL() throws Exception {
        scanner.setDryRun(true);
        ExchangeConnector.OrderBook book = ArbitrageTestFixtures.buildHighLiquidityBook(BINANCE_PRICE);
        when(mockConnector.fetchOrderBook(eq(BUY_EXCHANGE), eq(PAIR), anyInt())).thenReturn(book);

        Method fetchMethod = DeepMarketScanner.class.getDeclaredMethod("fetchOrderBookCached", String.class, String.class, int.class);
        fetchMethod.setAccessible(true);

        fetchMethod.invoke(scanner, BUY_EXCHANGE, PAIR, 20);
        TimeUnit.MILLISECONDS.sleep(2100);
        fetchMethod.invoke(scanner, BUY_EXCHANGE, PAIR, 20);

        verify(mockConnector, times(2)).fetchOrderBook(eq(BUY_EXCHANGE), eq(PAIR), anyInt());
    }

    // =========================================================================
    // 🧪 VALIDACIÓN DE CÁLCULOS
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("💰 Profit neto calculado correctamente considerando fees")
    void testNetProfitCalculation_IncludesFees() throws Exception {
        scanner.setDryRun(true);

        double buyPrice = 200.0;
        double sellPrice = 204.0;
        double capital = 100.0;
        double fee = 0.001;

        Map<String, Map<String, Double>> marketData = Map.of(
                BUY_EXCHANGE, Map.of(PAIR, buyPrice),
                SELL_EXCHANGE, Map.of(PAIR, sellPrice) // SELL_EXCHANGE ahora es "bybit", así que el scanner lo verá
        );

        BalanceSnapshot snapshot = ArbitrageTestFixtures.buildSingleExchangeSnapshot(BUY_EXCHANGE, "USDT", capital);
        injectSnapshot(snapshot);

        // ✅ CORRECCIÓN: Usamos las variables locales correctas
        ExchangeConnector.OrderBook buyBook = ArbitrageTestFixtures.buildHighLiquidityBook(buyPrice);
        ExchangeConnector.OrderBook sellBook = ArbitrageTestFixtures.buildHighLiquidityBook(sellPrice);

        when(mockConnector.fetchOrderBook(anyString(), eq(PAIR), anyInt())).thenReturn(buyBook, sellBook);
        when(mockConnector.calculateWeightedPrice(eq(buyBook), eq("BUY"), anyDouble())).thenReturn(buyPrice);
        when(mockConnector.calculateWeightedPrice(eq(sellBook), eq("SELL"), anyDouble())).thenReturn(sellPrice);

        double profitBefore = scanner.getTotalPotentialProfit();
        invokeAnalyzeSpatialSpread(ASSET, marketData, snapshot, System.currentTimeMillis());
        double profitAfter = scanner.getTotalPotentialProfit();

        double qty = capital / buyPrice;
        double sellRevenue = qty * sellPrice * (1 - fee);
        double buyCost = capital + (capital * fee);
        double expectedProfit = sellRevenue - buyCost;
        double actualProfit = profitAfter - profitBefore;

        assertThat(actualProfit).isCloseTo(expectedProfit, within(0.01));
    }

    // =========================================================================
    // 🛠️ HELPERS
    // =========================================================================
    private void injectSnapshot(BalanceSnapshot snapshot) throws Exception {
        Field field = DeepMarketScanner.class.getDeclaredField("currentSnapshot");
        field.setAccessible(true);
        field.set(scanner, snapshot);
    }

    private void injectMockExecutor() {
        try {
            Field field = DeepMarketScanner.class.getDeclaredField("crossExecutor");
            field.setAccessible(true);
            field.set(scanner, mockCrossExecutor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject CrossExecutor mock", e);
        }
    }

    private void injectMockFeeManager() {
        try {
            Field field = DeepMarketScanner.class.getDeclaredField("feeManager");
            field.setAccessible(true);
            field.set(scanner, mockFeeManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject FeeManager mock", e);
        }
    }

    private void invokeAnalyzeSpatialSpread(String asset, Map<String, Map<String, Double>> marketData, BalanceSnapshot snapshot, long timestamp) throws Exception {
        Method method = DeepMarketScanner.class.getDeclaredMethod("analyzeSpatialSpread", String.class, Map.class, BalanceSnapshot.class, long.class);
        method.setAccessible(true);
        method.invoke(scanner, asset, marketData, snapshot, timestamp);
    }
}