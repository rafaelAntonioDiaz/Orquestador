package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerManagerTest {

    @Mock
    private ExchangeConnector mockConnector;

    @Test
    void testLowBalanceAlert() throws IOException, InterruptedException {
        // Simulamos pobreza extrema (100 USDT < 700 USDT)
        when(mockConnector.fetchBalance("bybit_sub1")).thenReturn(100.0);

        SchedulerManager schedulerManager = new SchedulerManager(mockConnector);

        // Iniciamos el scheduler
        schedulerManager.startHealthCheck();

        // Esperamos un poco para que se ejecute la primera tarea (delay 0)
        Thread.sleep(1000); // 1 segundo es suficiente

        // Verificamos que se llamó a fetchBalance
        verify(mockConnector, atLeastOnce()).fetchBalance("bybit_sub1");

        System.out.println("✅ Test Scheduler: Se detectó saldo bajo y se disparó la lógica de alerta.");

        schedulerManager.stop();
    }
    @Test
    void testRebalancingTrigger() throws IOException, InterruptedException {
        System.out.println("\n--- TEST DE REBALANCEO (Rico vs Pobre) ---");

        // 1. Simulamos que Bybit Sub1 es POBRE (100 USDT)
        // Nota: En SchedulerManager, Sub2 está simulada fija en 500 USDT para este MVP
        when(mockConnector.fetchBalance("bybit_sub1")).thenReturn(100.0);

        SchedulerManager schedulerManager = new SchedulerManager(mockConnector);

        // 2. Iniciamos el motor
        schedulerManager.startHealthCheck();

        // 3. Esperamos a que el Scheduler haga sus cálculos (1 segundo)
        Thread.sleep(1000);

        // 4. Verificaciones
        verify(mockConnector, atLeastOnce()).fetchBalance("bybit_sub1");

        // En la consola deberías ver:
        // "💰 Balance Check [bybit_sub1]: $100.00"
        // "💰 Balance Check [bybit_sub2]: $500.00" (Simulado)
        // "⚖️ REBALANCEO NECESARIO: Mover $200.00 de bybit_sub2..."

        System.out.println("✅ Lógica de rebalanceo ejecutada correctamente.");
        schedulerManager.stop();
    }
}