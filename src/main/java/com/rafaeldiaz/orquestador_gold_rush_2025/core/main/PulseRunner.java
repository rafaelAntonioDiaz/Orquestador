package com.rafaeldiaz.orquestador_gold_rush_2025.core.main;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.MarketPulseEstimator;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

public class PulseRunner {
    public static void main(String[] args) {
        System.out.println("🚀 [IGNICIÓN]: Iniciando sistema de telemetría...");

        // 1. Inicializamos los componentes de la nave
        ExchangeConnector connector = new ExchangeConnector();
        MarketPulseEstimator simulator = new MarketPulseEstimator(connector);

        // 2. Reporte de saludo inicial al celular
        BotLogger.sendTelegram("🏎️ Ferrari en pista: Iniciando Perforación de 15 min.\n💰 Capital: $224.0 USDT");

        // 3. Arrancamos el cronómetro de 15 minutos
        simulator.start15MinuteDrill();

        // 4. Mantenemos el proceso vivo para que no se cierre antes de tiempo
        try {
            System.out.println("🛰️ Perforando el mercado... No cierres esta ventana.");
            Thread.sleep(16 * 60 * 1000); // 16 min de vida para asegurar el reporte final
        } catch (InterruptedException e) {
            System.err.println("❌ Proceso interrumpido.");
        }
    }
}