package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 📉 RISK MANAGEMENT SYSTEM (PERFORMANCE OPTIMIZED)
 * Patrón: Configuración Inmutable. Lee parámetros al inicio y los cachea.
 * Cero overhead de lectura de configuración en tiempo de ejecución.
 */
public class RiskManager {
    private final ExecutionCoordinator coordinator;
    private final String stateFile;

    // ⚙️ PARÁMETROS DE RIESGO (CACHED FINAL)
    // Se leen una sola vez al instanciar. Eficiencia máxima.
    private final double maxDailyLossLimit;
    private final double maxDrawdownLimit;
    private final int maxConsecutiveLosses;
    private final long streakPauseMs;
    private final double mcRuinThreshold;

    // Estado Financiero
    private double initialDailyCapital;
    private double currentCapital;
    private double peakCapital;
    private double dailyPnL = 0.0;
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

        // 1. CAPTURA DE CONFIGURACIÓN (SNAPSHOT AL INICIO)
        // Usamos los getters para permitir que el Test inyecte valores si el Mock está activo.
        // En producción, esto lee las constantes estáticas una sola vez.
        this.maxDailyLossLimit = BotConfig.getRiskMaxDailyLoss();
        this.maxDrawdownLimit = BotConfig.getRiskMaxDrawdown();
        this.maxConsecutiveLosses = BotConfig.getRiskMaxConsecutiveLosses();
        this.streakPauseMs = BotConfig.getRiskStreakPauseMs();
        this.mcRuinThreshold = BotConfig.getRiskMcRuinThreshold();

        BotLogger.info("🛡️ RiskManager: Iniciando carga de estado desde: " + stateFile);
        loadFinancialState();

        BotLogger.info(String.format("🛡️ PARÁMETROS ACTIVOS: MaxLoss=%.1f%% | MaxDD=%.1f%% | MaxStreak=%d",
                maxDailyLossLimit * 100, maxDrawdownLimit * 100, maxConsecutiveLosses));

        validateRiskParameters();
    }

    public synchronized boolean canExecuteTrade() {
        if (status.get() == SystemStatus.PAUSED_DEVIATION) {
            if (System.currentTimeMillis() > pauseUntilTimestamp) {
                BotLogger.info("🟢 COOLDOWN FINALIZADO. Reactivando sistema.");
                status.set(SystemStatus.OPERATIONAL);
                consecutiveLosses = 0;
                return true;
            }
            return false;
        }
        return status.get() == SystemStatus.OPERATIONAL;
    }

    public synchronized void reportTradeResult(double pnlUSD) {
        currentCapital += pnlUSD;
        dailyPnL += pnlUSD;
        if (currentCapital > peakCapital) peakCapital = currentCapital;

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
        // A. DISYUNTOR DIARIO (Usando variables cacheadas)
        double dailyLossRatio = -dailyPnL / initialDailyCapital;
        if (dailyPnL < 0 && dailyLossRatio >= this.maxDailyLossLimit) {
            status.set(SystemStatus.HALTED_DAILY_LIMIT);
            BotLogger.error(String.format("🛑 DISYUNTOR DIARIO ACTIVADO. Pérdida: %.2f%%.", dailyLossRatio * 100));
            coordinator.forceGlobalLockdown("DAILY_LOSS_LIMIT");
            return;
        }

        // B. DISYUNTOR DRAWDOWN
        double currentDrawdown = (peakCapital - currentCapital) / peakCapital;
        if (currentDrawdown >= this.maxDrawdownLimit) {
            status.set(SystemStatus.HALTED_DRAWDOWN);
            BotLogger.error(String.format("💀 CRITICAL DRAWDOWN (%.2f%%).", currentDrawdown * 100));
            // coordinator.forceGlobalLockdown("MAX_DRAWDOWN");
            return;
        }

        // C. DISYUNTOR RACHA
        if (consecutiveLosses >= this.maxConsecutiveLosses) {
            status.set(SystemStatus.PAUSED_DEVIATION);
            this.pauseUntilTimestamp = System.currentTimeMillis() + this.streakPauseMs;
            BotLogger.warn("⚠️ RACHA DE PÉRDIDAS (>limit). Pausando sistema por enfriamiento.");
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
        } catch (IOException e) { BotLogger.error("Error IO Risk: " + e.getMessage()); }
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
        } catch (IOException e) { BotLogger.error("Error Load Risk: " + e.getMessage()); }
    }

    public void overrideLockdown() {
        status.set(SystemStatus.OPERATIONAL); consecutiveLosses = 0; pauseUntilTimestamp = 0; saveFinancialState();
    }

    public boolean runMonteCarloSimulation(double winRate, double avgWin, double avgLoss) {
        int simulations = 1000; int tradesPerSim = 100; int ruinCount = 0;
        double ruinThreshold = currentCapital * (1.0 - 0.20); // 20% DD hardcap para MC
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();

        for (int i = 0; i < simulations; i++) {
            double simulatedEquity = currentCapital;
            for (int t = 0; t < tradesPerSim; t++) {
                if (random.nextDouble() < winRate) simulatedEquity += avgWin; else simulatedEquity -= avgLoss;
                if (simulatedEquity < ruinThreshold) { ruinCount++; break; }
            }
        }
        double ruinProbability = (double) ruinCount / simulations;
        BotLogger.info(String.format("🎲 Monte Carlo: Prob. Ruina = %.2f%%", ruinProbability * 100));

        if (ruinProbability > this.mcRuinThreshold) {
            BotLogger.error("🚨 RIESGO INACEPTABLE. Monte Carlo falló.");
            return false;
        }
        return true;
    }
}