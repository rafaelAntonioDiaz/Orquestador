package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 🕵️ EXPEDIENTE DE AUDITORÍA (FORENSE V2.0)
 * Captura la historia completa de una operación:
 * Desde la promesa matemática hasta la realidad de la ejecución.
 */
public class ArbitrageTrace {

    // --- ESTADOS DEL CICLO DE VIDA (PIPELINE) ---
    public enum AuditStage {
        // 1. FILTROS DE ENTRADA (Murió antes de nacer)
        SCAN_IGNORED,       // Spread bruto < MIN_SCAN_SPREAD
        ADVISOR_REJECTED,   // Spread neto < ADVISOR_MIN_SPREAD
        ORACLE_VETO,        // Oráculo detectó riesgo (Z-Score alto o baja confianza)
        SLIPPAGE_EXCEEDED,  // Impacto estimado en libro > MAX_SLIPPAGE
        LATENCY_TIMEOUT,    // Ping > MAX_LATENCY_MS
        RISK_PAUSED,        // Circuit Breaker o Stop Loss diario activado
        INSUFFICIENT_FUNDS, // 🛑 NUEVO: Rechazo por CFO (Saldo Virtual/Real)

        // 2. EJECUCIÓN (El disparo)
        ORDER_FAILED,       // API Error / Rechazo del Exchange
        ENTRY_FILLED,       // Compra inicial exitosa

        // 3. RESULTADOS CRÍTICOS (Arbitraje Espacial)
        ORPHAN_DETECTED,    // 💀 PELIGRO: Entry llena, Exit fallida (Cojo)
        EXIT_FILLED,        // 💰 ÉXITO: Ciclo cerrado completo
        FORCED_CLOSE,       // 🩹 CIERRE MANUAL/FORZADO: Se vendió para salvar capital

        // 4. SISTEMA
        SYSTEM_MSG          // Logs generales del bot (Arranque, Config, etc)
    }

    // IDENTIDAD
    public final String traceId;        // UUID único para rastrear el evento
    public final String timestamp;
    public final String assetPair;      // Ej: "BTC/USDT"
    public final AuditStage stage;      // El estado actual

    // EVIDENCIA DE RECHAZO (Por qué no disparamos)
    public final double spreadFound;    // vs ADVISOR_MIN_SPREAD
    public final double slippageCalc;   // Estimación pre-trade
    public final long latencyDetected;  // Ping de red (CheckNetwork)
    public final double oracleScore;    // Confianza del Oráculo

    // EVIDENCIA FORENSE DE EJECUCIÓN (Realidad vs Expectativa)
    public final double expectedProfit; // 🔮 Lo que prometió el Scanner
    public final double realProfit;     // 💵 Lo que realmente ganamos
    public final double realSlippage;   // 📉 Desviación de precio ((Real - Ideal) / Ideal)
    public final long executionDuration; // ⏱️ Tiempo total (Start -> Finish) en ms

    // DATOS DE CONTEXTO
    public final String exchangeA;
    public final String exchangeB;
    public final String extraMessage;   // Mensaje libre para detalles técnicos

    /**
     * CONSTRUCTOR 1: PARA RECHAZOS Y FILTROS
     * (Cuando la oportunidad muere en el análisis)
     */
    public ArbitrageTrace(String asset, AuditStage stage, String msg, double evidenceValue) {
        this.traceId = UUID.randomUUID().toString().substring(0, 8);
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        this.assetPair = asset;
        this.stage = stage;
        this.extraMessage = msg;

        // Mapeamos el valor de evidencia según el tipo de rechazo
        this.spreadFound = (stage == AuditStage.ADVISOR_REJECTED || stage == AuditStage.SCAN_IGNORED) ? evidenceValue : 0;
        this.latencyDetected = (stage == AuditStage.LATENCY_TIMEOUT) ? (long)evidenceValue : 0;
        this.oracleScore = (stage == AuditStage.ORACLE_VETO) ? evidenceValue : 0;
        this.slippageCalc = (stage == AuditStage.SLIPPAGE_EXCEEDED) ? evidenceValue : 0;

        // Valores nulos para ejecución
        this.exchangeA = "-";
        this.exchangeB = "-";
        this.realProfit = 0;
        this.expectedProfit = 0;
        this.realSlippage = 0;
        this.executionDuration = 0;
    }

    /**
     * CONSTRUCTOR 2: PARA EJECUCIONES FORENSES (EL NUEVO ESTÁNDAR)
     * (Cuando hubo dinero real en juego, ganemos o perdamos)
     */
    public ArbitrageTrace(String asset, AuditStage stage, String exA, String exB,
                          double expectedProfit, double realProfit,
                          long durationMs, double realSlippagePct, String msg) {

        this.traceId = UUID.randomUUID().toString().substring(0, 8);
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        this.assetPair = asset;
        this.stage = stage;
        this.exchangeA = exA;
        this.exchangeB = exB;
        this.extraMessage = msg;

        // Métricas Forenses
        this.expectedProfit = expectedProfit;
        this.realProfit = realProfit;
        this.executionDuration = durationMs;
        this.realSlippage = realSlippagePct;

        // Valores de filtro vacíos (ya pasamos los filtros)
        this.spreadFound = 0;
        this.slippageCalc = 0;
        this.latencyDetected = 0;
        this.oracleScore = 0; // Podríamos pasarlo si queremos ver qué score tenía al entrar
    }
}