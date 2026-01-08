package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.DashboardService;

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
    private static DashboardService dashboard;
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
    public static void setDashboard(DashboardService ds) { dashboard = ds; }
    /**
     * Toma la instantánea.
     * @param spreadRaw Spread en decimal (ej: 0.005 para 0.5%)
     */


    public static void log(String strategy, String asset, String route, double spreadRaw,
                           double pnlEstimated, String stage, String status, String detail) {

        if (spreadRaw <= 0) return;

        // 1. Log existente al CSV (Async I/O)
        String line = String.format("%s,%s,%s,%s,%.4f%%,%.4f,%s,%s,%s",
                LocalTime.now().format(timeFmt), strategy, asset, route,
                spreadRaw * 100, pnlEstimated, stage, status, detail.replace(",", ";")
        );
        logQueue.offer(line);

        // 2. NUEVO: Log en vivo al Dashboard
        if (dashboard != null) {
            dashboard.registrarTrazaDecision(asset, stage, status, detail, spreadRaw * 100);
        }
    }
}