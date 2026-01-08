package com.rafaeldiaz.orquestador_gold_rush_2025.core.main;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.GoldRushOrchestrator;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import static com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger.*; // Import estático para los colores

/**
 * 🚪 ENTRY POINT - AGENTE TOKIO (NEON EDITION)
 * La puerta de entrada al Ciberespacio Financiero.
 */
public class Main {

    public static void main(String[] args) {
        try {
            // 1. ARTE ASCII CIBERNÉTICO (Con Neón Cyan y Púrpura)
            System.out.println("\n");
            System.out.println(CYAN + "   ██████╗  ██████╗ ██╗     ██████╗     ██████╗ ██╗   ██╗███████╗██╗  ██╗" + RESET);
            System.out.println(CYAN + "  ██╔════╝ ██╔═══██╗██║     ██╔══██╗    ██╔══██╗██║   ██║██╔════╝██║  ██║" + RESET);
            System.out.println(CYAN + "  ██║  ███╗██║   ██║██║     ██║  ██║    ██████╔╝██║   ██║███████╗███████║" + RESET);
            System.out.println(PURPLE + "  ██║   ██║██║   ██║██║     ██║  ██║    ██╔══██╗██║   ██║╚════██║██╔══██║" + RESET);
            System.out.println(PURPLE + "  ╚██████╔╝╚██████╔╝███████╗██████╔╝    ██║  ██║╚██████╔╝███████║██║  ██║" + RESET);
            System.out.println(PURPLE + "   ╚═════╝  ╚═════╝ ╚══════╝╚═════╝     ╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝" + RESET);
            System.out.println(WHITE_BOLD + "           ⚡ AGENTE TOKIO | JAVA 25 BARE METAL | v4.5 STABLE ⚡" + RESET);
            System.out.println("\n");

            BotLogger.info(CYAN + "🔌 INICIALIZANDO SISTEMAS NEURONALES..." + RESET);

            // 2. Infraestructura Base
            long t1 = System.currentTimeMillis();
            ExchangeConnector connector = new ExchangeConnector();
            ExecutionCoordinator coordinator = new ExecutionCoordinator();
            long t2 = System.currentTimeMillis();

            BotLogger.info(GREEN + "   [✔] ENLACE DE RED ESTABLECIDO (" + (t2 - t1) + "ms)" + RESET);

            // 3. Contratación del Director
            GoldRushOrchestrator director = new GoldRushOrchestrator(connector, coordinator);
            BotLogger.info(GREEN + "   [✔] AGENTE TOKIO: EN LÍNEA" + RESET);

            // 4. Configurar Shutdown Hook (Elegante y Seguro)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n" + RED + "🛑 SIGNAL RECIBIDA: INICIANDO PROTOCOLO DE DESCONEXIÓN..." + RESET);
                director.triggerEmergencyStop();
                try { Thread.sleep(2000); } catch (InterruptedException e) {}
            }));

            // 5. Transferencia de Mando
            BotLogger.info(YELLOW + "⚠️  ADVERTENCIA: CEDIENDO CONTROL AL NÚCLEO AUTÓNOMO." + RESET);
            BotLogger.info(WHITE_BOLD + "🚀 EJECUTANDO START_MISSION()..." + RESET);

            director.startMission();

            // 6. Salida Limpia
            System.exit(0);

        } catch (Exception e) {
            System.err.println(RED + "🔥 FALLO CRÍTICO DE ARRANQUE: " + e.getMessage() + RESET);
            e.printStackTrace();
            System.exit(1);
        }
    }
}