package com.rafaeldiaz.orquestador_gold_rush_2025;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.*;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.GoldRushOrchestrator;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.List;

/**
 * 🚀 ORQUESTADOR PRINCIPAL - GOLD RUSH 2025 (EDICIÓN MULTI-MOTOR) 🚀
 * Arquitectura: Main -> Ensambla Estrategias -> Inicia Comandante (Orchestrator).
 */
public class Main {

    public static void main(String[] args) {
        BotLogger.info("======================================================");
        BotLogger.info("   🚀 INICIANDO ORQUESTADOR GOLD RUSH 2025 🚀   ");
        BotLogger.info("   Agente: ChasquiTokio | Modo: CAZADOR MULTI-EXCHANGE");
        BotLogger.info("   ☕ Runtime: " + System.getProperty("java.version"));
        BotLogger.info("======================================================");

        try {
            // ------------------------------------------------------------
            // PASO 1: LA ARTERIA PRINCIPAL (Conector)
            // ------------------------------------------------------------
            // Maneja las firmas, las llaves API y las peticiones HTTP seguras.
            ExchangeConnector connector = new ExchangeConnector();
            BotLogger.info("✅ [1/4] Conector Central: ONLINE (IP Verificada)");

            // ------------------------------------------------------------
            // PASO 2: LOS 4 JINETES (Estrategias)
            // ------------------------------------------------------------
            // Instanciamos los adaptadores para cada Exchange.
            List<ExchangeStrategy> strategies = List.of(
                    new BinanceStrategy(),            // API V3 Sólida
                    new BybitStrategy(connector),     // API V5 Unificada
                    new MexcStrategy(connector),      // El Rey de los Fees Bajos
                    new KucoinStrategy(connector)     // El Traductor Universal
            );
            BotLogger.info("✅ [2/4] Motores de Trading: 4/4 ACTIVOS");

            // ------------------------------------------------------------
            // PASO 3: EL COMANDANTE (Orquestador)
            // ------------------------------------------------------------
            // Él crea su propio Radar (DynamicPairSelector) y su Calculadora (ProfitCalculator).
            GoldRushOrchestrator commander = new GoldRushOrchestrator(strategies, connector);
            BotLogger.info("✅ [3/4] Comandante Supremo: LISTO");

            // ------------------------------------------------------------
            // PASO 4: EJECUCIÓN
            // ------------------------------------------------------------
            BotLogger.info("🚀 [4/4] INICIANDO VIGILANCIA... ¡BUENA CAZA!");
            commander.startSurveillance();

            // ------------------------------------------------------------
            // HOOK DE CIERRE (Ctrl+C)
            // ------------------------------------------------------------
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                BotLogger.info("\n🛑 SEÑAL DE APAGADO RECIBIDA...");
                commander.stop(); // Genera el reporte final de ganancias
                BotLogger.info("👋 Hasta la próxima, Ingeniero.");
            }));

            // Mantenemos el hilo principal vivo (aunque el ScheduledExecutor del Orchestrator ya lo hace)
            while (true) {
                Thread.sleep(60000);
            }

        } catch (Exception e) {
            BotLogger.error("🔥 ERROR FATAL EN EL MAIN: " + e.getMessage());
            e.printStackTrace();
        }
    }
}