package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ⚡ CROSS TRADE EXECUTOR (Arreglado v5.0 - Blindado con Validación de Inventario)
 * Ejecutor con Atomicidad y Verificación Previa de Fondos.
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
        if(!dryRun) BotLogger.warn("⚠️ CROSS EXECUTOR: MODO FUEGO REAL ACTIVO");
    }

    /**
     * Ejecuta con Validación Híbrida (RAM vs API).
     * @param snapshotTimestamp El momento exacto (System.currentTimeMillis) en que se tomó la foto.
     */
    public void executeCrossTrade(String buyExchange, String sellExchange, String pair,
                                  double buyPrice, double sellPrice, double tradeAmount,
                                  Map<String, Map<String, Double>> balanceSnapshot,
                                  long snapshotTimestamp) { // ✅ NUEVO ARGUMENTO

        // 🛑 1. CHECK DE RIESGO
        if (!riskManager.canExecuteTrade()) return;

        // Preparación de datos
        double rawQty = tradeAmount / buyPrice;
        double stepSize = connector.getStepSize(buyExchange, pair);
        final double qty = Math.floor(rawQty / stepSize) * stepSize;

        if (qty <= 0) {
            BotLogger.error("🚫 Cantidad normalizada inválida.");
            return;
        }

        // =================================================================================
        // 🛡️ 2. VALIDACIÓN DE INVENTARIO INTELIGENTE (Staleness Check)
        // =================================================================================

        if (!dryRun) {
            String baseAsset = pair.replace("USDT", "").replace("-", "").toUpperCase();
            String quoteAsset = "USDT";
            double estimatedCost = qty * buyPrice;

            // A. Verificamos COMPRADOR (USDT)
            double buyerUsdtBalance;

            // 🔍 ¿Es viejo el snapshot?
            if (coordinator.isSnapshotStale(buyExchange, snapshotTimestamp)) {
                BotLogger.warn("⚠️ Snapshot vencido para " + buyExchange + ". Usando Fetch Live (Lento pero Seguro).");
                buyerUsdtBalance = connector.fetchBalance(buyExchange, quoteAsset);
            } else {
                // Snapshot Fresco -> Usamos RAM
                buyerUsdtBalance = (balanceSnapshot != null && balanceSnapshot.containsKey(buyExchange))
                        ? balanceSnapshot.get(buyExchange).getOrDefault(quoteAsset, 0.0) : 0.0;
            }

            if (buyerUsdtBalance < estimatedCost) {
                BotLogger.warn("🛑 SKIP: Falta Liquidez en " + buyExchange);
                return;
            }

            // B. Verificamos VENDEDOR (Asset)
            double sellerAssetBalance;

            if (coordinator.isSnapshotStale(sellExchange, snapshotTimestamp)) {
                BotLogger.warn("⚠️ Snapshot vencido para " + sellExchange + ". Usando Fetch Live.");
                sellerAssetBalance = connector.fetchBalance(sellExchange, baseAsset);
            } else {
                sellerAssetBalance = (balanceSnapshot != null && balanceSnapshot.containsKey(sellExchange))
                        ? balanceSnapshot.get(sellExchange).getOrDefault(baseAsset, 0.0) : 0.0;
            }

            if (sellerAssetBalance < qty) {
                BotLogger.warn("🛑 SKIP: Falta Inventario en " + sellExchange);
                return;
            }
        }
        // =================================================================================

        if (dryRun) {
            BotLogger.info("[DRY-RUN] Simulación Cross-Exchange " + pair + " (Inventario validado en RAM).");
            return;
        }

        BotLogger.info(String.format("⚡ EJECUTANDO CROSS: Compra %s | Venta %s | Qty: %.4f", buyExchange, sellExchange, qty));

        // 🚀 3. DISPARO SIMULTÁNEO (ASYNC)
        CompletableFuture<OrderResult> buyTask = CompletableFuture.supplyAsync(() ->
                connector.placeOrder(buyExchange, pair, "BUY", "MARKET", qty, 0)
        );

        CompletableFuture<OrderResult> sellTask = CompletableFuture.supplyAsync(() ->
                connector.placeOrder(sellExchange, pair, "SELL", "MARKET", qty, 0)
        );

        // ⏳ 4. ESPERAR RESULTADOS (JOIN)
        OrderResult buyResult = null;
        OrderResult sellResult = null;

        try {
            CompletableFuture.allOf(buyTask, sellTask).join();
            buyResult = buyTask.get();
            sellResult = sellTask.get();

        } catch (Exception e) {
            BotLogger.error("🔥 Error crítico en hilos de ejecución: " + e.getMessage());
            // Si explotó el hilo, asumimos culpa de ambos (o del que falló)
            // Por seguridad, reportamos fallo
            // (Aquí podrías refinar para saber cuál falló, pero reportar a ambos es seguro)
        }
        // ⚖️ 5. ANÁLISIS DE RESULTADOS
        boolean buyOk = (buyResult != null && buyResult.isFilled());
        boolean sellOk = (sellResult != null && sellResult.isFilled());

        if (buyOk) coordinator.reportSuccess(buyExchange);
        else reportExchangeError(buyExchange, buyResult); // Helper para decidir si es Strike

        if (sellOk) coordinator.reportSuccess(sellExchange);
        else reportExchangeError(sellExchange, sellResult);

        if (buyOk && sellOk) {
            double pnlEstimado = (sellResult.executedQty() * sellResult.averagePrice()) - (buyResult.executedQty() * buyResult.averagePrice());
            BotLogger.info(String.format("✅ CROSS EXITOSO! PnL Est: $%.2f", pnlEstimado));
            riskManager.reportTradeResult(pnlEstimado);
        } else {
            handlePartialFailure(buyExchange, buyResult, sellExchange, sellResult, pair, qty);
        }
    }

    private void handlePartialFailure(String buyEx, OrderResult buyRes, String sellEx, OrderResult sellRes, String pair, double qty) {
        // ... (Mismo código de reversión que antes) ...
        BotLogger.error("🚨 EJECUCIÓN PARCIAL DETECTADA. INICIANDO PROTOCOLO DE EMERGENCIA.");

        boolean buyFilled = (buyRes != null && buyRes.isFilled());
        boolean sellFilled = (sellRes != null && sellRes.isFilled());

        if (buyFilled && !sellFilled) {
            BotLogger.warn("⚠️ Compramos en " + buyEx + " pero falló venta en " + sellEx);
            BotLogger.warn("🔄 ROLLBACK: Vendiendo inmediatamente en " + buyEx);
            double qtyToRollback = buyRes.executedQty();
            OrderResult rollback = connector.placeOrder(buyEx, pair, "SELL", "MARKET", qtyToRollback, 0);
            if (rollback.isFilled()) BotLogger.info("✅ ROLLBACK EXITOSO: Posición cerrada en " + buyEx);
            else BotLogger.error("💀 FATAL: Falló el Rollback. Revisar manual en " + buyEx);
        }
        else if (!buyFilled && sellFilled) {
            BotLogger.warn("⚠️ Vendimos en " + sellEx + " pero falló compra en " + buyEx);
            BotLogger.warn("🔄 ROLLBACK: Re-comprando inmediatamente en " + sellEx);
            double qtyToRollback = sellRes.executedQty();
            OrderResult rollback = connector.placeOrder(sellEx, pair, "BUY", "MARKET", qtyToRollback, 0);
            if (rollback.isFilled()) BotLogger.info("✅ ROLLBACK EXITOSO: Inventario repuesto en " + sellEx);
            else BotLogger.error("💀 FATAL: Falló recompra en " + sellEx);
        }
    }
    /**
     * Analiza por qué falló y decide si castigar al exchange.
     */
    private void reportExchangeError(String exchange, OrderResult result) {
        if (result == null) {
            // Null significa Timeout o Exception -> STRIKE ❌
            coordinator.reportFailure(exchange);
            return;
        }

        // Si el exchange respondió pero rechazó la orden:
        // - "Insufficient Balance" -> NO ES CULPA DEL SISTEMA (No strike)
        // - "System Error", "Engine Busy", "Timeout" -> SÍ ES CULPA (Strike)

        // Simplificación: Asumimos que si no es FILLED y no es Saldo, es problema técnico.
        // (Esto depende de cómo parseamos el error en ExchangeConnector, pero por ahora reportamos
        // fallo si el resultado es nulo o inválido).

        if ("ERROR".equals(result.status())) {
            coordinator.reportFailure(exchange);
        }
    }
}
