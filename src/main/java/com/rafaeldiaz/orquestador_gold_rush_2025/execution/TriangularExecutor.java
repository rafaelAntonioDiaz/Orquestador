package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

/**
 * 📐 TRIANGULAR EXECUTOR (PURE MUSCLE - JAVA 25)
 * Responsabilidad: Ejecución atómica secuencial.
 * Eliminado: Lógica de escaneo, formateo de logs en ruta crítica.
 */
public class TriangularExecutor {

    private final ExchangeConnector connector;
    private final String exchangeName;
    private boolean dryRun = true;

    // Buffers de seguridad (Constants en memoria para evitar accesos a config en hot-path)
    private static final double BUFFER_ENTRY = 0.995; // 0.5% margen en entrada
    private static final double BUFFER_EXIT = 0.999;  // 0.1% margen en salida (rounding errors)

    public TriangularExecutor(ExchangeConnector connector, String exchangeName) {
        this.connector = connector;
        this.exchangeName = exchangeName;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Ejecuta la triangulación VALIDADA.
     * @param p1 Pair String (ej: "BTCUSDT") - Pre-construido por el Scanner
     * @param p2 Pair String (ej: "BTCETH")  - Pre-construido
     * @param p3 Pair String (ej: "ETHUSDT") - Pre-construido
     * @param limitPrice1 Precio detectado por el scanner para calcular cantidad
     */
    public void executeSequence(String asset, String bridge, String p1, String p2, String p3,
                                double capitalUsdt, double limitPrice1) {

        if (dryRun) {
            BotLogger.info("[DRY-RUN] Triangular ejecutada: " + asset + "-" + bridge);
            return;
        }

        // --- PASO 1: ENTRY (USDT -> ASSET) ---
        // Cálculo puro (nanosegundos)
        double quantity1 = (capitalUsdt / limitPrice1) * BUFFER_ENTRY;

        // Fuego 1
        OrderResult r1 = connector.placeOrder(exchangeName, p1, "BUY", "MARKET", quantity1, 0);

        if (!r1.isFilled()) {
            BotLogger.warn("⚠️ Triangular abortada en P1: " + asset);
            return;
        }

        double acquiredAsset = r1.executedQty();

        // --- PASO 2: BRIDGE (ASSET -> BRIDGE) ---
        // Fuego 2
        OrderResult r2 = connector.placeOrder(exchangeName, p2, "SELL", "MARKET", acquiredAsset, 0);

        if (!r2.isFilled()) {
            // CRÍTICO: Fallo en mitad de la operación
            handleEmergencyExit(asset, p1, acquiredAsset);
            return;
        }

        double acquiredBridge = r2.executedValue();

        // Safety check (si la API es lenta devolviendo value, consultamos balance)
        if (acquiredBridge <= 0.0000001) {
            acquiredBridge = connector.fetchBalance(exchangeName, bridge);
        }

        // --- PASO 3: EXIT (BRIDGE -> USDT) ---
        // Fuego 3
        double bridgeToSell = acquiredBridge * BUFFER_EXIT;
        OrderResult r3 = connector.placeOrder(exchangeName, p3, "SELL", "MARKET", bridgeToSell, 0);

        if (r3.isFilled()) {
            double finalUsdt = r3.executedValue();
            double profit = finalUsdt - capitalUsdt;
            // Log asíncrono o simplificado
            BotLogger.logTrade("TRIANGULAR_" + asset + "_" + bridge, "WIN", 0, profit);
        } else {
            // Intento final desesperado ("Sweep")
            double realBal = connector.fetchBalance(exchangeName, bridge);
            connector.placeOrder(exchangeName, p3, "SELL", "MARKET", realBal, 0);
        }
    }

    private void handleEmergencyExit(String asset, String pairUsdt, double qty) {
        BotLogger.error("🚑 EMERGENCY EXIT: Vendiendo " + asset + " a USDT");
        connector.placeOrder(exchangeName, pairUsdt, "SELL", "MARKET", qty, 0);
    }
}