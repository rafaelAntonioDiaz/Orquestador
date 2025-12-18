package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotLoggerTest {

    @Test
    void testLogCreation() throws InterruptedException {
        // Act: Generamos actividad de log
        BotLogger.info("--- TEST DE INICIO DE LOGS ---");
        BotLogger.logTrade("BTC/USDT", "Test", 0.0, 100.0);

        // Esperamos un parpadeo (100ms) para dar tiempo al sistema de archivos (File I/O)
        Thread.sleep(100);

        // Assert: Verificamos existencia
        File logDir = new File("logs");
        File logFile = new File("logs/bot.log");
        File csvFile = new File("logs/trades.csv");

        // Debug: Imprimimos dónde está buscando Java (Ruta Absoluta)
        System.out.println("📂 Buscando logs en: " + logDir.getAbsolutePath());

        // Verificaciones
        assertTrue(logDir.exists(), "La carpeta /logs debería existir");

        // Verificamos si existe el archivo O si hay algún archivo de log rotado (bot.log.0, etc)
        boolean logExists = logFile.exists();
        if (!logExists && logDir.listFiles() != null) {
            for (File f : logDir.listFiles()) {
                if (f.getName().startsWith("bot.log")) {
                    logExists = true;
                    System.out.println("✅ Se encontró log rotado: " + f.getName());
                    break;
                }
            }
        }

        assertTrue(logExists, "El archivo bot.log (o rotado) debería existir");
        assertTrue(csvFile.exists(), "El archivo trades.csv debería existir");
    }
}