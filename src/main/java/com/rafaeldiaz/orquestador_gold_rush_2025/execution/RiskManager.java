package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig; // <--- LA FUENTE DE LA VERDAD
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 📉 RISK MANAGEMENT SYSTEM (Versión Sincronizada con BotConfig)
 * Totalmente desacoplado de valores hardcodeados.
 */
public class RiskManager {
    private final ExecutionCoordinator coordinator;
    private final String stateFile;

    // --- VARIABLES DE ESTADO ---
    private double initialDailyCapital;
    private double currentCapital;
    private double peakCapital;
    private double dailyPnL = 0.0;

    // Contadores Dinámicos
    private int consecutiveLosses = 0;
    private long pauseUntilTimestamp = 0;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<SystemStatus> status = new AtomicReference<>(SystemStatus.OPERATIONAL);

    public enum SystemStatus {
        OPERATIONAL,
        PAUSED_DEVIATION,
        HALTED_DAILY_LIMIT,
        HALTED_DRAWDOWN
    }

    public RiskManager(double startCapital, ExecutionCoordinator coordinator) {
        this(coordinator, startCapital, "financial_state.json");
    }

    public RiskManager(ExecutionCoordinator coordinator, double startCapital, String customStateFile) {
        this.coordinator = coordinator;
        this.stateFile = customStateFile;
        this.currentCapital = startCapital;
        this.initialDailyCapital = startCapital;
        this.peakCapital = startCapital;

        BotLogger.info("🛡️ RiskManager: Iniciando carga de estado desde: " + stateFile);
        loadFinancialState();

        // Log de parámetros cargados desde BotConfig para auditoría
        BotLogger.info(String.format("🛡️ PARÁMETROS: MaxLoss=%.1f%% | MaxDD=%.1f%% | MaxStreak=%d",
                BotConfig.RISK_MAX_DAILY_LOSS * 100,
                BotConfig.RISK_MAX_DRAWDOWN * 100,
                BotConfig.RISK_MAX_CONSECUTIVE_LOSSES));

        validateRiskParameters();
    }

    public synchronized boolean canExecuteTrade() {
        // Chequeo de Pausa Temporal (Cooldown)
        if (status.get() == SystemStatus.PAUSED_DEVIATION) {
            if (System.currentTimeMillis() > pauseUntilTimestamp) {
                BotLogger.info("🟢 COOLDOWN FINALIZADO. Reactivando sistema tras pausa por racha.");
                status.set(SystemStatus.OPERATIONAL);
                consecutiveLosses = 0;
                return true;
            }
            return false;
        }

        if (status.get() != SystemStatus.OPERATIONAL) {
            BotLogger.warn("⛔ OPERACIÓN DENEGADA. Estatus: " + status.get());
            return false;
        }
        return true;
    }

    public synchronized void reportTradeResult(double pnlUSD) {
        currentCapital += pnlUSD;
        dailyPnL += pnlUSD;
        if (currentCapital > peakCapital) peakCapital = currentCapital;

        // Lógica de Racha
        if (pnlUSD < 0) {
            consecutiveLosses++;
            BotLogger.warn("📉 PÉRDIDA REGISTRADA. Racha Actual: " + consecutiveLosses);
        } else if (pnlUSD > 0) {
            if (consecutiveLosses > 0) BotLogger.info("✨ RACHA NEGATIVA ROMPIDA.");
            consecutiveLosses = 0;
        }

        validateRiskParameters();
        CompletableFuture.runAsync(this::saveFinancialState);
    }

    private void validateRiskParameters() {
        // A. Disyuntor Diario
        double dailyLossRatio = -dailyPnL / initialDailyCapital;
        // USO DE BOTCONFIG
        if (dailyPnL < 0 && dailyLossRatio >= BotConfig.RISK_MAX_DAILY_LOSS) {
            status.set(SystemStatus.HALTED_DAILY_LIMIT);
            String msg = String.format("🛑 DISYUNTOR DIARIO ACTIVADO. Pérdida: %.2f%%.", dailyLossRatio * 100);
            BotLogger.error(msg);
            coordinator.forceGlobalLockdown("DAILY_LOSS_LIMIT");
            return;
        }

        // B. Disyuntor Drawdown
        double currentDrawdown = (peakCapital - currentCapital) / peakCapital;
        // USO DE BOTCONFIG
        if (currentDrawdown >= BotConfig.RISK_MAX_DRAWDOWN) {
            status.set(SystemStatus.HALTED_DRAWDOWN);
            BotLogger.error(String.format("💀 CRITICAL DRAWDOWN (%.2f%%).", currentDrawdown * 100));
            // coordinator.forceGlobalLockdown("MAX_DRAWDOWN");
            return;
        }

        // C. Disyuntor de Racha (Streak Breaker)
        // USO DE BOTCONFIG
        if (consecutiveLosses >= BotConfig.RISK_MAX_CONSECUTIVE_LOSSES) {
            status.set(SystemStatus.PAUSED_DEVIATION);
            this.pauseUntilTimestamp = System.currentTimeMillis() + BotConfig.RISK_STREAK_PAUSE_MS;

            BotLogger.warn("⚠️ RACHA DE PÉRDIDAS (>limit). Pausando sistema por enfriamiento.");
            BotLogger.warn("   -> Reactivación programada: " + Instant.ofEpochMilli(pauseUntilTimestamp));
        }
    }

