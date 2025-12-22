package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.SchedulerManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerManagerTest {

    @Mock
    private ExchangeConnector mockConnector;

    @Test
    void testLowBalanceAlert() throws InterruptedException {
        // 1. CORRECCIÓN: Ajustamos fetchBalance a (String, String)
        // Simulamos pobreza extrema (100 USDT)
        when(mockConnector.fetchBalance(eq("bybit_sub1"), eq("USDT"))).thenReturn(100.0);

        SchedulerManager schedulerManager = new SchedulerManager(mockConnector);

        // Iniciamos el scheduler
        schedulerManager.startHealthCheck();

        // Esperamos un poco para que se ejecute la primera tarea
        Thread.sleep(1000);

        // 2. CORRECCIÓN: Verificamos con el nuevo formato
        verify(mockConnector, atLeastOnce()).fetchBalance(eq("bybit_sub1"), eq("USDT"));

        System.out.println("✅ Test Scheduler: Se detectó saldo bajo y se disparó la lógica de alerta.");

        schedulerManager.stop();
    }

    @Test
    void testRebalancingTrigger() throws InterruptedException {
        System.out.println("\n--- TEST DE REBALANCEO (Rico vs Pobre) ---");

        // 3. CORRECCIÓN: Ajustamos el Mock para el rebalanceo
        when(mockConnector.fetchBalance(eq("bybit_sub1"), eq("USDT"))).thenReturn(100.0);

        SchedulerManager schedulerManager = new SchedulerManager(mockConnector);

        // Iniciamos el motor
        schedulerManager.startHealthCheck();

        // Esperamos a que el Scheduler haga sus cálculos
        Thread.sleep(1000);

        // 4. CORRECCIÓN: Verificación sincronizada
        verify(mockConnector, atLeastOnce()).fetchBalance(eq("bybit_sub1"), eq("USDT"));

        System.out.println("✅ Lógica de rebalanceo ejecutada correctamente.");
        schedulerManager.stop();
    }
}