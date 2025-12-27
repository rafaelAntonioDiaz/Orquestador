package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
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

        // --- FUEGO PARALELO (ESTÁNDAR JAVA 21+) ---
        // Usamos un Executor efímero que lanza un Hilo Virtual por cada tarea.
        // El try-with-resources asegura que se cierre automáticamente al terminar.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // 1. DISPARAR (FORK)
            // Enviamos las dos balas al mismo tiempo. No bloquea aquí.
            Future<OrderResult> fBuy = executor.submit(() ->
                    connector.placeOrder(buyExchange, pair, "BUY", "MARKET", qty, 0)
            );

            Future<OrderResult> fSell = executor.submit(() ->
                    connector.placeOrder(sellExchange, pair, "SELL", "MARKET", qty, 0)
            );

            // 2. RECOLECTAR (JOIN)
            // .get() esperará a que el hilo virtual termine.
            // Si hay error en la red, capturamos la excepción de forma segura.
            OrderResult buyResult = safeGet(fBuy);
            OrderResult sellResult = safeGet(fSell);

            // 3. PROCESAR
            processResults(buyExchange, buyResult, sellExchange, sellResult, pair, qty);

        } catch (Exception e) {
            BotLogger.error("🔥 Error Crítico en Executor: " + e.getMessage());
        }
    }
    private void processResults(String buyEx, OrderResult buyRes, String sellEx, OrderResult sellRes, String pair, double originalQty) {
        boolean buyOk = (buyRes != null && buyRes.isFilled());
        boolean sellOk = (sellRes != null && sellRes.isFilled());

        // A. ÉXITO TOTAL
        if (buyOk && sellOk) {
            double pnl = (sellRes.executedValue()) - (buyRes.executedValue());

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


    private void handlePartialFailure(String buyEx, OrderResult buyRes, String sellEx, OrderResult sellRes, String pair) {
        boolean buyOk = (buyRes != null && buyRes.isFilled());
        boolean sellOk = (sellRes != null && sellRes.isFilled());

        if (buyOk && !sellOk) {
            BotLogger.warn("🔄 ROLLBACK: Vendiendo en " + buyEx + " (Fallo venta en " + sellEx + ")");
            connector.placeOrder(buyEx, pair, "SELL", "MARKET", buyRes.executedQty(), 0);
            coordinator.reportFailure(sellEx);
        }
        else if (!buyOk && sellOk) {
            BotLogger.warn("🔄 ROLLBACK: Re-comprando en " + sellEx + " (Fallo compra en " + buyEx + ")");
            connector.placeOrder(sellEx, pair, "BUY", "MARKET", sellRes.executedQty(), 0);
            coordinator.reportFailure(buyEx);
        }
        else {
            BotLogger.error("❌ FALLO TOTAL: Ninguna orden entró. (Sin impacto financiero)");
        }
    }
}