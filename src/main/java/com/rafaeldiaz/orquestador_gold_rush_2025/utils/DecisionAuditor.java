package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 📸 AUDITOR FORENSE (SISTEMA DE TRAZABILIDAD)
 * Registra el ciclo de vida completo de cada oportunidad detectada.
 * Cero latencia en el hilo principal (Async I/O).
 */
public class DecisionAuditor {

    private static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private static final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String FILE_NAME = "decision_trace.csv";

    static {
        // Hilo Virtual dedicado a I/O (Fire-and-Forget)
        Thread.ofVirtual().name("Auditor-Writer").start(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, true))) {
                // Si el archivo es nuevo, escribimos la cabecera
                if (new File(FILE_NAME).length() == 0) {
                    writer.write("HORA,ESTRATEGIA,ACTIVO,RUTA,SPREAD_RAW,PNL_EST,ETAPA,ESTADO,DETALLE\n");
                }

                while (true) {
                    String line = logQueue.take(); // Espera eficiente
                    writer.write(line);
                    writer.newLine();
                    writer.flush(); // Persistencia inmediata
                }
            } catch (Exception e) {
                System.err.println("🔥 Error crítico en Auditor: " + e.getMessage());
            }
        });
    }

    /**
     * Toma la instantánea.
     * @param spreadRaw Spread en decimal (ej: 0.005 para 0.5%)
     */
    public static void log(String strategy, String asset, String route, double spreadRaw, double pnlEstimated,
                           String stage, String status, String detail) {

        // FILTRO DE RUIDO: Solo registramos si hay un spread matemático positivo.
        // Ignoramos el ruido de mercado plano (<=0) para no llenar el disco.
        if (spreadRaw <= 0) return;

        String line = String.format("%s,%s,%s,%s,%.4f%%,%.4f,%s,%s,%s",
                LocalTime.now().format(timeFmt),
                strategy,
                asset,
                route,
                spreadRaw * 100,      // % Legible
                pnlEstimated,         // $ Estimado
                stage,                // ESTRATEGIA, FINANCIERO, EJECUCION, BATALLA
                status,               // CANDIDATO, RECHAZADO, EXITO, PATA_ROTA
                detail.replace(",", ";") // Sanitizar comas
        );

        logQueue.offer(line);
    }
}