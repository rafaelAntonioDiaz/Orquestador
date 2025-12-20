package com.rafaeldiaz.orquestador_gold_rush_2025;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.DynamicPairSelector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.MarketListener;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

/**
 * 🚀 ORQUESTADOR PRINCIPAL - GOLD RUSH 2025 🚀
 * Arquitectura: Cerebro Adrenalina (Selector) -> Controla -> Ojos Rápidos (Listener).
 */
public class Main {

    public static void main(String[] args) {
        BotLogger.info("================================================");
        BotLogger.info("   🚀 INICIANDO ORQUESTADOR GOLD RUSH 2025 🚀   ");
        BotLogger.info("   Agente: ChasquiTokio | Modo: CAZADOR DE VOLATILIDAD");
        BotLogger.info("   ☕ Runtime: " + System.getProperty("java.version"));
        BotLogger.info("================================================");

        try {
            // ------------------------------------------------------------
            // PASO 1: INICIAR LOS OJOS (MarketListener)
            // ------------------------------------------------------------
            // Este componente empieza mirando SOL, AVAX y PEPE por defecto.
            // Es capaz de recibir órdenes para cambiar de objetivo en caliente.
            MarketListener marketListener = new MarketListener();
            marketListener.startScanning();

            BotLogger.info("✅ [1/3] Radar de Mercado (Ojos): ONLINE");

            // ------------------------------------------------------------
            // PASO 2: INICIAR EL CEREBRO (DynamicPairSelector)
            // ------------------------------------------------------------
            // Necesita un conector propio para hacer sus análisis macro.
            // Y necesita acceso al 'marketListener' para decirle qué mirar.
            ExchangeConnector connectorForBrain = new ExchangeConnector();

            DynamicPairSelector adrenalineBrain = new DynamicPairSelector(connectorForBrain, marketListener);
            adrenalineBrain.start();

            BotLogger.info("✅ [2/3] Monitor de Adrenalina (Cerebro): ONLINE");

            // ------------------------------------------------------------
            // PASO 3: MANTENER VIVO EL SISTEMA
            // ------------------------------------------------------------
            BotLogger.info("✅ [3/3] SISTEMA AUTÓNOMO ACTIVADO. ¡BUENA CAZA!");
            BotLogger.info("================================================");

            // Hook para cierre elegante (Ctrl+C)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                BotLogger.info("🛑 Deteniendo sistemas...");
                marketListener.stop();
                adrenalineBrain.stop();
                BotLogger.info("👋 Hasta la próxima, Ingeniero.");
            }));

            // Bucle infinito para evitar que el Main muera (aunque los schedulers ya mantienen vivo el proceso)
            while (true) {
                Thread.sleep(60000); // Latido cada minuto
            }

        } catch (Exception e) {
            BotLogger.error("🔥 ERROR FATAL EN EL ORQUESTADOR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}