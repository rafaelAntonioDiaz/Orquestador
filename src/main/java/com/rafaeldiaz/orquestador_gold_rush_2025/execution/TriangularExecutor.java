package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

/**
 * 📐 TRIANGULAR EXECUTOR (Centrifugadora V5.2 - Precisión Atómica)
 * Estrategia Intra-Exchange: USDT -> ALT -> BRIDGE -> USDT
 */
public class TriangularExecutor {

    private final ExchangeConnector connector;
    private final String exchangeName;
    private boolean dryRun = true;
    private final ExecutionCoordinator coordinator;
    // Margen mínimo (0.40% cubre fees 0.1%x3 y deja ganancia)
    private static final double MIN_TRIANGULAR_PROFIT_PCT = 0.40;

    public TriangularExecutor(ExchangeConnector connector,
                              String exchangeName, ExecutionCoordinator coordinator) {
        this.connector = connector;
        this.exchangeName = exchangeName;
        this.coordinator = coordinator; // Inyección
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Busca y Ejecuta oportunidad triangular usando un PUENTE ESPECÍFICO.
     */
    public void scanAndExecute(String asset, String bridge, double capitalUsdt) {
        // 1. Definir los pares
        String pair1 = asset + "USDT";
        String pair2 = asset + bridge;
        String pair3 = bridge + "USDT";

        // 2. Precios en tiempo real (Simulación Taker)
        double price1_Ask = connector.fetchPrice(exchangeName, pair1);
        double price2_Bid = connector.fetchPrice(exchangeName, pair2);
        double price3_Bid = connector.fetchPrice(exchangeName, pair3);

        if (price1_Ask == 0 || price2_Bid == 0 || price3_Bid == 0) return;

        // 3. Simulación Matemática
        double step1_AltQty = capitalUsdt / price1_Ask;
        double step2_BridgeQty = step1_AltQty * price2_Bid;
        double step3_FinalUsdt = step2_BridgeQty * price3_Bid;

        // 4. Rentabilidad
        double profitUsdt = step3_FinalUsdt - capitalUsdt;
        double profitPct = (profitUsdt / capitalUsdt) * 100.0;
        double netProfitPct = profitPct - 0.30; // Fees est.

        if (netProfitPct > MIN_TRIANGULAR_PROFIT_PCT) {
            BotLogger.info(String.format("📐 OPORTUNIDAD [%s] VÍA [%s] !!!", asset, bridge));
            BotLogger.info(String.format("   Ruta: USDT -> %s -> %s -> USDT", asset, bridge));
            BotLogger.info(String.format("   Gap Neto Est: %.2f%% (Capital: $%.2f)", netProfitPct, capitalUsdt));

            if (!dryRun) {
                // 🚦 SEMÁFORO: Pedimos permiso exclusivo para esta cuenta
                if (coordinator.tryAcquireLock(exchangeName)) {
                    try {
                        executeTriangle(asset, bridge, pair1, pair2, pair3, capitalUsdt, price1_Ask);
                    } finally {
                        coordinator.releaseLock(exchangeName); // SIEMPRE liberar
                    }
                } else {
                    BotLogger.warn("🚦 TRIANGULAR SKIP: Cuenta " + exchangeName + " ocupada por Espacial.");
                }
            }
        }
    }

    /**
     * Ejecuta las 3 órdenes secuenciales.
     */
    private void executeTriangle(String asset, String bridge, String p1, String p2, String p3, double initialUsdt, double price1) {
        BotLogger.warn("🚀 EJECUTANDO TRIÁNGULO: " + asset + " via " + bridge);

        // --- PASO 1: USDT -> ASSET ---
        double qty1 = (initialUsdt / price1) * 0.99; // Margen de error por redondeo
        OrderResult r1 = connector.placeOrder(exchangeName, p1, "BUY", "MARKET", qty1, 0);

        if (!r1.isFilled()) {
            BotLogger.error("❌ FALLO PASO 1. Abortando.");
            return;
        }
        double acquiredAsset = r1.executedQty();
        BotLogger.info("✅ PASO 1: Comprado " + acquiredAsset + " " + asset);

        // --- PASO 2: ASSET -> BRIDGE ---
        // Vendemos el Asset para recibir Bridge
        OrderResult r2 = connector.placeOrder(exchangeName, p2, "SELL", "MARKET", acquiredAsset, 0);

        if (!r2.isFilled()) {
            BotLogger.error("💀 ATASCADO EN PASO 2. Tienes " + asset);
            return;
        }

        // ✅ CORRECCIÓN SOLICITADA: EXTRACCIÓN PRECISA DEL QUOTE (BRIDGE)
        // En una venta (Sell ALT/SOL), 'executedValue' es el total de SOL recibido.
        // Esto es mucho más preciso que multiplicar qty * price.
        double acquiredBridgeGross = r2.executedValue();

        // Safety: Restamos un pelito (0.1%) por si el exchange cobra el fee del activo recibido
        // y para evitar errores de "Insufficient Balance" en el paso 3.
        double acquiredBridgeNet = acquiredBridgeGross * 0.999;

        BotLogger.info("✅ PASO 2: Recibido " + acquiredBridgeNet + " " + bridge + " (Exacto API)");

        // --- PASO 3: BRIDGE -> USDT ---
        // ⚡ OPTIMIZACIÓN DE VELOCIDAD:
        // Usamos el dato de memoria (acquiredBridgeNet) en lugar de llamar a fetchBalance().
        // Ahorramos ~300ms de latencia de red.
        OrderResult r3 = connector.placeOrder(exchangeName, p3, "SELL", "MARKET", acquiredBridgeNet, 0);

        if (r3.isFilled()) {
            double finalUsdt = r3.executedValue();
            BotLogger.info(String.format("🏆 CICLO COMPLETADO. Final: $%.2f", finalUsdt));
        } else {
            // Fallback de emergencia: Si falló por saldo, intentamos leer saldo real y reintentar (Lento pero seguro)
            BotLogger.warn("⚠️ Fallo Paso 3 Rápido. Intentando con lectura de saldo real...");
            double realBalance = connector.fetchBalance(exchangeName, bridge);
            connector.placeOrder(exchangeName, p3, "SELL", "MARKET", realBalance, 0);
        }
    }
}