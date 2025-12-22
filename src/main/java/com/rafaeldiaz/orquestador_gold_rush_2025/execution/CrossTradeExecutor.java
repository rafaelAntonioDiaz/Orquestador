package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.concurrent.CompletableFuture;

/**
 * ⚡ CROSS TRADE EXECUTOR (Arreglado para v4.0)
 * Ejecutor con Atomicidad (Rollback Automático) usando OrderResult.
 */
public class CrossTradeExecutor {

    private final ExchangeConnector connector;
    private boolean dryRun = true;

    // Constructor que recibe el conector (Inyección de Dependencia correcta)
    public CrossTradeExecutor(ExchangeConnector connector) {
        this.connector = connector;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        if(!dryRun) BotLogger.warn("⚠️ CROSS EXECUTOR: MODO FUEGO REAL ACTIVO");
    }

    /**
     * Ejecuta la maniobra "Pinza" simultánea y maneja fallos parciales.
     */
    public void executeCrossTrade(String buyExchange, String sellExchange, String pair, double buyPrice, double sellPrice) {
        if (dryRun) {
            BotLogger.info("[DRY-RUN] Simulación Cross-Exchange " + pair + " exitosa.");
            return;
        }

        BotLogger.info(String.format("⚡ EJECUTANDO CROSS: Compra %s | Venta %s", buyExchange, sellExchange));

        // 1. Cálculo de Cantidad (Usar capital seguro o dinámico)
        double tradeAmountUSDT = 20.0; // MVP: $20 por tiro
        double rawQty = tradeAmountUSDT / buyPrice;

        // Normalización usando la nueva inteligencia del conector
        double stepSize = connector.getStepSize(buyExchange, pair);
        final double qty = Math.floor(rawQty / stepSize) * stepSize;

        if (qty <= 0) {
            BotLogger.error("🚫 Cantidad normalizada inválida.");
            return;
        }

        // 2. DISPARO SIMULTÁNEO (ASYNC)
        // Ahora esperamos OrderResult, no String
        CompletableFuture<OrderResult> buyTask = CompletableFuture.supplyAsync(() ->
                connector.placeOrder(buyExchange, pair, "BUY", "MARKET", qty, 0)
        );

        CompletableFuture<OrderResult> sellTask = CompletableFuture.supplyAsync(() ->
                connector.placeOrder(sellExchange, pair, "SELL", "MARKET", qty, 0)
        );

        // 3. ESPERAR RESULTADOS (JOIN)
        OrderResult buyResult = null;
        OrderResult sellResult = null;

        try {
            CompletableFuture.allOf(buyTask, sellTask).join();
            buyResult = buyTask.get();
            sellResult = sellTask.get();

        } catch (Exception e) {
            BotLogger.error("🔥 Error crítico en hilos de ejecución: " + e.getMessage());
        }

        // 4. ANÁLISIS DE ATOMICIDAD (Verdad vs Inferencia)
        boolean buyOk = (buyResult != null && buyResult.isFilled());
        boolean sellOk = (sellResult != null && sellResult.isFilled());

        if (buyOk && sellOk) {
            // ÉXITO TOTAL
            BotLogger.info("✅✅ CROSS TRADE PERFECTO. IDs: " + buyResult.orderId() + " / " + sellResult.orderId());
            BotLogger.sendTelegram("✅ WIN! Arbitraje " + pair + " completado.");

        } else if (!buyOk && !sellOk) {
            // FALLO TOTAL (Nada pasó)
            BotLogger.warn("❌❌ Ambas órdenes fallaron. Capital seguro.");

        } else {
            // 🚨 PELIGRO: EJECUCIÓN PARCIAL
            handlePartialFailure(buyExchange, buyResult, sellExchange, sellResult, pair, qty);
        }
    }

    /**
     * Lógica de Reversión (Rollback) para salvar el capital.
     */
    private void handlePartialFailure(String buyEx, OrderResult buyRes, String sellEx, OrderResult sellRes, String pair, double qty) {
        BotLogger.error("🚨 EJECUCIÓN PARCIAL DETECTADA. INICIANDO PROTOCOLO DE EMERGENCIA.");
        BotLogger.sendTelegram("🚨 EJECUCIÓN PARCIAL! Intentando Rollback...");

        boolean buyFilled = (buyRes != null && buyRes.isFilled());
        boolean sellFilled = (sellRes != null && sellRes.isFilled());

        // CASO A: Compramos, pero falló la venta (Long Exposure)
        if (buyFilled && !sellFilled) {
            BotLogger.warn("⚠️ Compramos en " + buyEx + " pero falló venta en " + sellEx);
            BotLogger.warn("🔄 ROLLBACK: Vendiendo inmediatamente en " + buyEx);

            // Intentamos vender lo que realmente compramos
            double qtyToRollback = buyRes.executedQty();
            OrderResult rollback = connector.placeOrder(buyEx, pair, "SELL", "MARKET", qtyToRollback, 0);

            if (rollback.isFilled()) {
                BotLogger.info("✅ ROLLBACK EXITOSO: Posición cerrada en " + buyEx);
            } else {
                BotLogger.error("💀 FATAL: Falló el Rollback. Bag holder en " + buyEx);
                BotLogger.sendTelegram("💀 FATAL: Atrapados en " + buyEx + ". Revisar manual!");
            }
        }

        // CASO B: Vendimos, pero falló la compra (Short Exposure / Faltante)
        else if (!buyFilled && sellFilled) {
            BotLogger.warn("⚠️ Vendimos en " + sellEx + " pero falló compra en " + buyEx);
            BotLogger.warn("🔄 ROLLBACK: Re-comprando inmediatamente en " + sellEx);

            // Intentamos reponer lo vendido
            double qtyToRollback = sellRes.executedQty();
            OrderResult rollback = connector.placeOrder(sellEx, pair, "BUY", "MARKET", qtyToRollback, 0);

            if (rollback.isFilled()) {
                BotLogger.info("✅ ROLLBACK EXITOSO: Inventario repuesto en " + sellEx);
            } else {
                BotLogger.error("💀 FATAL: Falló recompra en " + sellEx);
                BotLogger.sendTelegram("💀 FATAL: Short descubierto en " + sellEx);
            }
        }
    }
}