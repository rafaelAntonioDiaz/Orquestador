package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeExecutorTest {

    @Mock
    private ExchangeConnector mockConnector;

    private TradeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TradeExecutor(mockConnector);
        executor.setDryRun(false); // Queremos probar la lógica real
    }

    @Test
    @DisplayName("ÉXITO: Debe completar las 3 patas si el mercado sigue siendo rentable")
    void testExecuteTriangularSuccess() throws IOException {
        // Arrange
        when(mockConnector.fetchBalance(anyString())).thenReturn(5000.0);
        when(mockConnector.placeOrder(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn("{\"orderId\":\"111\"}");

        // MOCK DE PRECIOS RENTABLES (Para pasar el checkMidTrade)
        // USDT -> BTC (50k) -> SOL (0.002 BTC) -> USDT (105)
        // Costo SOL implícito: 50000 * 0.002 = 100 USDT. Venta: 105 USDT. ¡Ganancia!
        when(mockConnector.fetchPrice(anyString(), eq("BTCUSDT"))).thenReturn(50000.0);
        when(mockConnector.fetchPrice(anyString(), eq("SOLBTC"))).thenReturn(0.002);
        when(mockConnector.fetchPrice(anyString(), eq("SOLUSDT"))).thenReturn(105.0);

        // Act
        executor.executeTriangular("BTC", "SOL", 100.0);

        // Assert
        // Pata 1: Buy BTC
        verify(mockConnector).placeOrder(anyString(), eq("BTCUSDT"), eq("Buy"), anyString(), anyDouble(), anyDouble());
        // Pata 2: Buy SOL (Debe ocurrir porque es rentable)
        verify(mockConnector).placeOrder(anyString(), eq("SOLBTC"), eq("Buy"), anyString(), anyDouble(), anyDouble());
        // Pata 3: Sell SOL
        verify(mockConnector).placeOrder(anyString(), eq("SOLUSDT"), eq("Sell"), anyString(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("ABORTAR: Debe revertir y detenerse si el profit colapsa a mitad de camino")
    void testAbortMidTrade() throws IOException {
        // Arrange
        when(mockConnector.fetchBalance(anyString())).thenReturn(5000.0);
        // Simulamos respuesta de orden OK
        when(mockConnector.placeOrder(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn("{\"orderId\":\"999\"}");

        // MOCK DE PRECIOS DE PÁNICO (El mercado se cae)
        // USDT -> BTC (50k).
        // Pero de repente SOL se desploma a 90 USDT (mientras costaba 100 vía BTC).
        // Costo: 100. Venta: 90. Pérdida del 10%.
        when(mockConnector.fetchPrice(anyString(), eq("BTCUSDT"))).thenReturn(50000.0);
        when(mockConnector.fetchPrice(anyString(), eq("SOLBTC"))).thenReturn(0.002);
        when(mockConnector.fetchPrice(anyString(), eq("SOLUSDT"))).thenReturn(90.0); // <--- COLAPSO

        // Act
        executor.executeTriangular("BTC", "SOL", 100.0);

        // Assert
        // 1. Pata 1: Buy BTC (Se ejecuta siempre al inicio)
        verify(mockConnector).placeOrder(anyString(), eq("BTCUSDT"), eq("Buy"), anyString(), anyDouble(), anyDouble());

        // 2. REVERSIÓN: Debe vender el BTC de vuelta a USDT (Sell BTCUSDT)
        verify(mockConnector).placeOrder(anyString(), eq("BTCUSDT"), eq("Sell"), anyString(), anyDouble(), anyDouble());

        // 3. SEGURIDAD: NO debe haber intentado comprar SOL (Pata 2)
        verify(mockConnector, never()).placeOrder(anyString(), eq("SOLBTC"), anyString(), anyString(), anyDouble(), anyDouble());

        // 4. SEGURIDAD: NO debe haber intentado vender SOL (Pata 3)
        verify(mockConnector, never()).placeOrder(anyString(), eq("SOLUSDT"), anyString(), anyString(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("NO debe operar si el saldo es insuficiente")
    void testInsufficientFunds() throws IOException {
        when(mockConnector.fetchBalance(anyString())).thenReturn(10.0); // Pobreza

        executor.executeTriangular("BTC", "SOL", 100.0);

        verify(mockConnector, never()).placeOrder(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble());
    }
}