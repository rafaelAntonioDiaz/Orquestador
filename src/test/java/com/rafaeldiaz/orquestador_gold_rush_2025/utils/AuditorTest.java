package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 🧪 PRUEBA DE ESTRÉS PARA EL AUDITOR
 * Simula una tormenta de oportunidades para verificar:
 * 1. Thread Safety (Seguridad de hilos).
 * 2. Persistencia en disco.
 * 3. Formato CSV correcto.
 */
public class AuditorTest {

    private static final String FILE_NAME = "decision_trace.csv";

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("🧪 INICIANDO PRUEBA DEL AUDITOR...");

        // 1. PREPARACIÓN: Borrar archivo viejo
        File file = new File(FILE_NAME);
        if (file.exists()) {
            if (file.delete()) {
                System.out.println("✅ Archivo anterior borrado.");
            } else {
                System.err.println("❌ No se pudo borrar el archivo anterior. ¿Está abierto?");
                return;
            }
        }

        // 2. PRUEBA FUNCIONAL BÁSICA
        System.out.println("📝 Escribiendo logs de prueba...");
        DecisionAuditor.log("TEST_STRAT", "BTC", "Bin->Bybit", 0.05, 0.0, "RADAR", "CANDIDATO", "Prueba 1");
        DecisionAuditor.log("TEST_STRAT", "ETH", "Mexc->Kucoin", 0.10, -0.5, "FINANCIERO", "RECHAZADO", "Fees altos");

        // 3. PRUEBA DE ESTRÉS (SIMULACIÓN TOKIO)
        int totalLogs = 1000;
        System.out.println("🔥 INICIANDO STRESS TEST (" + totalLogs + " hilos concurrentes)...");

        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalLogs; i++) {
                final int index = i;
                executor.submit(() -> {
                    // Simulamos diferentes activos y razones
                    DecisionAuditor.log(
                            "STRESS_STRAT",
                            "COIN" + index,
                            "ExA->ExB",
                            0.005, // 0.5%
                            1.50,
                            "EJECUCION",
                            "DISPARANDO",
                            "Hilo Virtual #" + index
                    );
                });
            }
        } // El try-with-resources espera a que terminen los hilos de envío

        // Damos un pequeño respiro para que el hilo escritor (I/O) vacíe la cola
        System.out.println("⏳ Esperando vaciado de cola (I/O)...");
        Thread.sleep(1000);

        long end = System.currentTimeMillis();
        System.out.println("✅ Escritura finalizada en " + (end - start) + "ms");

        // 4. VERIFICACIÓN FORENSE
        verifyResults(totalLogs + 2); // +2 por los logs manuales del paso 2
    }

    private static void verifyResults(int expectedDataLines) throws IOException {
        Path path = Path.of(FILE_NAME);
        if (!Files.exists(path)) {
            System.err.println("❌ ERROR FATAL: El archivo no se creó.");
            return;
        }

        List<String> lines = Files.readAllLines(path);
        int totalLines = lines.size();
        // La primera línea es la cabecera, así que restamos 1
        int dataLines = totalLines - 1;

        System.out.println("\n📊 REPORTE DE AUTOPSIA:");
        System.out.println("   ---------------------");
        System.out.println("   Líneas Totales: " + totalLines);
        System.out.println("   Cabecera:       " + (lines.get(0).startsWith("HORA,ESTRATEGIA") ? "✅ OK" : "❌ ERROR"));
        System.out.println("   Datos Esperados:" + expectedDataLines);
        System.out.println("   Datos Leídos:   " + dataLines);

        if (dataLines == expectedDataLines) {
            System.out.println("\n🏆 RESULTADO: PRUEBA EXITOSA. El Auditor es sólido como una roca.");
            System.out.println("   -> Puedes proceder al despliegue en Tokio.");
        } else {
            System.out.println("\n💀 RESULTADO: FALLO. Se perdieron datos.");
        }
    }
}