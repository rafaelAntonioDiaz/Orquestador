package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 🧭 DYNAMIC PAIR SELECTOR TEST (Inteligencia de Mercado)
 * Valida la lógica de selección de pares basada en ATR, Liquidez y Spread.
 * Usa Reflection para inspeccionar el Record privado 'OpportunityScore'.
 */
class DynamicPairSelectorTest {

    @Mock
    private ExchangeConnector mockConnector;
    @Mock
    private MarketListener mockListener; // Interfaz simple
    @Mock
    private FeeManager mockFeeManager;
    @Mock
    private PortfolioHealthManager mockCfo;

    private DynamicPairSelector selector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        selector = new DynamicPairSelector(mockConnector, mockListener, mockFeeManager, mockCfo);

        // Configuración Default segura
        when(mockFeeManager.getTradingFee(anyString(), anyString(), anyString())).thenReturn(0.001); // 0.1%
    }

    @Test
    @DisplayName("🔥 HOT MARKET: Debe calificar alto a un par volátil y líquido")
    void shouldScoreHigh_ForHotMarket() throws Exception {
        // ESCENARIO: SOLUSDT (Pump mode)
        String pair = "SOLUSDT";

        // 1. Mock Candles (Alta Volatilidad)
        List<double[]> candles = new ArrayList<>();
        for(int i=0; i<5; i++) {
            candles.add(new double[]{ 102.0, 98.0, 101.0 }); // Size 3 CORRECTO
        }
        when(mockConnector.fetchCandles(anyString(), eq(pair), anyString(), anyInt())).thenReturn(candles);

        // 2. Mock OrderBook (Spread ajustado y MUCHA liquidez)
        // 🔥 AJUSTE: Subimos size a 5000.0 para que Liquidez sea $500k (Score Liq = 1.0)
        mockOrderBook(pair, 100.0, 100.5, 5000.0);

        // 3. Ejecución
        Object opportunity = invokeAnalyze(pair);

        // 4. Validación
        assertThat(opportunity).as("Un mercado caliente no debe ser ignorado").isNotNull();

        double score = (double) getRecordField(opportunity, "score");

        System.out.println("🔥 HOT SCORE: " + score); // Ahora debería dar ~0.57
        assertThat(score).as("El score debe ser alto (> 0.5)").isGreaterThan(0.5);
    }

    @Test
    @DisplayName("🧟 ZOMBIE MARKET: Debe ignorar pares sin volatilidad")
    void shouldIgnore_DeadMarket() throws Exception {
        // ESCENARIO: Stablecoin
        String pair = "USDCUSDT";

        // High 1.0001, Low 0.9999, Close 1.0. ATR casi 0.
        List<double[]> candles = new ArrayList<>();
        for(int i=0; i<5; i++) {
            candles.add(new double[]{ 1.0001, 0.9999, 1.0 });
        }
        when(mockConnector.fetchCandles(anyString(), eq(pair), anyString(), anyInt())).thenReturn(candles);

        // 2. Ejecución
        Object opportunity = invokeAnalyze(pair);

        // 3. Validación
        assertThat(opportunity).as("Debe retornar NULL para mercados muertos").isNull();
    }

    @Test
    @DisplayName("💧 LOW LIQUIDITY: Debe rechazar trampas de liquidez")
    void shouldReject_LowLiquidity() throws Exception {
        // ESCENARIO: Volátil pero sin fondo
        String pair = "SHITCOINUSDT";

        // Volatilidad Alta
        List<double[]> candles = new ArrayList<>();
        for(int i=0; i<5; i++) {
            candles.add(new double[]{ 11.0, 9.0, 10.0 });
        }
        when(mockConnector.fetchCandles(anyString(), eq(pair), anyString(), anyInt())).thenReturn(candles);

        // OrderBook pobre (Total $1,000 < $15k)
        mockOrderBook(pair, 10.0, 10.1, 100.0);

        // Ejecución
        Object opportunity = invokeAnalyze(pair);

        // Validación
        assertThat(opportunity).as("Debe rechazar liquidez baja").isNull();
    }

    @Test
    @DisplayName("📡 RADAR ROUTINE: Resiliencia ante fallos masivos")
    void shouldRunRadar_WithoutExploding() throws Exception {
        // Simulamos que el connector falla o retorna nulos para todo
        when(mockConnector.fetchCandles(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("API DOWN"));

        // Invocamos la rutina principal (que usa Virtual Threads)
        Method method = DynamicPairSelector.class.getDeclaredMethod("executeRadarRoutine");
        method.setAccessible(true);

        // No debe lanzar excepción hacia afuera (debe capturar y loguear)
        try {
            method.invoke(selector);
            System.out.println("✅ RADAR STABILITY: Sobrevivió a fallos de API.");
        } catch (Exception e) {
            // Si llega aquí, es porque la excepción no fue manejada internamente
            throw e;
        }
    }

    // =========================================================================
    // 🛠️ REFLECTION HELPERS (Para acceder a la Caja Negra)
    // =========================================================================

    private Object invokeAnalyze(String pair) throws Exception {
        Method method = DynamicPairSelector.class.getDeclaredMethod("analyzeMarketCandidate", String.class);
        method.setAccessible(true);
        return method.invoke(selector, pair);
    }

    private Object getRecordField(Object record, String fieldName) throws Exception {
        if (record == null) return null;
        Method accessor = record.getClass().getMethod(fieldName);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }

    private void mockOrderBook(String pair, double bid, double ask, double bidSize) {
        // Creamos estructura compatible con ExchangeConnector.OrderBook (List<double[]>)
        ExchangeConnector.OrderBook book = new ExchangeConnector.OrderBook(
                Collections.singletonList(new double[]{bid, bidSize}), // Bids
                Collections.singletonList(new double[]{ask, bidSize})  // Asks
        );
        when(mockConnector.fetchOrderBook(anyString(), eq(pair), anyInt())).thenReturn(book);
    }
}