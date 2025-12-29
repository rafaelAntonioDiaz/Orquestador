package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

/**
 * 📐 TRIANGULAR EXECUTOR (STATELESS & ATOMIC)
 * Responsabilidad: Ejecución ciega. Recibe Strings ya construidos.
 * No concatena, no piensa. Solo dispara.
 */
public class TriangularExecutor {

    private final ExchangeConnector connector;
    private boolean dryRun = true;

    // Buffers de seguridad (Hardcoded para velocidad)
    private static final double BUFFER_ENTRY = 0.995;
    private static final double BUFFER_EXIT = 0.999;

    public TriangularExecutor(ExchangeConnector connector) {
        this.connector = connector;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * ⚡ EXECUTE SEQUENCE (Zero-Allocation)
     * Recibe los pares YA construidos por el Scanner para evitar latencia de String Builder.
     */
    public void executeSequence(String exchangeName, String asset, String bridge,
                                String p1, String p2, String p3,
                                double capitalUsdt, double limitPrice1) {

        // TODO: Comentar este log en producción para ganar ~50 micros
        // if (dryRun) BotLogger.info("[DRY] Triangular trigger: " + asset + "-" + bridge);

        if (dryRun) return;

        // --- PASO 1: ENTRY (USDT -> ASSET) ---
        double quantity1 = (capitalUsdt / limitPrice1) * BUFFER_ENTRY;

        // Fuego 1
        OrderResult r1 = connector.placeOrder(exchangeName, p1, "BUY", "MARKET", quantity1, 0);

        if (!r1.isFilled()) {
            BotLogger.warn("⚠️ Triangular abortada en P1 (" + exchangeName + ")");
            return;
        }

        double acquiredAsset = r1.executedQty();

        // --- PASO 2: BRIDGE (ASSET -> BRIDGE) ---
        // Fuego 2
        OrderResult r2 = connector.placeOrder(exchangeName, p2, "SELL", "MARKET", acquiredAsset, 0);

        if (!r2.isFilled()) {
            handleEmergencyExit(exchangeName, asset, p1, acquiredAsset);
            return;
        }

        double acquiredBridge = r2.executedValue();

        // Safety check (costoso, solo si falla la API en devolver value)
        if (acquiredBridge <= 0.0000001) {
            acquiredBridge = connector.fetchBalance(exchangeName, bridge);
        }

        // --- PASO 3: EXIT (BRIDGE -> USDT) ---
        // Fuego 3
        double bridgeToSell = acquiredBridge * BUFFER_EXIT;
        OrderResult r3 = connector.placeOrder(exchangeName, p3, "SELL", "MARKET", bridgeToSell, 0);

        if (r3.isFilled()) {
            double profit = r3.executedValue() - capitalUsdt;
            // Log asíncrono post-mortem
            BotLogger.logTrade("TRIANGULAR_" + asset + "_" + bridge, "WIN", 0, profit);
        } else {
            // Sweep final de emergencia
            double realBal = connector.fetchBalance(exchangeName, bridge);
            connector.placeOrder(exchangeName, p3, "SELL", "MARKET", realBal, 0);
        }
    }

    private void handleEmergencyExit(String exchangeName, String asset, String pairUsdt, double qty) {
        BotLogger.error("🚑 EMERGENCY EXIT: Vendiendo " + asset + " a USDT en " + exchangeName);
        connector.placeOrder(exchangeName, pairUsdt, "SELL", "MARKET", qty, 0);
    }
}