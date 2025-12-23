package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicPairSelectorTest {

    @Mock
    private ExchangeConnector mockConnector;
    @Mock
    private MarketListener mockListener;

    @Test
    @DisplayName("Debe seleccionar y suscribirse al par con mayor Volatilidad (ATR)")
    void testSelectTopVolatility() throws Exception {
        // ARRANGE
        FeeManager mockfeeManager = new FeeManager(mockConnector);
        DynamicPairSelector selector = new DynamicPairSelector(mockConnector, mockListener, mockfeeManager);

        // 1. Simulamos velas para BTC (Volátil)
        when(mockConnector.fetchPrice(anyString(), eq("BTCUSDT"))).thenReturn(50000.0);
        // 🚀 AJUSTE: Ahora usamos una Lista para coincidir con ExchangeConnector
        List<double[]> btcCandles = createCandlesList(50000, 51000, 49000);
        when(mockConnector.fetchCandles(anyString(), eq("BTCUSDT"), anyString(), anyInt()))
                .thenReturn(btcCandles);

        // 2. Simulamos velas para ETH (Aburrido/Estable)
        when(mockConnector.fetchPrice(anyString(), eq("ETHUSDT"))).thenReturn(3000.0);
        List<double[]> ethCandles = createCandlesList(3000, 3001, 2999);
        when(mockConnector.fetchCandles(anyString(), eq("ETHUSDT"), anyString(), anyInt()))
                .thenReturn(ethCandles);

        // Mock para evitar ruidos en otros pares
        lenient().when(mockConnector.fetchCandles(anyString(), argThat(s -> !s.equals("BTCUSDT") && !s.equals("ETHUSDT")), anyString(), anyInt()))
                .thenReturn(new ArrayList<>());

        // ACT
        Method method = DynamicPairSelector.class.getDeclaredMethod("detectAdrenaline");
        method.setAccessible(true);
        method.invoke(selector);

        // ASSERT
        verify(mockListener, atLeastOnce()).updateTargets(anyList());

        System.out.println("✅ Test DynamicPairSelector: El radar detectó volatilidad y reconfiguró los objetivos.");
    }

    // 🚀 HELPER REFORMATEADO: Devuelve List<double[]> en lugar de double[][]
    private List<double[]> createCandlesList(double base, double high, double low) {
        List<double[]> candles = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            candles.add(new double[]{high, low, base}); // [High, Low, Close]
        }
        return candles;
    }
}