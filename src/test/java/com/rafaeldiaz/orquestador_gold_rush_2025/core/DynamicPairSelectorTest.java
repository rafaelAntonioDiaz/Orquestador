package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.connect.MarketStreamer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicPairSelectorTest {

    @Mock
    private ExchangeConnector mockConnector;
    @Mock
    private MarketStreamer mockStreamer;

    @Test
    @DisplayName("Debe seleccionar y suscribirse al par con mayor Volatilidad (ATR)")
    void testSelectTopVolatility() throws Exception {
        // ARRANGE
        DynamicPairSelector selector = new DynamicPairSelector(mockConnector, mockStreamer);

        // 1. Simulamos velas para BTC (Volátil)
        // Precio actual: 50,000. ATR simulado alto (diferencia High-Low grande)
        when(mockConnector.fetchPrice(anyString(), eq("BTCUSDT"))).thenReturn(50000.0);
        double[][] btcCandles = createCandles(50000, 51000, 49000); // 2000 diferencia -> ATR alto
        when(mockConnector.fetchCandles(anyString(), eq("BTCUSDT"), anyString(), anyInt()))
                .thenReturn(btcCandles);

        // 2. Simulamos velas para ETH (Aburrido/Estable)
        // Precio actual: 3000. ATR simulado bajo (diferencia mínima)
        when(mockConnector.fetchPrice(anyString(), eq("ETHUSDT"))).thenReturn(3000.0);
        double[][] ethCandles = createCandles(3000, 3001, 2999); // 2 diferencia -> ATR bajo
        when(mockConnector.fetchCandles(anyString(), eq("ETHUSDT"), anyString(), anyInt()))
                .thenReturn(ethCandles);

        // Mock para otros candidatos (return vacío para que los ignore)
        when(mockConnector.fetchCandles(anyString(), matches("^(?!BTCUSDT|ETHUSDT).*$"), anyString(), anyInt()))
                .thenReturn(new double[0][0]);

        // ACT
        // Usamos Reflexión para invocar el método privado "evaluatePairs"
        Method method = DynamicPairSelector.class.getDeclaredMethod("evaluatePairs");
        method.setAccessible(true);
        method.invoke(selector);

        // ASSERT
        // El bot debería haber gritado "HOT PAIR" para BTC y haberse suscrito
        verify(mockStreamer).subscribe("BTCUSDT");

        // NO debería haberse suscrito a ETH (porque su score fue patético comparado con BTC)
        // Nota: En tu lógica actual suscribes al TOP 3. Como solo 2 tienen datos válidos aquí
        // y uno es mucho mejor, verificamos que AL MENOS BTC fue llamado.
    }

    // Helper para crear velas dummy [High, Low, Close]
    private double[][] createCandles(double base, double high, double low) {
        double[][] candles = new double[14][3];
        for (int i = 0; i < 14; i++) {
            candles[i][0] = high; // High
            candles[i][1] = low;  // Low
            candles[i][2] = base; // Close
        }
        return candles;
    }
}