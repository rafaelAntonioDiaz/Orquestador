package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 📉 RISK MANAGEMENT SYSTEM (Módulo de Control de Riesgo y Persistencia)
 *
 * RESPONSABILIDAD:
 * 1. Monitorear la Curva de Equidad (Equity Curve) en tiempo real.
 * 2. Ejecutar Disyuntores (Circuit Breakers) ante violaciones de parámetros de riesgo.
 * 3. Persistir el estado financiero (PnL Diario, Drawdown) para continuidad operativa.
 *
 * MODELO MATEMÁTICO:
 * - Daily Stop: PnL_Diario < -(Capital_Inicial * 0.02)
 * - Max Drawdown: (Peak_Capital - Current_Capital) / Peak_Capital > 0.08
 */
public class RiskManager {

    // --- PARÁMETROS DE RIESGO (HARD LIMITS) ---
    private static final double MAX_DAILY_LOSS_PERCENT = 0.02; // Límite de pérdida diaria (2%)
    private static final double MAX_DRAWDOWN_PERCENT = 0.08;   // Drawdown Máximo permitido (8%)
    private static final int MAX_CONSECUTIVE_FAILURES = 3;     // Límite de fallos de ejecución consecutivos
    private static final String STATE_FILE = "financial_state.json"; // Archivo de persistencia

    // --- VARIABLES DE ESTADO (MEMORY HEAP) ---
    private double initialDailyCapital; // Capital al inicio de la sesión (00:00)
    private double currentCapital;      // Equity actual (Mark-to-Market)
    private double peakCapital;         // High-Water Mark (Máximo histórico)
    private double dailyPnL = 0.0;      // Profit and Loss acumulado del día

    private final ObjectMapper mapper = new ObjectMapper();

    // Contadores de Desviación
    private final AtomicInteger executionFailures = new AtomicInteger(0);
    private final AtomicReference<SystemStatus> status = new AtomicReference<>(SystemStatus.OPERATIONAL);

    // Estados del Autómata Finito
    public enum SystemStatus {
        OPERATIONAL,        // Sistema nominal
        PAUSED_DEVIATION,   // Pausa técnica por anomalías consecutivas
        HALTED_DAILY_LIMIT, // Detenido: Límite de riesgo diario alcanzado
        HALTED_DRAWDOWN     // Detenido: Violación de Max Drawdown (Requiere auditoría)
    }

    public RiskManager(double startCapital) {
        // Inicialización por defecto
        this.currentCapital = startCapital;
        this.initialDailyCapital = startCapital;
        this.peakCapital = startCapital;

        BotLogger.info("🛡️ RiskManager: Iniciando secuencia de carga de estado...");
        loadFinancialState(); // Carga de persistencia

        BotLogger.info(String.format("📊 ESTADO FINANCIERO INICIAL: Equity: $%.2f | PnL Diario: $%.2f | High-Water Mark: $%.2f",
                currentCapital, dailyPnL, peakCapital));

        validateRiskParameters(); // Validación inicial pre-arranque
    }

    /**
     * Valida si el sistema tiene autorización para operar según los parámetros de riesgo.
     */
    public synchronized boolean canExecuteTrade() {
        if (status.get() != SystemStatus.OPERATIONAL) {
            BotLogger.warn("⛔ OPERACIÓN DENEGADA. Estatus del Sistema: " + status.get());
            return false;
        }
        return true;
    }

