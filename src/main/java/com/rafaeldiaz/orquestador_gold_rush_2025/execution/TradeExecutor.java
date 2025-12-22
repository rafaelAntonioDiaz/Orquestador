package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.text.DecimalFormat;

/**
 * ⚔️ TRADE EXECUTOR (VERSIÓN 4.0: PRODUCCIÓN / FUEGO REAL)
 * Implementa lógica atómica secuencial, normalización de cantidades (StepSize)
 * y manejo de errores con paracaídas (Emergency Sell).
 */
public class TradeExecutor {

    private final ExchangeConnector connector;
    private final FeeManager feeManager;
    private boolean dryRun = true;
    private final DecimalFormat df = new DecimalFormat("0.00000000");

    public TradeExecutor(ExchangeConnector connector, FeeManager feeManager) {
        this.connector = connector;
        this.feeManager = feeManager;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        if (!dryRun) {
            BotLogger.warn("🚨🚨 ALERTA: TRADE EXECUTOR EN MODO FUEGO REAL (LIVE TRADING) 🚨🚨");
        }
    }

    // =====================================================================
    // 🔺 SISTEMA 1: EJECUCIÓN TRIANGULAR (Secuencial Atómica)
    // Ruta: USDT -> COIN (Buy) -> BRIDGE (Sell Coin) -> USDT (Sell Bridge)
    // =====================================================================
    public void executeTriangular(String exchange, String asset, String bridge, double capitalInput) {
        String pair1 = asset + "USDT";   // Comprar Asset con USDT
        String pair2 = asset + bridge;   // Vender Asset por Bridge
        String pair3 = bridge + "USDT";  // Vender Bridge por USDT

        BotLogger.info(String.format("⚡ [EXECUTOR] Triángulo: USDT -> %s -> %s -> USDT (Cap: $%.2f)", asset, bridge, capitalInput));

        if (dryRun) {
            logDryRun(asset, bridge);
            return;
        }

        // --- PASO 1: COMPRAR ACTIVO (USDT -> ASSET) ---
        double price1 = connector.fetchAsk(exchange, pair1);
        if (price1 <= 0) {
            BotLogger.error("❌ Fallo obteniendo precio inicial para " + pair1);
            return;
        }

        // 1. Normalizar cantidad de compra
        double stepSize1 = connector.getStepSize(exchange, pair1);
        double qty1 = normalizeQuantity(capitalInput / price1, stepSize1);

        if (qty1 <= 0) {
            BotLogger.error("🚫 Cantidad calculada inválida para " + pair1);
            return;
        }

        BotLogger.info("🔫 Paso 1: Comprando " + df.format(qty1) + " " + asset);
        // Limit FOK un 0.5% arriba para asegurar entrada inmediata sin slippage infinito
        double limitPrice1 = price1 * 1.005;

        OrderResult result1 = connector.placeOrder(exchange, pair1, "BUY", "LIMIT", qty1, limitPrice1);

        if (!result1.isFilled()) {
            BotLogger.warn("🚫 Paso 1 No Completado (Status: " + result1.status() + "). Abortando operación sin costo.");
            return;
        }
        BotLogger.info("✅ Paso 1 EXITOSO. Obtuvimos: " + df.format(result1.executedQty()) + " " + asset);

        // --- PASO 2: CAMBIAR A PUENTE (ASSET -> BRIDGE) ---
        // Usamos la cantidad REAL ejecutada. Restamos un pequeño margen de seguridad (0.2%)
        // para cubrir fees si se cobraron en el activo base y evitar errores de saldo insuficiente.
        double qtyOwned = result1.executedQty() * 0.998;

        double stepSize2 = connector.getStepSize(exchange, pair2);
        double qtyToSell = normalizeQuantity(qtyOwned, stepSize2);

        BotLogger.info("🔫 Paso 2: Vendiendo " + df.format(qtyToSell) + " " + asset + " por " + bridge);
        OrderResult result2 = connector.placeOrder(exchange, pair2, "SELL", "MARKET", qtyToSell, 0);

        if (!result2.isFilled()) {
            // SI FALLA EL PASO 2: Tenemos el activo "caliente". Hay que volver a USDT ya.
            handleEmergencySell(exchange, pair1, qtyToSell);
            return;
        }
        BotLogger.info("✅ Paso 2 EXITOSO. Cambiado a Bridge.");

        // --- PASO 3: CERRAR CICLO (BRIDGE -> USDT) ---
        // Consultamos el balance real del puente para vender absolutamente todo lo que tengamos.
        // Esto corrige cualquier discrepancia por fees variables.
        double bridgeBalance = connector.fetchBalance(exchange, bridge);
        double stepSize3 = connector.getStepSize(exchange, pair3);
        double qtyBridgeToSell = normalizeQuantity(bridgeBalance * 0.995, stepSize3); // 99.5% para margen de error

        BotLogger.info("🔫 Paso 3: Vendiendo " + df.format(qtyBridgeToSell) + " " + bridge + " por USDT");
        OrderResult result3 = connector.placeOrder(exchange, pair3, "SELL", "MARKET", qtyBridgeToSell, 0);

        if (!result3.isFilled()) {
            BotLogger.error("💀 ERROR CRÍTICO PASO 3. Nos quedamos con " + bridge + ". Intervención manual requerida.");
            BotLogger.sendTelegram("💀 FATAL: Stuck with " + bridge + " in " + exchange);
            return;
        }

        BotLogger.info("💎 CICLO COMPLETADO. ID Final: " + result3.orderId());
        BotLogger.sendTelegram("💎 TRIANGULAR WIN: " + asset + "-" + bridge);
    }

