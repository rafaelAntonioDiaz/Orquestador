package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🏎️ PUESTA A PUNTO: Test de Perforación OMNIDIRECCIONAL.
 * Escanea 4 exchanges y múltiples activos simultáneamente.
 */
public class PulseDrillTest {

    @Test
    @DisplayName("🎯 LANZAMIENTO: Deep Scan 15 Minutos")
    void runPulseDrill() throws InterruptedException {
        // 1. Inicializamos
        ExchangeConnector connector = new ExchangeConnector();
        DeepMarketScanner scanner = new DeepMarketScanner(connector);

        // 2. Encendemos el OJO QUE TODO LO VE
        System.out.println("🚀 [IGNICIÓN]: Iniciando Deep Market Scanner (Modo Deva del Mercado,)...");

        // Duración: 15 minutos
        scanner.startOmniScan(15);

        // 3. Mantener vivo el test
        System.out.println("🛰️ Escaneando el multiverso cripto... Observa la consola.");
        Thread.sleep(16 * 60 * 1000); // 16 min para dar margen

        System.out.println("🏁 Test completado.");
    }
}