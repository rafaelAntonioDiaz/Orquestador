package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 🚀 Menos neurótico, más efectivo
class TradeExecutorTest {
    @Mock private ExchangeConnector mockConnector;
    private TradeExecutor executor;
    private final double REAL_BALANCE = 224.0;

    @BeforeEach void setUp() {
        executor = new TradeExecutor(mockConnector);
        executor.setDryRun(false);
    }

    @Test
    @DisplayName("✅ SUCCESS: USDT -> BTC -> SOL -> USDT (Real Proof)")
    void testExecuteTriangularSuccess() {
        when(mockConnector.fetchBalance(anyString(), eq("USDT"))).thenReturn(REAL_BALANCE);
        when(mockConnector.fetchPrice(anyString(), anyString())).thenReturn(50000.0, 0.002, 110.0);
        // Damos balances intermedios para que el bot pueda completar el ciclo
        when(mockConnector.fetchBalance(anyString(), eq("BTC"))).thenReturn(0.00448);
        when(mockConnector.fetchBalance(anyString(), eq("SOL"))).thenReturn(2.24);
        when(mockConnector.placeOrder(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble())).thenReturn("OK");

        executor.executeTriangular("BTC", "SOL", REAL_BALANCE);
        verify(mockConnector, times(3)).placeOrder(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble());
    }


    @Test
    @DisplayName("🚨 ABORT: Reversión real ante colapso de precio")
    void testAbortMidTrade() {
        // ARRANGE
        when(mockConnector.fetchBalance(anyString(), eq("USDT"))).thenReturn(224.0);
        when(mockConnector.fetchPrice(anyString(), eq("BTCUSDT"))).thenReturn(50000.0);
        when(mockConnector.fetchPrice(anyString(), eq("SOLBTC"))).thenReturn(0.0); // 📉 Trigger

        // Clave: El bot necesita ver que compró BTC para intentar venderlo
        when(mockConnector.placeOrder(anyString(), eq("BTCUSDT"), eq("BUY"), anyString(), anyDouble(), anyDouble()))
                .thenReturn("ORD-1");

        // Mock del retorno de la venta de emergencia
        when(mockConnector.placeOrder(anyString(), eq("BTCUSDT"), eq("SELL"), anyString(), anyDouble(), anyDouble()))
                .thenReturn("RECOVERY-ID");

        // ACT
        executor.executeTriangular("BTC", "SOL", 224.0);

        // ASSERT: ¡Ahora sí Mockito verá el SELL!
        verify(mockConnector, atLeastOnce()).placeOrder(anyString(), eq("BTCUSDT"), eq("SELL"), anyString(), anyDouble(), anyDouble());
    }}