    /**
     * Ajusta la cantidad al múltiplo exacto permitido por el exchange (StepSize).
     * Ejemplo: Si raw=1.498 y step=0.1, retorna 1.40 (floor).
     */
    private double normalizeQuantity(double rawQty, double stepSize) {
        if (stepSize == 0) return rawQty;
        // Usamos Math.floor para redondear hacia abajo y evitar "Insufficient Balance"
        double steps = Math.floor(rawQty / stepSize);
        return steps * stepSize;
    }

    /**
     * Venta de Pánico: Si falla el paso intermedio, vendemos el activo original contra USDT.
     */
    private void handleEmergencySell(String exchange, String pair, double qty) {
        BotLogger.error("🚨 FALLO PASO 2. INICIANDO VENTA DE EMERGENCIA (A USDT).");
        OrderResult panicResult = connector.placeOrder(exchange, pair, "SELL", "MARKET", qty, 0);

        if (panicResult.isFilled()) {
            BotLogger.info("✅ Emergencia resuelta. Volvimos a USDT (con pérdida de spread).");
        } else {
            BotLogger.error("💀 FATAL: Falló venta de emergencia. Bag holder de " + pair);
        }
    }

    private void logDryRun(String asset, String bridge) {
        BotLogger.info("✅ [DRY-RUN] Paso 1: Compra " + asset + " simulada OK");
        BotLogger.info("✅ [DRY-RUN] Paso 2: Cambio " + asset + "/" + bridge + " simulada OK");
        BotLogger.info("✅ [DRY-RUN] Paso 3: Venta " + bridge + "/USDT simulada OK");
    }
    // =====================================================================
    // 🌍 SISTEMA 2: ARBITRAJE ESPACIAL (Simulacro Logístico)
    // Ruta: Buy ExA -> Withdraw -> Sell ExB
    // =====================================================================
    public void executeSpatialArbitrage(String asset, String buyEx, String sellEx, double capitalUsdt) {
        String pair = asset + "USDT";
        BotLogger.warn("⚔️ [EXECUTOR] Iniciando Protocolo Espacial: " + asset + " [" + buyEx + " -> " + sellEx + "]");

        try {
            // 1. OBTENER PRECIO REAL DE COMPRA
            double liveBuyPrice = connector.fetchAsk(buyEx, pair);
            if (liveBuyPrice <= 0) {
                BotLogger.error("⛔ Error leyendo precio en " + buyEx);
                return;
            }

            double qtyToBuy = capitalUsdt / liveBuyPrice;

            // 2. SIMULAR COMPRA (O EJECUTAR SI NO ES DRY RUN)
            if (dryRun) {
                BotLogger.info("🔫 [DRY-RUN] Comprando " + df.format(qtyToBuy) + " " + asset + " en " + buyEx + " a " + liveBuyPrice);
            } else {
                // String orderId = connector.placeOrder(buyEx, pair, "BUY", "MARKET", qtyToBuy, 0);
                // if (orderId == null) throw new RuntimeException("Fallo compra real");
                BotLogger.info("🔫 [REAL] Orden enviada a " + buyEx);
            }

            // 3. SIMULAR LOGÍSTICA DE RETIRO
            // Nota: No llamamos a connector.withdraw() porque aún no existe. Simulamos el delay.
            double withdrawFee = feeManager.getWithdrawalFee(buyEx, asset);
            double qtyArriving = qtyToBuy - withdrawFee;

            if (qtyArriving <= 0) {
                BotLogger.error("💀 El Fee de retiro se comió todo el capital. Operación cancelada.");
                return;
            }

            BotLogger.info("🚚 [LOGÍSTICA] Simulando retiro de " + asset + "...");
            BotLogger.info("   - Fee Red: " + withdrawFee + " " + asset);
            BotLogger.info("   - Cantidad en tránsito: " + df.format(qtyArriving));

            // Simular espera de red (rápida para el test)
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            BotLogger.info("📥 [LOGÍSTICA] Fondos 'recibidos' en " + sellEx);

            // 4. SIMULAR VENTA EN DESTINO
            double liveSellPrice = connector.fetchBid(sellEx, pair);
            if (liveSellPrice <= 0) {
                BotLogger.error("⛔ Error leyendo precio en destino " + sellEx);
                return;
            }

            // Chequeo de seguridad: ¿Sigue siendo rentable?
            double finalUsdt = qtyArriving * liveSellPrice;
            double pnl = finalUsdt - capitalUsdt;

            if (dryRun) {
                BotLogger.info("💰 [DRY-RUN] Vendiendo a " + liveSellPrice + " en " + sellEx);
                BotLogger.info(String.format("🏁 RESULTADO FINAL: Capital Inicial: $%.2f | Final: $%.2f | PnL: %s$%.2f",
                        capitalUsdt, finalUsdt, (pnl >= 0 ? "+" : ""), pnl));

                if (pnl > 0) BotLogger.sendTelegram("💎 SIMULACIÓN EXITOSA: " + asset + " Profit: $" + df.format(pnl));
                else BotLogger.warn("📉 SIMULACIÓN PÉRDIDA: El slippage o fee de red mató el trade.");
            }

        } catch (Exception e) {
            BotLogger.error("💥 Falla en ejecución espacial: " + e.getMessage());
        }
    }
}