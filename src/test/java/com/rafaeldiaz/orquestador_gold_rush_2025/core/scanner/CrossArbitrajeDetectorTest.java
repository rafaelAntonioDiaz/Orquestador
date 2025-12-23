package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrossTradeExecutorTest {

    @Test
    @DisplayName("Debe intentar disparar órdenes paralelas a MEXC y Binance")
    void testParallelExecution() {
        System.out.println("--- 🔫 TEST DE EJECUCIÓN PARALELA ---");

        CrossTradeExecutor executor = new CrossTradeExecutor(new ExchangeConnector());

        // Simulamos una orden: Comprar en MEXC, Vender en Binance
        // Par: BTCUSDT, Precio Compra: 50000, Precio Venta: 51000
        executor.executeCrossTrade("mexc", "binance", "BTCUSDT", 50000.0, 51000.0);

        System.out.println("--- FIN DEL TEST ---");
        // Deberías ver en los logs dos intentos de disparo con latencias muy bajas.
    }
}