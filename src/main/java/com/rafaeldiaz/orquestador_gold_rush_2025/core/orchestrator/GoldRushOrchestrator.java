package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner.DeepMarketScanner;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 🎼 GOLD RUSH ORCHESTRATOR (v3.1 - PRE-FLIGHT READY)
 * El Director Ejecutivo. Gestiona el ciclo de vida y la validación de despegue.
 */
public class GoldRushOrchestrator {

    // Componentes Estructurales
    private final ExchangeConnector connector;
    private final ExecutionCoordinator coordinator;

    // Departamentos Especializados
    private final RiskManager riskManager;
    private final PortfolioHealthManager cfo;
    private final DeepMarketScanner scanner;

    // Control de Tiempo
    private final CountDownLatch missionLatch = new CountDownLatch(1);

    public GoldRushOrchestrator(ExchangeConnector connector, ExecutionCoordinator coordinator) {
        this.connector = connector;
        this.coordinator = coordinator;

        // 1. Departamento de Riesgo
        this.riskManager = new RiskManager(BotConfig.SEED_CAPITAL, coordinator);
        // 2. Departamento Financiero (CFO)
        this.cfo = new PortfolioHealthManager(connector);
        // 3. Departamento de Inteligencia (Cerebro)
        this.scanner = new DeepMarketScanner(connector, coordinator);
        this.scanner.setDryRun(BotConfig.DRY_RUN);
    }
    /**
     * 🚀 INICIA LA MISIÓN
     * Ejecuta PreFlightCheck -> Arranca Scanner -> Espera -> Apaga.
     */
    public void startMission() {
        try {
            // =================================================================
            // 🛫 PASO 1: PRE-FLIGHT CHECK (AUTORIDAD FINAL)
            // =================================================================
            // Validamos Java, Integridad FOK, Monte Carlo y Red.
            // Si esto falla, PreFlightCheck ejecutará System.exit(1).
            PreFlightCheck.runSequence(this.connector, this.riskManager);
            BotLogger.info("✅ SYSTEM OPERATIONAL. T-0: Liftoff.");
            // =================================================================
            // 🚀 PASO 2: ACTIVACIÓN DE MOTORES
            // =================================================================
            scanner.startOmniScan(BotConfig.SCAN_DURATION_MIN);

            // Reporte Inicial
            String auditLabel = BotConfig.getFullEnvironmentStatus();
            BotLogger.logSystemEvent("MISSION_START", auditLabel);
            BotLogger.sendTelegram("🏁 MISIÓN INICIADA (" + BotConfig.SCAN_DURATION_MIN + " min)\n" + auditLabel);

            // =================================================================
            // ⏱️ PASO 3: VUELO DE CRUCERO
            // =================================================================
            BotLogger.info("⏱️ CRONÓMETRO EN MARCHA. Supervisando en segundo plano...");

            // Bloqueamos el hilo principal hasta que termine el tiempo o stop() sea llamado
            boolean finishedOnTime = missionLatch.await(BotConfig.SCAN_DURATION_MIN, TimeUnit.MINUTES);

            if (!finishedOnTime) {
                BotLogger.warn("⌛ TIEMPO DE MISIÓN CUMPLIDO.");
            }

        } catch (InterruptedException e) {
            BotLogger.warn("🛑 INTERRUPCIÓN MANUAL DETECTADA.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            BotLogger.error("🔥 FALLO CRÍTICO EN VUELO: " + e.getMessage());
            // No hacemos throw aquí porque queremos ejecutar el shutdownSequence
        } finally {
            // =================================================================
            // 🛬 PASO 4: ATERRIZAJE SEGURO
            // =================================================================
            shutdownSequence();
        }
    }

    public void shutdownSequence() {
        BotLogger.warn("🔻 DIRECTOR: Iniciando protocolo de apagado...");

        // Detener Cerebro (Deja de enviar órdenes)
        if (scanner != null) {
            scanner.shutdown();
        }

        // Reporte Final
        double totalPnL = (scanner != null) ? scanner.getTotalPotentialProfit() : 0.0;
        long totalTrades = (scanner != null) ? scanner.getTradesCount() : 0;

        String endReport = String.format("MISSION_END | PnL: $%.2f | Trades: %d", totalPnL, totalTrades);
        BotLogger.logSystemEvent("MISSION_END", endReport);
        BotLogger.sendTelegram("🏁 FIN DE MISIÓN\n" + endReport);
        missionLatch.countDown();
    }

    public void triggerEmergencyStop() {
        missionLatch.countDown();
    }
}