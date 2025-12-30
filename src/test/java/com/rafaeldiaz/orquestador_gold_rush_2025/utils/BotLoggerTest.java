package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 📝 BOT LOGGER TEST (La Caja Negra)
 * Valida la persistencia en CSV, la robustez asíncrona y la integridad de los reportes.
 */
class BotLoggerTest {

    private static final String TEST_LOG_DIR = "logs";
    private static final String TEST_CSV_FILE = "logs/trades.csv";
    private static final String TEST_OPP_FILE = "logs/opportunities.csv";

    @BeforeEach
    void setUp() throws IOException {
        // Limpieza preventiva: Borrar logs anteriores para empezar limpio
        deleteFile(TEST_CSV_FILE);
        deleteFile(TEST_OPP_FILE);
        new File(TEST_LOG_DIR).mkdirs(); // Asegurar que el directorio existe
    }

    @AfterEach
    void tearDown() {
        // Limpieza post-test (opcional, útil para debugging si se comenta)
        // deleteFile(TEST_CSV_FILE);
        // deleteFile(TEST_OPP_FILE);
    }

    @Test
    @DisplayName("📄 CSV TRADES: Debe registrar una operación exitosa")
    void shouldLogTradeToCSV() throws IOException, InterruptedException {
        // 1. Ejecutamos el log
        BotLogger.logTrade("BTCUSDT", "WIN", 1.5, 1000.0);

        // 2. Esperamos un poco porque es ASÍNCRONO (Cola -> Hilo Worker)
        Thread.sleep(200);

        // 3. Verificamos el archivo
        File csv = new File(TEST_CSV_FILE);
        assertThat(csv).exists();

        List<String> lines = Files.readAllLines(csv.toPath());

        // Debe tener al menos 2 líneas: Cabecera + Data
        assertThat(lines.size()).isGreaterThanOrEqualTo(2);

        // Verificamos la última línea
        String lastLine = lines.get(lines.size() - 1);
        System.out.println("📝 CSV Line: " + lastLine);

        assertThat(lastLine).contains("BTCUSDT");
        assertThat(lastLine).contains("WIN");
        assertThat(lastLine).contains("1.5000"); // Formato %.4f
        assertThat(lastLine).contains("1000.00"); // Formato %.2f
    }

    @Test
    @DisplayName("🔎 CSV OPPORTUNITIES: Debe registrar hallazgos del radar")
    void shouldLogOpportunityToCSV() throws IOException, InterruptedException {
        // 1. Logueamos una oportunidad detectada pero no ejecutada
        BotLogger.logOpportunity("TRIANGULAR", "SOL", "BNB", 0.5, 0.45, "SKIPPED", "LOW_PROFIT");

        // 2. Espera asíncrona
        Thread.sleep(200);

        // 3. Validación
        File csv = new File(TEST_OPP_FILE);
        assertThat(csv).exists();

        List<String> lines = Files.readAllLines(csv.toPath());
        String lastLine = lines.get(lines.size() - 1);
        System.out.println("🔎 Opp Line: " + lastLine);

        assertThat(lastLine).contains("TRIANGULAR");
        assertThat(lastLine).contains("SOL");
        assertThat(lastLine).contains("SKIPPED");
    }

    @Test
    @DisplayName("🚨 TELEGRAM SAFEGUARD: No debe explotar sin credenciales")
    void shouldNotCrash_WhenTelegramTokenMissing() {
        // Como no cargamos el .env real en el entorno de test (o puede ser dummy),
        // verificamos que el método sea robusto y capture excepciones internamente.
        assertDoesNotThrow(() -> {
            BotLogger.error("TEST ERROR MESSAGE - PLEASE IGNORE");
            // Damos tiempo al hilo virtual para arrancar y (posiblemente) fallar silenciosamente
            Thread.sleep(100);
        });

        System.out.println("✅ TELEGRAM: Fail-safe confirmado (no rompió el hilo principal).");
    }

    @Test
    @DisplayName("💾 SYSTEM EVENT: Debe registrar hitos del sistema")
    void shouldLogSystemEvent() throws IOException, InterruptedException {
        BotLogger.logSystemEvent("TEST_START", "Unit Testing BotLogger");

        Thread.sleep(200);

        File csv = new File(TEST_OPP_FILE);
        List<String> lines = Files.readAllLines(csv.toPath());
        String lastLine = lines.get(lines.size() - 1);

        assertThat(lastLine).contains("SYSTEM");
        assertThat(lastLine).contains("TEST_START");

        System.out.println("✅ SYSTEM EVENT: Registrado correctamente.");
    }

    // --- Helper ---
    private void deleteFile(String path) {
        File f = new File(path);
        if (f.exists()) {
            f.delete();
        }
    }
}