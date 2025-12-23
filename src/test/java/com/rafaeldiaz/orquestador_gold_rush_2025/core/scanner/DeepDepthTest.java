package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import org.junit.jupiter.api.Test;
import java.util.List;

/**
 * 🩻 TEST DE RAYOS X (Order Book Analysis)
 * Verifica que el bot puede ver la profundidad del mercado y calcular el slippage real.
 */
public class DeepDepthTest {

    @Test
    void xrayVisionTest() {
        ExchangeConnector connector = new ExchangeConnector();
        // Usamos PEPE porque su volatilidad hace evidente el slippage
        String pair = "PEPEUSDT";
        List<String> exchanges = List.of("binance", "bybit", "mexc", "kucoin");

        System.out.println("\n🩻 INICIANDO ESCANEO DE PROFUNDIDAD (X-RAY) PARA " + pair);
        System.out.println("==========================================================================================");
        System.out.printf("%-10s | %-16s | %-16s | %-12s | %-15s%n", "EXCHANGE", "PRECIO BASE ($10)", "PRECIO WHALE ($2k)", "SLIPPAGE %", "ESTADO");
        System.out.println("------------------------------------------------------------------------------------------");

        for (String ex : exchanges) {
            try {
                // 1. Descargar el Libro de Órdenes (50 niveles para ver profundidad real)
                ExchangeConnector.OrderBook book = connector.fetchOrderBook(ex, pair, 50);

                if (book.asks() == null || book.asks().isEmpty()) {
                    System.out.printf("%-10s | %-16s | %-16s | %-12s | %-15s%n", ex, "❌ CIEGO", "---", "---", "🔴 ERROR API");
                    continue;
                }

                // 2. Simular compras de diferentes tamaños
                // Calculamos cuántos PEPE son $10 USD y $2000 USD basados en el primer precio
                double priceRef = book.asks().get(0)[0];
                if (priceRef <= 0) continue;

                double amountSmall = 10.0 / priceRef;   // Cantidad de tokens para $10
                double amountWhale = 2000.0 / priceRef; // Cantidad de tokens para $2,000

                // 3. Calculamos el precio promedio ponderado (Weighted Average Price)
                double priceSmall = connector.calculateWeightedPrice(book, "BUY", amountSmall);
                double priceWhale = connector.calculateWeightedPrice(book, "BUY", amountWhale);

                // 4. Calcular Slippage (Impacto en el precio)
                String slipStr;
                String stateStr;

                if (priceWhale > 0) {
                    double slippage = (priceWhale - priceSmall) / priceSmall * 100.0;
                    slipStr = String.format("%.4f%%", slippage);
                    // Si el slippage es bajo (< 0.2%), hay mucha liquidez. Si es alto, cuidado.
                    stateStr = (slippage < 0.2) ? "🟢 LÍQUIDO" : (slippage < 0.5 ? "⚠️ NORMAL" : "⛔ PELIGROSO");
                } else {
                    priceWhale = 0.0;
                    slipStr = "INF";
                    stateStr = "⛔ SIN LIQUIDEZ"; // No alcanzan las órdenes del libro para llenar $2k
                }

                // Formateo visual (8 decimales para PEPE)
                System.out.printf("%-10s | $%.9f     | $%.9f     | %-12s | %s%n",
                        ex, priceSmall, priceWhale, slipStr, stateStr);

            } catch (Exception e) {
                System.out.printf("%-10s | %-16s | %-16s | %-12s | %-15s%n", ex, "ERR", "---", "---", "🔥 " + e.getMessage());
            }
        }
        System.out.println("==========================================================================================");
        System.out.println("🏁 Análisis de Profundidad Finalizado.");
    }
}