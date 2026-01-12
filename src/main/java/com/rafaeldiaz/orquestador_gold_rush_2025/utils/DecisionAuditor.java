package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.ArbitrageTrace;
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
     * REGISTRO DE FILTROS (Cuando la oportunidad es rechazada antes de operar)
     * @param asset Par (ej: BTC/USDT)
     * @param stage Razón del rechazo (ej: SPREAD_TOO_LOW)
     * @param message Detalle técnico
     * @param evidence Valor que causó el rechazo (ej: el spread real encontrado)
     */
    public static void logFilter(String asset, ArbitrageTrace.AuditStage stage, String message, double evidence) {
        // Constructor 1 de ArbitrageTrace (Ligero)
        ArbitrageTrace trace = new ArbitrageTrace(asset, stage, message, evidence);
        logQueue.offer(trace.toString());
    }

    /**
     * REGISTRO FORENSE (Cuando hubo ejecución, exitosa o fallida)
     * @param asset Par operado
     * @param stage Estado final (EXIT_FILLED, ORDER_FAILED)
     * @param exA Exchange Compra
     * @param exB Exchange Venta
     * @param expProfit Profit Estimado
     * @param realProfit Profit Real
     * @param duration Duración ms
     * @param slippage Slippage sufrido
     * @param msg Notas
     */
    public static void logExecution(String asset, ArbitrageTrace.AuditStage stage, String exA, String exB,
                                    double expProfit, double realProfit, long duration,
                                    double slippage, String msg) {
        // Constructor 2 de ArbitrageTrace (Completo)
        ArbitrageTrace trace = new ArbitrageTrace(asset, stage, exA, exB, expProfit, realProfit, duration, slippage, msg);
        logQueue.offer(trace.toString());
    }

    /**
     * Método de compatibilidad (si tienes código viejo llamando a 'log')
     * Redirige a logFilter.
     */
    public static void log(String ignoredStrategy, String asset, String ignoredRoute, double spreadRaw,
                           double ignoredPnl, String stageStr, String status, String detail) {
        try {
            ArbitrageTrace.AuditStage stage = ArbitrageTrace.AuditStage.valueOf(stageStr); // Intenta mapear el string al enum
            logFilter(asset, stage, detail, spreadRaw);
        } catch (IllegalArgumentException e) {
            // Si el string no coincide con el enum, lo mandamos como SYSTEM_MSG
            logFilter(asset, ArbitrageTrace.AuditStage.SYSTEM_MSG, status + ": " + detail, spreadRaw);
        }
    }
}
