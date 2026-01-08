package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 🧠 TEST DE INTELIGENCIA DE MERCADO
 * Verifica que el Cerebro (Selector) controle correctamente al Corazón (Scanner)
 * basándose en el inventario real.
 */
class MarketIntelligenceTest {

    @Mock private ExchangeConnector connector;
    @Mock private ExecutionCoordinator coordinator;
    @Mock private FeeManager feeManager;
    @Mock private PortfolioHealthManager cfo;

    private DeepMarketScanner scanner;
    private DynamicPairSelector selector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Instanciamos el sistema real
        // Nota: DeepMarketScanner crea sus propios helpers internos,
        // pero para testear la integración "Watchdog -> Scanner",
        // nos enfocaremos en la inyección de targets.

        scanner = new DeepMarketScanner(connector, coordinator);

        // Inyectamos el Scanner como listener del Selector
        selector = new DynamicPairSelector(connector, scanner, feeManager, cfo);
    }

    @Test
    @DisplayName("🐕 WATCHDOG: Debe actualizar 'Hunting Grounds' basado en el inventario")
    void testWatchdogUpdatesHuntingGrounds() throws InterruptedException {
        // 1. ESCENARIO: Tenemos saldo en BTC y USDT, pero NO en ETH.
        List<String> walletAssets = List.of("BTC", "USDT");

        // Mock del CFO: Cuando le pregunten, dirá que solo esos dos son operables
        when(cfo.discoverTradableAssets()).thenReturn(walletAssets);

        System.out.println("🤖 Iniciando Cerebro (Selector)...");
        selector.start();

        // 2. ESPERA: Damos tiempo al Watchdog (corre en hilo virtual) para despertar
        // El delay inicial es 0, así que debería ser rápido.
        System.out.println("⏳ Esperando sincronización Watchdog -> Scanner...");
        Thread.sleep(1000); // 1 segundo es suficiente para la VM local

        // 3. VERIFICACIÓN INDIRECTA
        // Lamentablemente 'huntingGrounds' es privado en DeepMarketScanner.
        // Pero podemos verificar si el método 'updateTargets' fue llamado.
        // Como 'scanner' es real (no mock), no podemos usar verify(scanner).
        // PERO, podemos usar un truco: Reflection o Log Inspection.

        // Mejor enfoque para test limpio:
        // Vamos a Mockear el Scanner para verificar que el Selector le grita las órdenes.

        MarketListener mockListener = mock(MarketListener.class);
        DynamicPairSelector testSelector = new DynamicPairSelector(connector, mockListener, feeManager, cfo);

        testSelector.start();
        Thread.sleep(500); // Esperar al hilo virtual

        // VERIFICACIÓN: ¿El Selector le dijo al Listener (Scanner) qué buscar?
        verify(mockListener, atLeastOnce()).updateTargets(walletAssets);

        System.out.println("✅ PRUEBA PASADA: El Selector ordenó escanear solo [BTC, USDT].");
        testSelector.stop();
    }
}