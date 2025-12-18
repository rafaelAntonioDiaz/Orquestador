package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Responsable de ejecutar la secuencia triangular atómica.
 * Task 2.2.2 del Backlog.
 */
public class TradeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TradeExecutor.class);
    private final ExchangeConnector connector;

    // Modo seguridad: Si es true, simula las llamadas API pero no gasta dinero.
    private boolean dryRun = true;

    public TradeExecutor(ExchangeConnector connector) {
        this.connector = connector;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Ejecuta el ciclo triangular: USDT -> A -> B -> USDT
     * @param coinA Moneda intermedia 1 (ej. BTC)
     * @param coinB Moneda intermedia 2 (ej. SOL)
     * @param amountUSDT Cantidad inicial en USDT
     */
// ... dentro de TradeExecutor ...

    /**
     * Ejecuta el ciclo triangular con VERIFICACIÓN MID-TRADE (Task 2.2.3).
     */
    public void executeTriangular(String coinA, String coinB, double amountUSDT) {
        String exchange = "bybit_sub1";
        logger.info("⚡ INICIANDO EJECUCIÓN TRIANGULAR: USDT -> {} -> {} -> USDT", coinA, coinB);

        try {
            // 0. Pre-check Saldo
            double currentBalance = dryRun ? 5000.0 : connector.fetchBalance(exchange);
            if (currentBalance < amountUSDT * 1.05) {
                logger.error("❌ Saldo insuficiente. Req: {}, Disp: {}", amountUSDT, currentBalance);
                return;
            }

            // --- PATA 1: USDT -> A ---
            String pair1 = coinA + "USDT";
            double qty1 = amountUSDT; // En real: Calcular Qty exacta precio
            executeLeg(exchange, pair1, "Buy", "Market", qty1, 0.0);

            // =================================================================
            // 🛑 TASK 2.2.3: MID-TRADE SAFETY CHECK
            // Antes de meternos en la Pata 2, verificamos si sigue siendo rentable.
            // =================================================================
            if (!dryRun) { // En DryRun no chequeamos porque no hay precios reales cambiando
                boolean stillProfitable = checkProfitability(exchange, coinA, coinB);
                if (!stillProfitable) {
                    logger.warn("🛑 ABORTANDO: El mercado se movió en contra tras Pata 1. Vendiendo {} a USDT.", coinA);
                    // Pánico: Revertir Pata 1 (Vender A de vuelta a USDT) para minimizar daños
                    executeLeg(exchange, pair1, "Sell", "Market", qty1, 0.0);
                    return; // ¡Salimos del ciclo!
                }
            }

            // --- PATA 2: A -> B ---
            String pair2 = coinB + coinA;
            double qty2 = 0.0; // TODO: Usar balance real de A obtenido en Pata 1
            executeLeg(exchange, pair2, "Buy", "Market", qty2, 0.0);

            // --- PATA 3: B -> USDT ---
            String pair3 = coinB + "USDT";
            double qty3 = 0.0; // TODO: Usar balance real de B obtenido en Pata 2
            executeLeg(exchange, pair3, "Sell", "Market", qty3, 0.0);

            logger.info("✅ CICLO COMPLETADO EXITOSAMENTE");

        } catch (IOException e) {
            logger.error("🚨 CRITICAL: Error ejecutando trade: {}", e.getMessage());
        }
    }

    /**
     * Verifica si el ciclo sigue dando > 0% de ganancia basándose en precios frescos.
     */
    private boolean checkProfitability(String exchange, String coinA, String coinB) {
        try {
            // Recalculamos rápido con precios REST frescos (más seguro que caché para ejecución)
            double priceA_USDT = connector.fetchPrice(exchange, coinA + "USDT");
            double priceB_CoinA = connector.fetchPrice(exchange, coinB + coinA); // Asumiendo par B/A
            double priceB_USDT = connector.fetchPrice(exchange, coinB + "USDT");

            // Simulación rápida de 1 unidad
            double start = 1.0;
            double amtA = start / priceA_USDT;
            double amtB = amtA / priceB_CoinA;
            double end = amtB * priceB_USDT;

            double profit = (end - start) / start;

            // Si la ganancia bajó de 0.1% (o se volvió negativa), abortamos.
            // Descontando fees aproximados (0.1% * 2 patas restantes = 0.2%)
            if (profit < 0.002) {
                logger.warn("📉 Spread colapsó a {}%. Riesgoso continuar.", String.format("%.4f", profit*100));
                return false;
            }
            return true;

        } catch (Exception e) {
            logger.error("Error verificando profit mid-trade: {}", e.getMessage());
            return false; // Ante la duda, abortar
        }
    }
    private void executeLeg(String exchange, String pair, String side, String type, double qty, double price) throws IOException {
        if (dryRun) {
            logger.info("[DRY-RUN] Orden simulada: {} {} {} @ {} (Qty: {})", side, pair, type, price, qty);
        } else {
            String response = connector.placeOrder(exchange, pair, side, type, qty, price);
            logger.info("Orden enviada {}: {}", pair, response);
            // Aquí deberíamos esperar confirmación (FOK) antes de seguir
        }
    }
}