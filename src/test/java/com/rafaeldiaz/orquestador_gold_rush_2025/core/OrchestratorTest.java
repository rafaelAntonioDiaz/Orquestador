package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.*;
import org.junit.jupiter.api.Test;
import java.util.List;

class OrchestratorTest {

    @Test
    void testOrquestadorEnVivo() throws InterruptedException {
        // 1. Preparamos los motores
        ExchangeConnector connector = new ExchangeConnector(); // Usa tus credenciales reales
        List<ExchangeStrategy> strategies = List.of(
                new BybitStrategy(connector),
                new BinanceStrategy()
        );

        // 2. Iniciamos el Orquestador
        // CORRECCIÓN: Ahora pasamos (strategies, connector) porque el Orquestador necesita el conector para los Fees.
        GoldRushOrchestrator orchestrator = new GoldRushOrchestrator(strategies, connector);

        orchestrator.startSurveillance();

        // 3. Dejamos correr la vigilancia por 1 minuto
        System.out.println("⏳ Escaneando mercados por 60 segundos...");
        Thread.sleep(60000);

        // 4. Apagamos
        orchestrator.stop();
    }
}