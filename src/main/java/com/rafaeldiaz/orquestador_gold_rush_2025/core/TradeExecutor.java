package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

/**
 * EJECUTOR DE ARBITRAJE TRIANGULAR (Sistema 1).
 * Responsable de ejecutar la secuencia rápida: USDT -> A -> B -> USDT
 * dentro del mismo exchange (Bybit).
 */
public class TradeExecutor {

    private final ExchangeConnector connector;
    private boolean dryRun = true; // Por defecto en modo seguro (Simulacro)

    // Cuenta por defecto para arbitraje triangular (Bybit Sub1)
    private static final String EXCHANGE = "bybit_sub1";

    public TradeExecutor(ExchangeConnector connector) {
        this.connector = connector;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Ejecuta el ciclo triangular completo.
     * @param coinA Moneda intermedia 1 (ej. BTC)
     * @param coinB Moneda intermedia 2 (ej. SOL)
     * @param amountUSDT Cantidad inicial en Dólares (ej. 20.0)
     */
    public void executeTriangular(String coinA, String coinB, double amountUSDT) {
        BotLogger.info(String.format("⚡ INICIANDO TRIANGULACIÓN: USDT -> %s -> %s -> USDT", coinA, coinB));

        // 1. VALIDACIÓN DE SALDO INICIAL
        // 🔥 AQUÍ ESTABA EL ERROR: Ahora pedimos explícitamente el saldo de "USDT"
        double balanceUSDT = connector.fetchBalance(EXCHANGE, "USDT");

        if (!dryRun) {
            if (balanceUSDT < amountUSDT) {
                BotLogger.error("❌ Saldo insuficiente en USDT. Req: " + amountUSDT + " Disp: " + balanceUSDT);
                return;
            }
        }

        if (dryRun) {
            BotLogger.info("[DRY-RUN] Simulación de ejecución exitosa. No se movieron fondos.");
            return;
        }

        // =====================================================================
        // 🏁 FASE 1: USDT -> COIN A (Comprar A)
        // =====================================================================
        // Par: COIN_A + "USDT" (ej. BTCUSDT)
        String pair1 = coinA + "USDT";
        // En mercado Spot, al comprar, gastamos USDT. Usamos Market para velocidad.
        // Nota: En Bybit V5 Market Buy, 'qty' suele ser el monto en USDT si se configura quoteQty,
        // pero por seguridad calculamos la cantidad de moneda base estimada.
        double price1 = connector.fetchPrice(EXCHANGE, pair1);
        double qtyA = amountUSDT / price1;

        // Ajuste de decimales (Precision) - MVP: 5 decimales
        qtyA = Math.floor(qtyA * 100000) / 100000.0;

        String order1 = connector.placeOrder(EXCHANGE, pair1, "BUY", "MARKET", qtyA, 0);
        if (order1 == null) {
            BotLogger.error("❌ Falló Paso 1 (Buy " + coinA + "). Abortando.");
            return;
        }
        BotLogger.info("✅ Paso 1 Completado: Comprado " + qtyA + " " + coinA);


        // =====================================================================
        // 🏁 FASE 2: COIN A -> COIN B
        // =====================================================================
        // Aquí se complica. Depende del par disponible.
        // Escenario común: Existe pairB_pairA (ej. SOLBTC) -> Base SOL, Quote BTC.
        // Nosotros tenemos BTC (Quote), queremos SOL (Base). => COMPRAMOS SOL.

        String pair2 = coinB + coinA; // Ej. SOLBTC
        // Verificamos si existe precio, si no, intentamos al revés (A/B)
        double price2 = connector.fetchPrice(EXCHANGE, pair2);

        String order2 = null;
        double qtyB = 0.0;

        if (price2 > 0) {
            // Caso Normal: SOLBTC existe. Compramos SOL pagando con BTC.
            // Cuantos SOL compro con mis BTC?
            // qtyBTC disponible ~= qtyA (menos fees).
            // qtySOL = qtyBTC / price2
            double currentQtyA = connector.fetchBalance(EXCHANGE, coinA); // Verificamos saldo real post-fee
            qtyB = currentQtyA / price2;
            qtyB = Math.floor(qtyB * 10000) / 10000.0; // 4 decimales

            order2 = connector.placeOrder(EXCHANGE, pair2, "BUY", "MARKET", qtyB, 0);
        } else {
            // Caso Inverso: Existe BTCSOL? (Raro en CEX, común en DEX).
            // Si el par es al revés, tendríamos que VENDER A para obtener B.
            BotLogger.error("❌ Par intermedio no encontrado o no soportado: " + pair2);
            return; // Abortamos estrategia por seguridad
        }

        if (order2 == null) {
            BotLogger.error("❌ Falló Paso 2 (Swap " + coinA + "->" + coinB + "). Quedamos en " + coinA);
            return;
        }
        BotLogger.info("✅ Paso 2 Completado: Obtenido " + qtyB + " " + coinB);


        // =====================================================================
        // 🏁 FASE 3: COIN B -> USDT (Vender B)
        // =====================================================================
        String pair3 = coinB + "USDT";
        double currentQtyB = connector.fetchBalance(EXCHANGE, coinB);

        // Vender todo lo que tenemos de B
        currentQtyB = Math.floor(currentQtyB * 10000) / 10000.0;

        String order3 = connector.placeOrder(EXCHANGE, pair3, "SELL", "MARKET", currentQtyB, 0);

        if (order3 == null) {
            BotLogger.error("❌ Falló Paso 3 (Sell " + coinB + "). Quedamos en " + coinB);
        } else {
            BotLogger.info("🏆 CICLO TRIANGULAR FINALIZADO CON ÉXITO.");
        }
    }
}