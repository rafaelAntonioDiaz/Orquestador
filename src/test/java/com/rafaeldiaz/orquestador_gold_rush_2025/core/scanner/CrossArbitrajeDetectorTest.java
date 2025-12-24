package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.CrossTradeExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager; // ✅ Importar el Policía
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrossTradeExecutorTest {

    @Test
    @DisplayName("Debe intentar disparar órdenes paralelas a MEXC y Binance")
    void testParallelExecution() {
        System.out.println("--- 🔫 TEST DE EJECUCIÓN PARALELA ---");

        // 1. Preparamos el Conector
        ExchangeConnector connector = new ExchangeConnector();

        // 2. Preparamos el RiskManager (Requisito Nuevo)
        // Le damos un capital ficticio de $1000 para que no bloquee el test
        RiskManager riskPolice = new RiskManager(1000.0);

        // 3. Instanciamos el Ejecutor con sus DOS dependencias
        CrossTradeExecutor executor = new CrossTradeExecutor(connector, riskPolice);

        // Aseguramos que sea DryRun para no gastar dinero real en un JUnit
        executor.setDryRun(true);

        // Simulamos una orden: Comprar en MEXC, Vender en Binance
        // Par: BTCUSDT, Precio Compra: 50000, Precio Venta: 51000
        executor.executeCrossTrade("mexc", "binance", "BTCUSDT", 50000.0, 51000.0);

        System.out.println("--- FIN DEL TEST ---");
        // Deberías ver en los logs del test: "[DRY-RUN] Simulación Cross-Exchange..."
    }
}