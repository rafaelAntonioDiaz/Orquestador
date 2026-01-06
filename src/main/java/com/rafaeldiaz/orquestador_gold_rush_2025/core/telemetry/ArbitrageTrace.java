package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 🕵️ EXPEDIENTE DE AUDITORÍA
 * Captura la historia completa de una operación (o intento fallido)
 * para diagnóstico forense en el Dashboard.
 */
public class ArbitrageTrace {

    // --- ESTADOS DEL CICLO DE VIDA (PIPELINE) ---
    public enum AuditStage {
        // 1. FILTROS DE ENTRADA (Murió antes de nacer)
        SCAN_IGNORED,       // Spread bruto < MIN_SCAN_SPREAD
        ADVISOR_REJECTED,   // Spread neto < ADVISOR_MIN_SPREAD
        ORACLE_VETO,        // Oráculo detectó riesgo (Z-Score alto o baja confianza)
        SLIPPAGE_EXCEEDED,  // Impacto en libro > MAX_SLIPPAGE
        LATENCY_TIMEOUT,    // Ping > MAX_LATENCY_MS
        RISK_PAUSED,        // Circuit Breaker o Stop Loss diario activado

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

    public final String timestamp;
    public final String assetPair;      // Ej: "BTC/USDT"
    public final AuditStage stage;      // El estado actual

    // EVIDENCIA (Valores en el momento del crimen)
    public final double spreadFound;    // vs ADVISOR_MIN_SPREAD
    public final double slippageCalc;   // vs MAX_SLIPPAGE
    public final long latencyDetected;  // vs MAX_LATENCY_MS
    public final double oracleScore;    // vs ORACLE_MIN_CONFIDENCE

    // RESULTADOS (Para operaciones reales)
    public final double realProfit;
    public final String exchangeA;
    public final String exchangeB;
    public final String extraMessage;   // Mensaje libre para detalles técnicos

    // Constructor para RECHAZOS (Filtros)
    public ArbitrageTrace(String asset, AuditStage stage, String msg, double evidenceValue) {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        this.assetPair = asset;
        this.stage = stage;
        this.extraMessage = msg;

        // Mapeamos el valor de evidencia según el tipo de rechazo
        this.spreadFound = (stage == AuditStage.ADVISOR_REJECTED || stage == AuditStage.SCAN_IGNORED) ? evidenceValue : 0;
        this.latencyDetected = (stage == AuditStage.LATENCY_TIMEOUT) ? (long)evidenceValue : 0;
        this.oracleScore = (stage == AuditStage.ORACLE_VETO) ? evidenceValue : 0;
        this.slippageCalc = (stage == AuditStage.SLIPPAGE_EXCEEDED) ? evidenceValue : 0;

        this.exchangeA = "-";
        this.exchangeB = "-";
        this.realProfit = 0;
    }

    // Constructor para EJECUCIONES (Éxito, Falla o Huérfana)
    public ArbitrageTrace(String asset, AuditStage stage, String exA, String exB, double profit, String msg) {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        this.assetPair = asset;
        this.stage = stage;
        this.exchangeA = exA;
        this.exchangeB = exB;
        this.realProfit = profit;
        this.extraMessage = msg;

        this.spreadFound = 0;
        this.slippageCalc = 0;
        this.latencyDetected = 0;
        this.oracleScore = 0;
    }
}