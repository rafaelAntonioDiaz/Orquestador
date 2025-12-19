package com.rafaeldiaz.orquestador_gold_rush_2025;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
// IMPORTANTE: Importamos la implementación CONCRETA
import com.rafaeldiaz.orquestador_gold_rush_2025.connect.BybitStreamer;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.ArbitrageDetector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.DynamicPairSelector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.MarketListener;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.SchedulerManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 🏛️ ORQUESTADOR GOLD RUSH 2025 - MAIN ENGINE 🏛️
 * Optimizado para Java 25 | Virtual Threads | Soft Start
 */
public class Main {

    public static void main(String[] args) {
        printBanner();

        // Notificación de arranque
        BotLogger.sendTelegram("🚀 ChasquiTokio v2.0 INICIADO. Motores calentando...");

        try {
            // 1. NUCLEO: Conector Maestro
            var connector = new ExchangeConnector();
            BotLogger.info("✅ [1/5] ExchangeConnector: ONLINE");
            wait(1); // Soft Start

            // 2. SALUD: Gestor Contable
            var scheduler = new SchedulerManager(connector);
            scheduler.startHealthCheck();
            BotLogger.info("✅ [2/5] SchedulerManager: ONLINE");
            wait(1);

            // 3. SISTEMA TRIANGULAR (Bybit High-Frequency)
            // 🔥 CORRECCIÓN AQUÍ: Instanciamos la clase CONCRETA (BybitStreamer)
            // pero la guardamos en una variable 'var' (inferncia de tipos)
            var streamer = new BybitStreamer();

            var triangularDetector = new ArbitrageDetector(connector);

            // Conexión Neural: Oído (Streamer) -> Cerebro (Detector)
            streamer.addListener(triangularDetector);

            // Inteligencia: Selector Dinámico
            var pairSelector = new DynamicPairSelector(connector, streamer);
            pairSelector.start();
            BotLogger.info("✅ [3/5] Sistema Triangular (WebSocket V5): ONLINE");
            wait(1);

            // 4. SISTEMA CROSS-EXCHANGE (Arbitraje Lento/Seguro)
            var crossListener = new MarketListener();
            crossListener.startScanning();
            BotLogger.info("✅ [4/5] Radar Cross-Exchange: ONLINE");
            wait(1);

            // 5. ESTADO FINAL
            BotLogger.info("✅ [5/5] SISTEMA AL 100%. ESPERANDO OPORTUNIDADES...");
            BotLogger.info("================================================");

            // Hook de Cierre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n");
                BotLogger.warn("🛑 SEÑAL DE APAGADO RECIBIDA...");
                BotLogger.sendTelegram("🛑 ChasquiTokio APAGADO MANUALMENTE.");
                scheduler.stop();
                marketListenerStop(crossListener);
                pairSelector.stop();
                streamer.stop(); // Apagamos también el WebSocket
            }));

            // Mantener vivo el Main Thread eternamente
            new CountDownLatch(1).await();

        } catch (Exception e) {
            handleCrash(e);
        }
    }

    // --- Helpers de Utilidad y Limpieza ---

    private static void wait(int seconds) {
        try { TimeUnit.SECONDS.sleep(seconds); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void marketListenerStop(MarketListener listener) {
        if (listener != null) listener.stop();
    }

    private static void handleCrash(Exception e) {
        BotLogger.error("🔥 FALLO CRÍTICO DE SISTEMA: " + e.getMessage());
        BotLogger.sendTelegram("🔥 CRASH REPORT: " + e.getMessage());
        e.printStackTrace();
    }

    private static void printBanner() {
        BotLogger.info("================================================");
        BotLogger.info("   🚀 INICIANDO ORQUESTADOR GOLD RUSH 2025 🚀   ");
        BotLogger.info("   Agente: ChasquiTokio | Perfil: AGRESIVO      ");
        BotLogger.info("   ☕ Runtime: " + System.getProperty("java.version"));
        BotLogger.info("================================================");
    }
}