    /**
     * Registra el resultado matemático de una operación y actualiza la curva de equidad.
     * @param pnlUSD Resultado neto de la operación (Net Profit/Loss)
     */
// RiskManager.java optimizado
    public synchronized void reportTradeResult(double pnlUSD) {
        // 1. Actualización en RAM (Nanosegundos)
        currentCapital += pnlUSD;
        dailyPnL += pnlUSD;
        if (currentCapital > peakCapital) peakCapital = currentCapital;

        // Lógica de circuito (rápida)
        if (pnlUSD < 0) { /* lógica de fallos */ }
        validateRiskParameters();

        // 2. Persistencia ASÍNCRONA (Fire & Forget)
        // No bloqueamos el hilo de trading esperando al disco duro
        CompletableFuture.runAsync(this::saveFinancialState);
    }
    /**
     * Evalúa las condiciones de parada (Circuit Breakers).
     */
    private void validateRiskParameters() {
        // A. Disyuntor Diario (Daily Stop Loss)
        // Cálculo estricto sobre el capital inicial del día
        double dailyLossRatio = -dailyPnL / initialDailyCapital;

        if (dailyPnL < 0 && dailyLossRatio >= MAX_DAILY_LOSS_PERCENT) {
            status.set(SystemStatus.HALTED_DAILY_LIMIT);
            BotLogger.error(String.format("🛑 DISYUNTOR DIARIO ACTIVADO. Pérdida: %.2f%% (Límite: %.2f%%). Ejecución detenida.",
                    dailyLossRatio * 100, MAX_DAILY_LOSS_PERCENT * 100));
        }

        // B. Disyuntor de Drawdown (Protección de Capital Base)
        double currentDrawdown = (peakCapital - currentCapital) / peakCapital;
        if (currentDrawdown >= MAX_DRAWDOWN_PERCENT) {
            status.set(SystemStatus.HALTED_DRAWDOWN);
            BotLogger.error(String.format("💀 CRITICAL DRAWDOWN DETECTADO (%.2f%%). Sistema bloqueado por seguridad.",
                    currentDrawdown * 100));
        }
    }

    // =========================================================
    // 💾 CAPA DE PERSISTENCIA (I/O)
    // =========================================================

    private void saveFinancialState() {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("date", LocalDate.now().toString()); // Fecha contable
            node.put("currentCapital", currentCapital);
            node.put("initialDailyCapital", initialDailyCapital);
            node.put("peakCapital", peakCapital);
            node.put("dailyPnL", dailyPnL);
            node.put("status", status.get().name());

            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(STATE_FILE), node);
        } catch (IOException e) {
            BotLogger.error("⚠️ Error Crítico I/O: No se pudo persistir el estado financiero: " + e.getMessage());
        }
    }

    private void loadFinancialState() {
        File file = new File(STATE_FILE);
        if (!file.exists()) return; // Inicialización limpia (Primer despliegue)

        try {
            JsonNode node = mapper.readTree(file);
            String savedDate = node.path("date").asText();
            String today = LocalDate.now().toString();

            // Recuperación de métricas globales
            this.currentCapital = node.path("currentCapital").asDouble(currentCapital);
            this.peakCapital = node.path("peakCapital").asDouble(peakCapital);

            if (savedDate.equals(today)) {
                // CONTINUIDAD DE SESIÓN (Mismo día contable)
                BotLogger.info("🔄 Sesión recuperada. Manteniendo contabilidad intradiaria.");
                this.initialDailyCapital = node.path("initialDailyCapital").asDouble(initialDailyCapital);
                this.dailyPnL = node.path("dailyPnL").asDouble(0.0);

                String savedStatus = node.path("status").asText("OPERATIONAL");
                this.status.set(SystemStatus.valueOf(savedStatus));

            } else {
                // NUEVA SESIÓN CONTABLE (Rollover diario)
                BotLogger.info("☀️ Inicio de Nueva Sesión Contable. Reseteando métricas intradiarias.");

                // El cierre de ayer es la apertura de hoy
                this.initialDailyCapital = this.currentCapital;
                this.dailyPnL = 0.0;
                this.status.set(SystemStatus.OPERATIONAL); // Restablecimiento operativo

                saveFinancialState(); // Inicializar archivo para el nuevo día
            }

        } catch (IOException e) {
            BotLogger.error("⚠️ Corrupción de datos o error de lectura. Iniciando con parámetros por defecto: " + e.getMessage());
        }
    }

    /**
     * Intervención humana para restablecer el sistema tras una pausa técnica.
     */
    public void overrideLockdown() {
        status.set(SystemStatus.OPERATIONAL);
        executionFailures.set(0);
        BotLogger.warn("🔓 INTERVENCIÓN MANUAL: Protocolos de bloqueo restablecidos por operador.");
        saveFinancialState();
    }
}