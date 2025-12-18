package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotLoggerTest {

    @Test
    void testLogCreation() {
        // Simulamos un evento
        BotLogger.info("Test de inicio de sistema");
        BotLogger.logTrade("BTC/USDT", "Triangular", 0.55, 100.0);

        // Verificamos que se crearon los archivos
        File logFile = new File("logs/bot.log");
        File csvFile = new File("logs/trades.csv");

        assertTrue(logFile.exists(), "El archivo bot.log debería existir");
        assertTrue(csvFile.exists(), "El archivo trades.csv debería existir");

        System.out.println("✅ Sistema de Logs verificado. Revisa la carpeta /logs en tu proyecto.");
    }
}