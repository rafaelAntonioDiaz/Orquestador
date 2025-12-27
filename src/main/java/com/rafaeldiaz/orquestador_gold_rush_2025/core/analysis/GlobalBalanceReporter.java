package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 🏦 REPORTERO DE SALDOS (VISIBILIDAD TOTAL - OJO DE SAURON)
 * Sin filtros de polvo, sin cuentas ocultas. Muestra TODO.
 */
public class GlobalBalanceReporter {

    private final ExchangeConnector connector;

    // Formateador preciso para cripto (hasta 8 decimales para ver el polvo)
    private final DecimalFormat dfQty = new DecimalFormat("###,##0.00000000");

    public GlobalBalanceReporter(ExchangeConnector connector) {
        this.connector = connector;
    }

    public void printReport() {
        // Paleta de Colores
        String C = BotLogger.CYAN;
        String G = BotLogger.GREEN;
        String Y = BotLogger.YELLOW;
        String W = BotLogger.WHITE_BOLD;
        String P = BotLogger.PURPLE;
        String R = BotLogger.RESET;

        BotLogger.info("\n" + C + "╔══════════════════╦══════════╦══════════════════════════╦═════════════════╗" + R);
        BotLogger.info(C + "║ 🏦 ESTADO DEL TESORO (INVENTARIO REAL - MODO RAW)                           ║" + R);
        BotLogger.info(C + "╠══════════════════╬══════════╬══════════════════════════╬═════════════════╣" + R);
        BotLogger.info(C + "║ EXCHANGE         ║ ACTIVO   ║ CANTIDAD TOTAL           ║ DISPONIBLE      ║" + R);
        BotLogger.info(C + "╠══════════════════╬══════════╬══════════════════════════╬═════════════════╣" + R);

        // 1. RECOLECCIÓN EXHAUSTIVA DE CUENTAS
        // Combinamos TODAS las listas posibles para no dejar a nadie fuera (MEXC, Kucoin, etc.)
        Set<String> allAccounts = new HashSet<>();
        if (BotConfig.ACTIVE_EXCHANGES != null) allAccounts.addAll(BotConfig.ACTIVE_EXCHANGES);
        if (BotConfig.SPATIAL_ACCOUNTS != null) allAccounts.addAll(BotConfig.SPATIAL_ACCOUNTS);
        if (BotConfig.TRIANGULAR_ACCOUNTS != null) allAccounts.addAll(BotConfig.TRIANGULAR_ACCOUNTS);

        for (String exchange : allAccounts) {
            try {
                // Fetch de saldos (Raw)
                Map<String, Double> balances = connector.fetchBalances(exchange);

                // Si la respuesta es nula o vacía, avisamos
                if (balances == null || balances.isEmpty()) {
                    // Opcional: Avisar si está vacío, pero mejor mantener la tabla limpia
                    continue;
                }

                Map<String, Double> sortedBalances = new TreeMap<>(balances);

                for (Map.Entry<String, Double> entry : sortedBalances.entrySet()) {
                    String asset = entry.getKey();
                    Double qty = entry.getValue();

                    // ⚠️ FILTRO ELIMINADO: Ahora mostramos todo lo que sea mayor a CERO absoluto.
                    // A veces quedan residuos de 0.00000001, queremos verlos para saber que existen.
                    if (qty > 0.00000000) {

                        String exName = exchange.length() > 16 ? exchange.substring(0, 16) : exchange;

                        String row = String.format(C + "║ " + P + "%-16s " + C + "║ " + Y + "%-8s " + C + "║ " + G + "%-24s " + C + "║ " + W + "%-15s " + C + "║" + R,
                                exName.toUpperCase(),
                                asset,
                                dfQty.format(qty), // Mostramos 8 decimales
                                "100%"
                        );
                        BotLogger.info(row);
                    }
                }
            } catch (Exception e) {
                // Si falla MEXC o Kucoin por configuración, saldrá aquí en rojo
                BotLogger.error("❌ Error leyendo saldo de " + exchange + ": " + e.getMessage());
            }
        }
        BotLogger.info(C + "╚══════════════════╩══════════╩══════════════════════════╩═════════════════╝" + R + "\n");
    }
}