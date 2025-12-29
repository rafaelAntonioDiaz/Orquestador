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

/**
 * <h1>Orquestador Principal - Gold Rush 2025 (Agente Tokio)</h1>
 * <p>
 * VERSIÓN ENDURANCE TEST (MODO MINUTOS) - CON RÓTULO DE AUDITORÍA COMPLETA
 * </p>
 */
public class Main {

    public static void main(String[] args) {

        try {
            BotLogger.info("======================================================");
            BotLogger.info("🔥 INICIANDO GOLD RUSH 2025 (MODO ENDURANCE TEST)...");

            // -----------------------------------------------------------
            // 1. INFRAESTRUCTURA BASE
            // -----------------------------------------------------------
            ExecutionCoordinator coordinator = new ExecutionCoordinator();
            ExchangeConnector connector = new ExchangeConnector();
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

            DeepMarketScanner scanner = new DeepMarketScanner(connector, coordinator);
            scanner.setDryRun(BotConfig.DRY_RUN);
            scanner.injectCFO(cfo);
            scanner.injectCoordinator(coordinator);

            BotLogger.info("✅ [3/6] Componentes Cargados. Iniciando Diagnóstico...");

            // -----------------------------------------------------------
            // 4. SECUENCIA DE DESPEGUE
            // -----------------------------------------------------------
            SystemDiagnostics.runSequence(connector, cfo, feeManager, riskManager);
            BotLogger.info("✅ [4/6] Diagnóstico Completado. Sistemas Nominales.");

            // -----------------------------------------------------------
            // 5. ACTIVACIÓN DE MOTORES
            // -----------------------------------------------------------
            scanner.startOmniScan(BotConfig.SCAN_DURATION_MIN);
            BotLogger.info("🚀 [5/6] Escáner Espacial: ORBITANDO (Duración: " + BotConfig.SCAN_DURATION_MIN + " min)");

            List<String> targets = BotConfig.FIXED_ASSETS;
            List<String> bridges = BotConfig.TRIANGULAR_ASSETS;
            Map<String, TriangularExecutor> executorCache = new HashMap<>();

            for (String accountName : BotConfig.TRIANGULAR_ACCOUNTS) {
                TriangularExecutor executor = new TriangularExecutor(connector);
                executor.setDryRun(BotConfig.DRY_RUN);
                executorCache.put(accountName, executor);
                BotLogger.info("   ✈️ Escuadrón Desplegado: " + accountName);
            }

            BotLogger.info("🌪️ [6/6] Escáner Triangular: CAZANDO");

// -----------------------------------------------------------
            // 🌪️ BUCLE TRIANGULAR (MOTOR CORREGIDO v2.0)
            // -----------------------------------------------------------
            triangularScheduler.scheduleAtFixedRate(() -> {
                try {
                    for (String accountName : BotConfig.TRIANGULAR_ACCOUNTS) {
                        TriangularExecutor executor = executorCache.get(accountName);
                        if (executor == null) continue;

                        // 1. CHEQUEO DE COMBUSTIBLE
                        // Validamos saldo real una vez por ciclo de cuenta
                        // (Podemos optimizar esto con caché luego, pero por seguridad lo dejamos)
                        double realBalance = connector.fetchBalance(accountName, "USDT");
                        if (realBalance < 10.0) continue;

                        // 2. DESCARGA MASIVA DE PRECIOS (Batch Fetch)
                        // Extraemos el nombre base del exchange (ej: "bybit_sub2" -> "bybit")
                        String exchangeBase = accountName.split("_")[0];
                        Map<String, Double> prices = connector.fetchAllPrices(exchangeBase);

                        if (prices == null || prices.isEmpty()) continue;

                        // 3. FUERZA BRUTA EN MEMORIA (Zero Latency)
                        for (String target : targets) {     // Ej: PEPE
                            for (String bridge : bridges) { // Ej: SOL
                                if (target.equals(bridge)) continue;

                                // ✅ TELEMETRÍA: Registramos intento de cálculo
                                com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.MetricsService.get().recordOp();

                                // Construcción de Pares (Naming Convention Standard)
                                // Ruta: USDT -> Target -> Bridge -> USDT
                                String p1 = target + "USDT"; // Buy Target (Entry)
                                String p2 = target + bridge; // Sell Target for Bridge (Cross)
                                String p3 = bridge + "USDT"; // Sell Bridge for USDT (Exit)

                                // Búsqueda O(1) en el mapa
                                Double price1 = prices.get(p1);
                                Double price2 = prices.get(p2);
                                Double price3 = prices.get(p3);

                                // Si falta algún par en el mercado, saltamos
                                if (price1 == null || price2 == null || price3 == null) continue;

                                // 🧮 CÁLCULO DE ARBITRAJE TRIANGULAR
                                // Fórmula: (1 USDT / P1) * P2 * P3 = USDT Finales
                                double crossRate = (1.0 / price1) * price2 * price3;

                                // Verificamos si supera el umbral configurado (ej: 1.005 para 0.5%)
                                if (crossRate > (1.0 + BotConfig.MIN_SCAN_SPREAD)) {

                                    // Cálculo de profit estimado
                                    double potentialProfit = (crossRate - 1.0) * realBalance;

                                    BotLogger.trade(String.format("📐 OPORTUNIDAD TRIANGULAR: %s-%s | Profit: %.2f%% ($%.2f)",
                                            target, bridge, (crossRate - 1.0) * 100, potentialProfit));

                                    // 🔥 EJECUCIÓN FUEGO REAL
                                    // Usamos 'realBalance' completo o un % configurado
                                    double tradeCap = Math.min(realBalance, BotConfig.SEED_CAPITAL); // Capamos por config

                                    executor.executeSequence(
                                            accountName,    // Cuenta (ej: bybit_sub2)
                                            target,         // Asset (ej: PEPE)
                                            bridge,         // Bridge (ej: SOL)
                                            p1, p2, p3,     // Pares String
                                            tradeCap,       // Capital USDT
                                            price1          // Precio límite ref
                                    );
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    BotLogger.error("⚠️ Error Crítico en Bucle Triangular: " + e.getMessage());
                    com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.MetricsService.get().recordError("TriangularLoop");
                }
            }, 0, 500, TimeUnit.MILLISECONDS); // Bajamos a 500ms el ciclo completo (más agresivo)
            BotLogger.info("✅ [SISTEMA INTEGRADO]: Agente Operativo. Iniciando Cronómetro.");

            // -----------------------------------------------------------
            // 📸 FOTO INICIAL (RÓTULO COMPLETO DE AUDITORÍA)
            // -----------------------------------------------------------
            // Aquí obtenemos el estado COMPLETO, sin resúmenes.
            String fullAuditLabel = BotConfig.getFullEnvironmentStatus();

            // Logueamos en el CSV como evento de sistema
            BotLogger.logSystemEvent("TEST_START", fullAuditLabel);

            // Telegram recibe un resumen visual y el detalle técnico
            double durationHours = BotConfig.SCAN_DURATION_MIN / 60.0;
            BotLogger.sendTelegram(String.format("🏁 INICIO DE PRUEBA (%.1fh)\n%s", durationHours, fullAuditLabel));

            // -----------------------------------------------------------
            // ⏳ EL SUEÑO DE LOS JUSTOS
            // -----------------------------------------------------------
            long endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(BotConfig.SCAN_DURATION_MIN);

            BotLogger.info(String.format("⏱️ CRONÓMETRO INICIADO: %d Minutos (%.1f Horas).",
                    BotConfig.SCAN_DURATION_MIN, durationHours));
            BotLogger.info("   -> Hora estimada de finalización: " + new java.util.Date(endTime));

            Thread.sleep(TimeUnit.MINUTES.toMillis(BotConfig.SCAN_DURATION_MIN));

            // -----------------------------------------------------------
            // 🛑 ATERRIZAJE (SHUTDOWN SEQUENCE)
            // -----------------------------------------------------------
            BotLogger.warn("⌛ TIEMPO CUMPLIDO. Iniciando protocolo de cierre...");

            scanner.shutdown();
            triangularScheduler.shutdownNow();

            double totalPnL = scanner.getTotalPotentialProfit();
            long totalTrades = scanner.getTradesCount();

            // 3. FOTO FINAL (CON RÓTULO COMPLETO NUEVAMENTE)
            String endResult = String.format("STATUS:COMPLETED | PnL:%.4f | TRADES:%d | CONTEXT:[%s]",
                    totalPnL, totalTrades, fullAuditLabel);

            BotLogger.logSystemEvent("TEST_END", endResult);
            BotLogger.sendTelegram("🏁 FIN DE PRUEBA\n" + endResult);

            BotLogger.info("🏁 Prueba finalizada con éxito. Apagando JVM.");
            Thread.sleep(2000);
            System.exit(0);

        } catch (InterruptedException e) {
            BotLogger.warn("🛑 Sistema interrumpido manualmente.");
            BotLogger.logSystemEvent("TEST_ABORT", "Interrupcion Manual");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            BotLogger.error("🔥 FALLO CATASTRÓFICO: " + e.getMessage());
            e.printStackTrace();
        }
    }
}