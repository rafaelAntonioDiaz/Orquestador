package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.concurrent.CompletableFuture;

/**
 * Task 5.4 (RESUELTA): Ejecutor con Atomicidad (Rollback Automático).
 * Si una pata de la operación falla, deshace la otra inmediatamente.
 */
public class CrossTradeExecutor {

    private final ExchangeConnector connector;

    // Para evitar disparar ordenes reales mientras probamos lógica
    // Cambiar a FALSE cuando tengas los $300 del board
    private boolean dryRun = true;

    public CrossTradeExecutor() {
        this.connector = new ExchangeConnector(); // Usa el conector maestro que ya arreglamos
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Ejecuta la maniobra "Pinza" y maneja fallos parciales (Rollback).
     */
    public void executeCrossTrade(String buyExchange, String sellExchange, String pair, double buyPrice, double sellPrice) {
        if (dryRun) {
            BotLogger.info("[DRY-RUN] Simulación de ejecución Cross-Exchange exitosa.");
            return;
        }

        BotLogger.info(String.format("⚡ EJECUTANDO CROSS: Compra %s | Venta %s", buyExchange, sellExchange));

        // 1. Cálculo de Cantidad (Hardcodeado a ~15 USD para MVP o logica dinámica)
        // Ojo: Usar un valor seguro para pruebas.
        double tradeAmountUSDT = 15.0;
        double quantity = tradeAmountUSDT / buyPrice;

        // Ajuste de precisión (ej. 5 decimales para BTC)
        final double qty = Math.floor(quantity * 100000) / 100000.0;

        // 2. DISPARO SIMULTÁNEO (ASYNC)
        // Usamos supplyAsync para obtener el OrderID como retorno

        CompletableFuture<String> buyTask = CompletableFuture.supplyAsync(() ->
                connector.placeOrder(buyExchange, pair, "BUY", "MARKET", qty, 0)
        );

        CompletableFuture<String> sellTask = CompletableFuture.supplyAsync(() ->
                connector.placeOrder(sellExchange, pair, "SELL", "MARKET", qty, 0)
        );

        // 3. ESPERAR RESULTADOS (JOIN)
        String buyOrderId = null;
        String sellOrderId = null;

        try {
            // Unimos los hilos (esto espera a que ambos terminen)
            CompletableFuture.allOf(buyTask, sellTask).join();

            buyOrderId = buyTask.get();  // Será null si falló
            sellOrderId = sellTask.get(); // Será null si falló

        } catch (Exception e) {
            BotLogger.error("🔥 Error crítico esperando confirmación de órdenes: " + e.getMessage());
        }

        // 4. ANÁLISIS DE ATOMICIDAD (¿Qué pasó?)

        if (buyOrderId != null && sellOrderId != null) {
            // ESCENARIO IDEAL: AMBAS EXITOSAS
            BotLogger.info("✅✅ CROSS TRADE PERFECTO. IDs: " + buyOrderId + " / " + sellOrderId);
            BotLogger.sendTelegram("✅ WIN! Arbitraje completado exitosamente.");

        } else if (buyOrderId == null && sellOrderId == null) {
            // FALLO TOTAL (Ninguna entró)
            BotLogger.error("❌❌ Fallo Total. Ninguna orden entró. Capital seguro.");

        } else {
            // 🚨 ESCENARIO DE PELIGRO: EJECUCIÓN PARCIAL (Limp Leg)
            handlePartialFailure(buyExchange, buyOrderId, sellExchange, sellOrderId, pair, qty);
        }
    }

    /**
     * Lógica de Reversión (Rollback) para salvar el capital.
     */
    private void handlePartialFailure(String buyEx, String buyId, String sellEx, String sellId, String pair, double qty) {
        BotLogger.error("🚨 EJECUCIÓN PARCIAL DETECTADA. INICIANDO PROTOCOLO DE EMERGENCIA.");
        BotLogger.sendTelegram("🚨 EJECUCIÓN PARCIAL! Intentando Rollback...");

        // CASO A: Compramos, pero no pudimos vender (Tenemos el activo "caliente")
        if (buyId != null && sellId == null) {
            BotLogger.warn("⚠️ Compramos en " + buyEx + " pero falló la venta en " + sellEx);
            BotLogger.warn("🔄 ROLLBACK: Vendiendo inmediatamente en " + buyEx + " para recuperar USDT.");

            // Acción: Vender de vuelta en el exchange donde compramos (Asumiendo pérdida de spread)
            String rollbackId = connector.placeOrder(buyEx, pair, "SELL", "MARKET", qty, 0);

            if (rollbackId != null) {
                BotLogger.info("✅ ROLLBACK EXITOSO: Posición cerrada en " + buyEx);
            } else {
                BotLogger.error("💀 FATAL: Falló el Rollback. Asistencia humana requerida en " + buyEx);
                BotLogger.sendTelegram("💀 FATAL: Quedamos atrapados en " + buyEx + ". Revisar Manualmente!");
            }
        }

        // CASO B: Vendimos (Short/Spot), pero no pudimos comprar (Faltante de activo)
        // Nota: En Spot normal esto es raro (no puedes vender lo que no tienes),
        // pero si tenías inventario viejo, podrías haberlo vendido sin poder reponerlo.
        else if (buyId == null && sellId != null) {
            BotLogger.warn("⚠️ Vendimos en " + sellEx + " pero falló la compra en " + buyEx);
            BotLogger.warn("🔄 ROLLBACK: Re-comprando inmediatamente en " + sellEx);

            String rollbackId = connector.placeOrder(sellEx, pair, "BUY", "MARKET", qty, 0);

            if (rollbackId != null) {
                BotLogger.info("✅ ROLLBACK EXITOSO: Inventario repuesto en " + sellEx);
            } else {
                BotLogger.error("💀 FATAL: Falló el Rollback de recompra en " + sellEx);
            }
        }
    }
}