    private void saveFinancialState() {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("date", LocalDate.now().toString());
            node.put("currentCapital", currentCapital);
            node.put("initialDailyCapital", initialDailyCapital);
            node.put("peakCapital", peakCapital);
            node.put("dailyPnL", dailyPnL);
            node.put("status", status.get().name());
            node.put("consecutiveLosses", consecutiveLosses);
            node.put("pauseUntilTimestamp", pauseUntilTimestamp);

            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(this.stateFile), node);
        } catch (IOException e) {
            BotLogger.error("⚠️ Error I/O RiskManager: " + e.getMessage());
        }
    }

    private void loadFinancialState() {
        File file = new File(this.stateFile);
        if (!file.exists()) return;

        try {
            JsonNode node = mapper.readTree(file);
            String savedDate = node.path("date").asText();
            String today = LocalDate.now().toString();

            this.currentCapital = node.path("currentCapital").asDouble(currentCapital);
            this.peakCapital = node.path("peakCapital").asDouble(peakCapital);
            this.consecutiveLosses = node.path("consecutiveLosses").asInt(0);
            this.pauseUntilTimestamp = node.path("pauseUntilTimestamp").asLong(0);

            if (savedDate.equals(today)) {
                BotLogger.info("🔄 Sesión recuperada.");
                this.initialDailyCapital = node.path("initialDailyCapital").asDouble(initialDailyCapital);
                this.dailyPnL = node.path("dailyPnL").asDouble(0.0);
                this.status.set(SystemStatus.valueOf(node.path("status").asText("OPERATIONAL")));
            } else {
                BotLogger.info("☀️ Nueva Sesión Contable.");
                this.initialDailyCapital = this.currentCapital;
                this.dailyPnL = 0.0;
                this.status.set(SystemStatus.OPERATIONAL);
                this.consecutiveLosses = 0;
                this.pauseUntilTimestamp = 0;
                saveFinancialState();
            }
        } catch (IOException e) {
            BotLogger.error("⚠️ Error carga RiskManager: " + e.getMessage());
        }
    }

    public void overrideLockdown() {
        status.set(SystemStatus.OPERATIONAL);
        consecutiveLosses = 0;
        pauseUntilTimestamp = 0;
        BotLogger.warn("🔓 INTERVENCIÓN MANUAL: Sistema restablecido.");
        saveFinancialState();
    }

    public boolean runMonteCarloSimulation(double winRate, double avgWin, double avgLoss) {
        int simulations = 1000;
        int tradesPerSim = 100;
        int ruinCount = 0;
        double ruinThreshold = currentCapital * 0.80;

        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();

        for (int i = 0; i < simulations; i++) {
            double simulatedEquity = currentCapital;
            for (int t = 0; t < tradesPerSim; t++) {
                if (random.nextDouble() < winRate) simulatedEquity += avgWin;
                else simulatedEquity -= avgLoss;

                if (simulatedEquity < ruinThreshold) {
                    ruinCount++;
                    break;
                }
            }
        }
        double ruinProbability = (double) ruinCount / simulations;
        BotLogger.info(String.format("🎲 Monte Carlo: Prob. Ruina (20%% drawdown) = %.2f%%", ruinProbability * 100));

        // USO DE BOTCONFIG
        if (ruinProbability > BotConfig.RISK_MC_RUIN_THRESHOLD) {
            BotLogger.error("🚨 MONTE CARLO ALERT: Riesgo estadístico inaceptable. Bloqueando.");
            status.set(SystemStatus.HALTED_DRAWDOWN);
            return false;
        }
        return true;
    }
}