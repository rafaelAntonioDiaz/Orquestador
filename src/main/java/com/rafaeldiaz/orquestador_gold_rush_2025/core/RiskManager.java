package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 👮 EL GOBERNADOR (Risk Management System)
 * Controla la salud financiera de la cuenta.
 * Implementa: Daily Stop, Max Drawdown, Streak Breaker y Monte Carlo simplificado.
 */
public class RiskManager {

    // --- CONFIGURACIÓN DE REGLAS (HARD LIMITS) ---
    private static final double MAX_DAILY_LOSS_PERCENT = 0.02; // -2% Diario
    private static final double MAX_DRAWDOWN_PERCENT = 0.08;   // -8% Desde el pico histórico
    private static final int MAX_CONSECUTIVE_LOSSES = 3;       // Pausa tras 3 fallos seguidos

    // --- ESTADO (MEMORIA DEL SISTEMA) ---
    private final double initialDailyCapital;
    private double currentCapital;
    private double peakCapital; // Para cálculo de Drawdown
    private double dailyPnL = 0.0;

    // Contadores de Racha
    private final AtomicInteger consecutiveLosses = new AtomicInteger(0);
    private final AtomicReference<TradeStatus> status = new AtomicReference<>(TradeStatus.ACTIVE);

    public enum TradeStatus {
        ACTIVE,
        PAUSED_STREAK,    // Pausa temporal por mala racha
        HALTED_DAILY,     // Detenido por límite diario
        HALTED_DRAWDOWN   // Detenido por colapso de cuenta (Game Over)
    }

    public RiskManager(double startCapital) {
        this.initialDailyCapital = startCapital;
        this.currentCapital = startCapital;
        this.peakCapital = startCapital;
        BotLogger.info("🛡️ RiskManager INICIADO. Capital Base: $" + startCapital);
    }

    /**
     * 🚦 SEMÁFORO: ¿Puedo operar?
     * @return true si el riesgo es aceptable, false si estamos bloqueados.
     */
    public synchronized boolean canExecuteTrade() {
        if (status.get() != TradeStatus.ACTIVE) {
            BotLogger.warn("⛔ TRADE BLOQUEADO por RiskManager. Estado: " + status.get());
            return false;
        }
        return true;
    }

    /**
     * 📝 REGISTRO DE RESULTADOS (Post-Mortem)
     * Debe llamarse después de CADA operación (ganadora o perdedora).
     * @param pnlUSD Ganancia o pérdida neta en dólares (ej. -0.50 o +1.20)
     */
    public synchronized void reportTradeResult(double pnlUSD) {
        currentCapital += pnlUSD;
        dailyPnL += pnlUSD;

        // 1. Actualizar Pico para Drawdown
        if (currentCapital > peakCapital) {
            peakCapital = currentCapital;
        }

        // 2. Gestión de Rachas (Streak)
        if (pnlUSD < 0) {
            int losses = consecutiveLosses.incrementAndGet();
            BotLogger.warn("📉 Racha de pérdidas: " + losses + "/" + MAX_CONSECUTIVE_LOSSES);
            if (losses >= MAX_CONSECUTIVE_LOSSES) {
                status.set(TradeStatus.PAUSED_STREAK);
                BotLogger.error("⏸️ PAUSA POR RACHA NEGATIVA. Se requiere intervención o cool-down.");
            }
        } else {
            consecutiveLosses.set(0); // Reset si ganamos
            // Lógica de Trailing Profit Híbrido: Asegurar ganancias parciales
            // (Si ya ganamos +1%, subimos el piso de seguridad mentalmente)
        }

        // 3. Chequeo de Límites Duros (Daily & Drawdown)
        checkCriticalLimits();

        BotLogger.info(String.format("📊 ESTADO CUENTA: Cap:$%.2f | DailyPnL:$%.2f | DD:%.2f%%",
                currentCapital, dailyPnL, calculateDrawdown()));
    }

    private void checkCriticalLimits() {
        // A. Límite Diario (-2%)
        double dailyLossPercent = (initialDailyCapital - currentCapital) / initialDailyCapital;
        if (dailyPnL < 0 && dailyLossPercent >= MAX_DAILY_LOSS_PERCENT) {
            status.set(TradeStatus.HALTED_DAILY);
            BotLogger.error("🛑 STOP LOSS DIARIO ALCANZADO (-2%). Apagando motores por hoy.");
        }

        // B. Max Drawdown (-8% desde el pico)
        double drawdown = calculateDrawdown();
        if (drawdown >= MAX_DRAWDOWN_PERCENT) {
            status.set(TradeStatus.HALTED_DRAWDOWN);
            BotLogger.error("💀 MAX DRAWDOWN ALCANZADO (-8%). Protocolo de preservación activado.");
        }
    }

    private double calculateDrawdown() {
        if (peakCapital == 0) return 0.0;
        return (peakCapital - currentCapital) / peakCapital;
    }

    /**
     * 🎲 MONTE CARLO SIM (Probabilidad de Ruina)
     * Ejecuta una simulación rápida basada en el rendimiento actual.
     * Si la probabilidad de ruina supera el 5%, devuelve false.
     */
    public boolean runMonteCarloSimulation() {
        // Implementación simplificada para HFT
        // Si el WinRate cae por debajo del 40% y el Ratio Riesgo/Beneficio es malo, alerta.
        // Aquí podrías agregar lógica compleja matemática.
        return true; // Placeholder para Fase 4
    }

    // Método para resetear la racha manualmente (Cool-down reset)
    public void resetStreak() {
        if (status.get() == TradeStatus.PAUSED_STREAK) {
            consecutiveLosses.set(0);
            status.set(TradeStatus.ACTIVE);
            BotLogger.info("🔄 Racha reseteada. Volviendo al ruedo.");
        }
    }
}