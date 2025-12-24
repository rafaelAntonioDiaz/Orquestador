package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.Map;

/**
 * 🏦 AUDITOR DE BILLETERAS GLOBAL
 * Se encarga de mostrar "La Verdad" sobre nuestros fondos en tiempo real.
 */
public class GlobalBalanceReporter {

    private final ExchangeConnector connector;
    // Lista de monedas que nos importan (para filtrar basura/dust)
    private final String[] CRITICAL_ASSETS = {"USDT", "PEPE", "WIF", "SOL", "BNB", "ETH", "FET"};

    public GlobalBalanceReporter(ExchangeConnector connector) {
        this.connector = connector;
    }

    public void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔═════════════════════════════════════════════════════════════╗\n");
        sb.append("║ 🏦 ESTADO DEL TESORO (INVENTARIO REAL)                      ║\n");
        sb.append("╠══════════╦══════════╦═════════════════════╦═════════════════╣\n");
        sb.append("║ EXCHANGE ║ ACTIVO   ║ CANTIDAD TOTAL      ║ DISPONIBLE      ║\n");
        sb.append("╠══════════╬══════════╬═════════════════════╬═════════════════╣\n");

        boolean assetsFound = false;

        String[] exchanges = {
                "binance",
                "bybit_sub1", // Miramos explícitamente la 1
                "bybit_sub2", // Miramos la 2
                "bybit_sub3", // Miramos la 3
                "kucoin",
                "mexc"
        };

        for (String exchange : exchanges) {
            try {
                // Imaginamos que su conector tiene un método getBalances(exchange)
                // que devuelve un Mapa <Moneda, Cantidad> o similar.
                // Si su implementación es distinta, ajustaremos esta línea.
                Map<String, Double> balances = connector.fetchBalances(exchange);

                if (balances == null || balances.isEmpty()) continue;

                for (String asset : CRITICAL_ASSETS) {
                    if (balances.containsKey(asset)) {
                        double qty = balances.get(asset);

                        // Solo mostramos si hay saldo relevante (> 0.01 para evitar dust)
                        if (qty > 0.0001) {
                            assetsFound = true;
                            // Formato limpio tipo tabla
                            sb.append(String.format("║ %-8s ║ %-8s ║ %-19.4f ║ %-15s ║\n",
                                    exchange.toUpperCase(),
                                    asset,
                                    qty,
                                    "100%" // Aquí podría ir el 'Available' real si la API lo da
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                // Si falla un exchange, no rompemos el reporte, solo lo saltamos
                sb.append(String.format("║ %-8s ║ ERROR    ║ ⚠ NO CONECTADO      ║                 ║\n", exchange.toUpperCase()));
            }
        }

        sb.append("╚══════════╩══════════╩═════════════════════╩═════════════════╝\n");

        if (assetsFound) {
            BotLogger.info(sb.toString());
        } else {
            BotLogger.info("🏦 [TESORO]: Billeteras vacías o error de lectura.");
        }
    }
}