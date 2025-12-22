package com.rafaeldiaz.orquestador_gold_rush_2025.core.main;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.*;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner.ArbitrageDetector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner.DynamicPairSelector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

public class Main {

    public static void main(String[] args) {
        BotLogger.info("======================================================");
        BotLogger.info("   🚀 INICIANDO ORQUESTADOR GOLD RUSH 2025 🚀   ");
        BotLogger.info("   Agente: ChasquiTokio | Modo: CAZADOR AUTÓNOMO");
        BotLogger.info("======================================================");

        try {
            // 1. CONECTOR BASE
            ExchangeConnector connector = new ExchangeConnector();
            BotLogger.info("✅ [1/4] Conector Central: ONLINE");

            // 2. SISTEMA SENSORIAL (OJOS Y OÍDOS)
            // BybitStreamer se conecta automáticamente al instanciarse
            BybitStreamer streamer = new BybitStreamer();

            // 3. LÓGICA DE NEGOCIO (EL CAZADOR)
            // El detector procesa los precios que llegan del streamer
            ArbitrageDetector detector = new ArbitrageDetector(connector);
            streamer.addListener(detector); // Conexión Ojos -> Cazador

            // 4. INTELIGENCIA DE MERCADO (EL CEREBRO)
            // El selector analiza volatilidad y le dice al streamer qué mirar.
            // AHORA SÍ: 'streamer' implementa MarketListener, así que esto compila y funciona.
            FeeManager feeManager = new FeeManager(connector);
            DynamicPairSelector selector = new DynamicPairSelector(connector, streamer, feeManager);

            // 5. INICIO DE SISTEMAS AUTÓNOMOS
            BotLogger.info("🧠 Iniciando Lóbulo Frontal (Selector Dinámico)...");
            selector.start(); // Inicia el loop de 60s

            // Nota: streamer.start() no es necesario si se inicia en el constructor,
            // pero si añadiste el método start() sugerido, déjalo.

            BotLogger.info("✅ [SISTEMA INTEGRADO]: Cerebro, Ojos y Manos conectados.");

            // ------------------------------------------------------------
            // MANTENIMIENTO DEL PROCESO
            // ------------------------------------------------------------
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                BotLogger.info("\n🛑 SEÑAL DE APAGADO RECIBIDA...");
                selector.stop();
                streamer.stop();
                BotLogger.info("👋 Hasta la próxima, Ingeniero.");
            }));

            // Loop infinito para mantener vivo el main thread
            while (true) {
                Thread.sleep(60000);
            }

        } catch (Exception e) {
            BotLogger.error("🔥 ERROR FATAL EN EL MAIN: " + e.getMessage());
            e.printStackTrace();
        }
    }
}