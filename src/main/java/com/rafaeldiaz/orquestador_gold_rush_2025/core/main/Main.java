package com.rafaeldiaz.orquestador_gold_rush_2025.core.main;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner.DeepMarketScanner;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.RiskManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.execution.TriangularExecutor;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.SystemDiagnostics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {

        BotLogger.info("======================================================");
        BotLogger.info("🔥 INICIANDO GOLD RUSH 2025 (PRODUCCIÓN)...");

        // -----------------------------------------------------------
        // 1. INFRAESTRUCTURA BASE
        // -----------------------------------------------------------
        // El Coordinador es el semáforo que evita choques entre estrategias
        ExecutionCoordinator coordinator = new ExecutionCoordinator();
        ExchangeConnector connector = new ExchangeConnector();

        // Scheduler para la estrategia triangular (hilo dedicado)
        ScheduledExecutorService triangularScheduler = Executors.newSingleThreadScheduledExecutor();

        BotLogger.info("✅ [1/6] Conector Central y Coordinador: ONLINE");

        // -----------------------------------------------------------
        // 2. EL CEREBRO FINANCIERO (CFO)
        // -----------------------------------------------------------
        PortfolioHealthManager cfo = new PortfolioHealthManager(connector);
        BotLogger.info("✅ [2/6] CFO (Gestor de Salud): ONLINE");

        // -----------------------------------------------------------
        // 3. INICIALIZACIÓN DE COMPONENTES DE SOPORTE
        // -----------------------------------------------------------
        FeeManager feeManager = new FeeManager(connector);
        RiskManager riskManager = new RiskManager(BotConfig.SEED_CAPITAL);

        // Sistema Espacial (Arbitraje entre Exchanges)
        DeepMarketScanner scanner = new DeepMarketScanner(connector, coordinator);
        scanner.setDryRun(BotConfig.DRY_RUN);
        scanner.injectCFO(cfo);
        scanner.injectCoordinator(coordinator);

        BotLogger.info("✅ [3/6] Componentes Cargados. Iniciando Diagnóstico...");

        // -----------------------------------------------------------
        // 4. SECUENCIA DE DESPEGUE (SHOW VISUAL) 🎭
        // -----------------------------------------------------------
        // Esto valida red, saldo, seguridad y fees antes de operar.
        SystemDiagnostics.runSequence(connector, cfo, feeManager, riskManager);

        BotLogger.info("✅ [4/6] Diagnóstico Completado. Sistemas Nominales.");

        // -----------------------------------------------------------
        // 5. ACTIVACIÓN DE MOTORES
        // -----------------------------------------------------------

        // A. Motor Espacial (Scanner en Background)
        scanner.startOmniScan(BotConfig.SCAN_DURATION_MIN); // Usamos constante de config
        BotLogger.info("🚀 [5/6] Escáner Espacial: ORBITANDO");

        // B. Preparación Fuerza Aérea Triangular
        List<String> targets = BotConfig.FIXED_ASSETS;
        List<String> bridges = BotConfig.TRIANGULAR_ASSETS;
        Map<String, TriangularExecutor> executorCache = new HashMap<>();

        for (String accountName : BotConfig.TRIANGULAR_ACCOUNTS) {
            TriangularExecutor executor = new TriangularExecutor(connector, accountName, coordinator);
            executor.setDryRun(BotConfig.DRY_RUN);
            executorCache.put(accountName, executor);
            BotLogger.info("   ✈️ Escuadrón Desplegado: " + accountName);
        }

        BotLogger.info("🌪️ [6/6] Escáner Triangular: CAZANDO");

        // C. Bucle de Patrulla Triangular (Cada 5-10 segundos)
        triangularScheduler.scheduleAtFixedRate(() -> {
            try {
                for (String accountName : BotConfig.TRIANGULAR_ACCOUNTS) {
                    TriangularExecutor executor = executorCache.get(accountName);
                    if (executor == null) continue;

                    // Chequeo de seguridad de saldo mínimo para no saturar API si está vacía
                    double realBalance = connector.fetchBalance(accountName, "USDT");
                    if (realBalance < 10.0) continue;

                    double tradeSize = realBalance * BotConfig.TRADE_SIZE_PERCENT;

                    // Barrido de combinaciones
                    for (String target : targets) {
                        for (String bridge : bridges) {
                            if (target.equals(bridge)) continue; // Evitar pares idénticos

                            // El executor ya gestiona su propio bloqueo si encuentra oportunidad
                            executor.scanAndExecute(target, bridge, tradeSize);

                            // Pequeña pausa para no ametrallar la API en el bucle interno
                            Thread.sleep(50);
                        }
                    }
                }
            } catch (Exception e) {
                BotLogger.error("⚠️ Error Crítico en Bucle Triangular: " + e.getMessage());
            }
        }, 5, 10, TimeUnit.SECONDS);

        BotLogger.info("✅ [SISTEMA INTEGRADO]: Agente Operativo 24/7. Esperando oportunidades...");

        // -----------------------------------------------------------
        // 6. HEARTBEAT (Mantiene el Main vivo)
        // -----------------------------------------------------------
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            BotLogger.warn("🛑 Sistema interrumpido manualmente.");
            Thread.currentThread().interrupt();
        }
    }
}