package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.DecisionAuditor;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * ⚡ CROSS TRADE EXECUTOR (Versión 1.1 Ninja - Zero Friction)
 * Ejecución paralela real sin bloqueos de log previos al disparo.
 */
public class CrossTradeExecutor {

    private final ExchangeConnector connector;
    private boolean dryRun = true;
    private final RiskManager riskManager;
    private final ExecutionCoordinator coordinator;

    public CrossTradeExecutor(
            ExchangeConnector connector, RiskManager riskManager,
            ExecutionCoordinator coordinator) {
        this.connector = connector;
        this.riskManager = riskManager;
        this.coordinator = coordinator;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        if (!dryRun) BotLogger.warn("⚠️ CROSS EXECUTOR: MODO FUEGO REAL ACTIVO");
    }

    public void executeCrossTrade(String buyExchange, String sellExchange, String pair,
                                  double qty, double buyPriceLog, double sellPriceLog) {

        if (!riskManager.canExecuteTrade()) return;

        if (dryRun) {
            BotLogger.info("[DRY-RUN] Cross: Buy " + buyExchange + " / Sell " + sellExchange + " Qty: " + qty);
            return;
        }
        double capitalUsdt = qty * buyPriceLog;
        // LOG DE INICIO DE BATALLA

        // --- FUEGO PARALELO (ESTÁNDAR JAVA 21+) ---
        // Usamos un Executor efímero que lanza un Hilo Virtual por cada tarea.
        // El try-with-resources asegura que se cierre automáticamente al terminar.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // 1. DISPARAR (FORK)
            // Enviamos las dos balas al mismo tiempo. No bloquea aquí.
            Future<OrderResult> fBuy = executor.submit(() ->
                    connector.placeOrder(
                            buyExchange,
                            pair,
                            "BUY",
                            "MARKET",
                            capitalUsdt, // <--- Importante: Pasa el CAPITAL en USDT (ej. 50.0), no la cantidad de tokens
                            0,
                            true     // <--- ¡ESTE TRUE ES LA CLAVE! Activa el modo "Gastar USDT
                    ));

            Future<OrderResult> fSell = executor.submit(() ->
                    connector.placeOrder(
                            sellExchange, // Exchange B (¡Ojo! Era buyExchange en tu snippet)
                            pair,
                            "SELL",       // (¡Ojo! Era BUY en tu snippet)
                            "MARKET",
                            qty,          // <--- Enviamos Tokens (Ej. 25.5 IMX)
                            0,
                            false         // <--- FALSE: "Vender X Tokens" (Base Order)
                    ));

            // 2. RECOLECTAR (JOIN)
            // .get() esperará a que el hilo virtual termine.
            // Si hay error en la red, capturamos la excepción de forma segura.
            OrderResult buyResult = safeGet(fBuy);
            OrderResult sellResult = safeGet(fSell);

            // 3. PROCESAR
            processResults(buyExchange, buyResult, sellExchange, sellResult, pair, qty);

        } catch (Exception e) {
            BotLogger.error("🔥 Error Crítico en Executor: " + e.getMessage());
            DecisionAuditor.log("SPATIAL", pair, "SYSTEM_ERROR", 0
                    , 0, "EJECUCION", "ERROR", e.getMessage());
        }
    }
    private void processResults(String buyEx, OrderResult buyRes, String sellEx, OrderResult sellRes, String pair, double originalQty) {
        boolean buyOk = (buyRes != null && buyRes.isFilled());
        boolean sellOk = (sellRes != null && sellRes.isFilled());
        String route = buyEx + "->" + sellEx; // <--- AQUÍ CONSTRUIMOS LA RUTA
        String asset = pair.replace("USDT", "").replace("-", ""); // Limpiamos "BTCUSDT" a "BTC"
        // A. ÉXITO TOTAL
        if (buyOk && sellOk) {
            double pnl = (sellRes.executedValue()) - (buyRes.executedValue());
        // ✅ BATALLA: VICTORIA
            DecisionAuditor.log("SPATIAL", pair, route, 0.0, pnl,
                    "BATALLA", "EXIT_FILLED", "PnL Real: $" + String.format("%.4f", pnl));
            // Reporte asíncrono
            Thread.ofVirtual().start(() -> riskManager.reportTradeResult(pnl));
            coordinator.reportSuccess(buyEx);
            coordinator.reportSuccess(sellEx);
            BotLogger.info("✅ CROSS WIN: PnL estimado $" + pnl);
            return;
        }

        // B. FALLO PARCIAL (ROLLBACK)
        handlePartialFailure(buyEx, buyRes, sellEx, sellRes, pair);
    }

    private void handlePartialFailure(String buyEx, OrderResult buyRes, String sellEx, OrderResult sellRes, String pair) {
        boolean buyOk = (buyRes != null && buyRes.isFilled());
        boolean sellOk = (sellRes != null && sellRes.isFilled());

        if (buyOk && !sellOk) {
            BotLogger.warn("🔄 ROLLBACK: Vendiendo en " + buyEx + " (Fallo venta en " + sellEx + ")");
            connector.placeOrder(buyEx, pair, "SELL", "MARKET", buyRes.executedQty(), 0);
            coordinator.reportFailure(sellEx);
            // 📝 AUDITORÍA: HUÉRFANO (COMPRA OK, VENTA FAIL)
            DecisionAuditor.log("SPATIAL", pair, buyEx + "->X", 0, -1.0,
                    "BATALLA", "ORPHAN_DETECTED", "Fallo Venta " + sellEx + ". Rollback intentado.");
        }
        else if (!buyOk && sellOk) {
            BotLogger.warn("🔄 ROLLBACK: Re-comprando en " + sellEx + " (Fallo compra en " + buyEx + ")");
            connector.placeOrder(sellEx, pair, "BUY", "MARKET", sellRes.executedQty(), 0);
            coordinator.reportFailure(buyEx);
            // 📝 AUDITORÍA: HUÉRFANO (COMPRA FAIL, VENTA OK)
            DecisionAuditor.log("SPATIAL", pair, "X->" + sellEx, 0, -1.0,
                    "BATALLA", "ORPHAN_DETECTED", "Fallo Compra " + buyEx + ". Rollback intentado.");
        }
        else {
            BotLogger.error("❌ FALLO TOTAL: Ninguna orden entró.");
            // 📝 AUDITORÍA: ERROR DE SALIDA
            DecisionAuditor.log("SPATIAL", pair, "FAIL->FAIL", 0, 0,
                    "EJECUCION", "ORDER_FAILED", "Rechazo simultáneo ambos lados.");        }
    }
    // Método auxiliar para manejar el .get() sin ensuciar la lógica principal con try-catch
    private OrderResult safeGet(Future<OrderResult> future) {
        try {
            return future.get(); // Esto bloquea el hilo virtual, no el del sistema operativo. ¡Eficiente!
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            BotLogger.error("⚠️ Error en ejecución de orden: " + e.getCause().getMessage());
            return null;
        }
    }
}