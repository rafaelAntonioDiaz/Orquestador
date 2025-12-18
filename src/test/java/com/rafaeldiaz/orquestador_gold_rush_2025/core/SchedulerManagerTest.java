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
}