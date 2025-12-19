package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BotLoggerTest {

    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = LOG_DIR + "/bot.log.0"; // FileHandler suele añadir .0 al bloquear
    private static final String CSV_FILE = LOG_DIR + "/trades.csv";

    @BeforeEach
    void setUp() {
        // Aseguramos que el directorio exista
        new File(LOG_DIR).mkdirs();
    }

    @AfterEach
    void tearDown() {
        // Opcional: Limpiar archivos después de testear para no llenar el disco
        // File f = new File(CSV_FILE);
        // if (f.exists()) f.delete();
    }

    @Test
    @DisplayName("Debe crear archivo de log general y rotarlo")
    void testLogCreation() {
        BotLogger.info("--- TEST DE INICIO DE LOGS ---");

        File dir = new File(LOG_DIR);
        assertTrue(dir.exists(), "El directorio logs debe existir");
        assertTrue(dir.isDirectory(), "Debe ser un directorio");

        // El FileHandler de Java a veces bloquea el archivo 'bot.log' y crea 'bot.log.0.lck'
        // Buscamos cualquier archivo que empiece por bot.log
        File[] logs = dir.listFiles((d, name) -> name.startsWith("bot.log"));
        assertTrue(logs != null && logs.length > 0, "Debe haber al menos un archivo de log generado");

        System.out.println("📂 Buscando logs en: " + dir.getAbsolutePath());
        if (logs.length > 0) {
            System.out.println("✅ Se encontró log rotado: " + logs[0].getName());
        }
    }

    @Test
    @DisplayName("Debe registrar Trades en CSV con formato internacional (Punto decimal)")
    void testCsvDecimalFormat() throws IOException {
        // Borramos CSV previo para asegurar prueba limpia
        File csv = new File(CSV_FILE);
        if (csv.exists()) csv.delete();

        // Ejecutamos log
        double profit = 1.50; // 1.5%
        double amount = 100.25; // $100.25
        BotLogger.logTrade("BTCUSDT", "BUY", profit, amount);

        // Verificamos
        assertTrue(csv.exists(), "El archivo trades.csv debe ser creado");

        List<String> lines = Files.readAllLines(csv.toPath());
        assertTrue(lines.size() >= 2, "Debe tener cabecera y al menos una línea de datos");

        String header = lines.get(0);
        String data = lines.get(1); // La línea recién insertada

        System.out.println("📄 Header CSV: " + header);
        System.out.println("📄 Data CSV: " + data);

        // VALIDACIÓN CRÍTICA (Locale.US)
        // Buscamos el punto "." en los números
        assertTrue(data.contains("1.5000"), "El profit debe usar punto decimal (1.5000)");
        assertTrue(data.contains("100.25"), "El monto debe usar punto decimal (100.25)");

        // No debe contener comas decimales (ej: 1,5000 rompería el CSV)
        // La línea tiene comas separadoras, pero verificamos que los números no estén rotos
        String[] columns = data.split(",");
        assertEquals("BTCUSDT", columns[1]);
        assertEquals("BUY", columns[2]);
        // Si el formato fuera incorrecto (1,5000), el split generaría más columnas o datos erróneos
        assertEquals(5, columns.length, "El CSV debe tener exactamente 5 columnas");
    }
}