package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.*;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.util.List;

/**
 * 🛠️ SYSTEM CHECK DIAGNOSTIC (MATRIZ DE LA VERDAD UNIVERSAL) 🛠️
 * Auditoría forense de conectividad, precios y fees reales para Múltiples Activos.
 * Valida: Stablecoins, L1s, Memecoins y Bitcoin a través de todo el espectro de exchanges.
 */
public class SystemCheckTest {

    private final ExchangeConnector connector = new ExchangeConnector();
    private final FeeManager feeManager = new FeeManager(connector);

    // Formatos visuales adaptativos
    private final DecimalFormat money = new DecimalFormat("$#,##0.000000"); // 6 decimales para ver precios bajos
    private final DecimalFormat cryptoPrecise = new DecimalFormat("0.0000####"); // Para BTC, SOL
    private final DecimalFormat cryptoHuge = new DecimalFormat("#,###"); // Para PEPE, SHIB (Enteros grandes)

    @Test
    void runDeepSystemAudit() {
        printHeader("🚨 INICIANDO ESCANEO DE ESPECTRO COMPLETO (DEEP SCAN) 🚨");

        // 1. CARGAMOS LOS 4 MOTORES
        List<ExchangeStrategy> strategies = List.of(
                new BinanceStrategy(),
                new BybitStrategy(connector),
                new MexcStrategy(connector),
                new KucoinStrategy(connector)
        );

        // 2. DEFINIMOS LA CANASTA DE CAZA (HUNTING GROUNDS REPRESENTATIVOS)
        // Incluimos variedad para estresar el sistema de formatos y fees.
        List<String> auditBasket = List.of("USDT", "SOL", "BTC", "PEPE", "XRP", "AVAX");

        for (String asset : auditBasket) {
            printSection("ANALIZANDO ACTIVO: " + asset);

            // Cabecera de Tabla Dinámica
            System.out.printf("%-10s | %-12s | %-18s | %-25s | %-10s%n",
                    "EXCHANGE", "CONEXIÓN", "PRECIO (USDT)", "FEE RETIRO (" + asset + ")", "ESTADO");
            System.out.println("------------------------------------------------------------------------------------------");

            for (ExchangeStrategy strategy : strategies) {
                String exName = strategy.getName();
                String connectionStatus;
                String priceStatus = "---";
                String feeStatusStr;
                boolean criticalFailure = false;

                // A. PRUEBA DE CONEXIÓN (Ping de Saldo)
                try {
                    String checkCode = exName.equalsIgnoreCase("bybit") ? "bybit_sub1" : exName.toLowerCase();
                    // Usamos USDT como ping universal de conexión
                    double balance = connector.fetchBalance(checkCode, "USDT");
                    connectionStatus = (balance >= 0) ? "🟢 ON" : "🔴 OFF";
                } catch (Exception e) {
                    connectionStatus = "🔥 ERR";
                }

                // B. PRUEBA DE VISIÓN (Precio)
                if (!asset.equals("USDT")) {
                    try {
                        String pair = asset + "USDT";
                        double price = strategy.fetchAsk(pair);
                        if (price > 0) {
                            // Ajustamos visualización si el precio es muy pequeño (Pepe)
                            priceStatus = (price < 0.01) ? String.format("$%.8f", price) : money.format(price);
                        } else {
                            priceStatus = "❌ CIEGO";
                            criticalFailure = true; // No podemos operar si no vemos el precio
                        }
                    } catch (Exception e) {
                        priceStatus = "🔥 ERR";
                    }
                } else {
                    priceStatus = "   $1.00   "; // Base
                }

                // C. PRUEBA DE FEE (La Verdad)
                // 1. Fee Crudo (API Real)
                double rawFee = connector.fetchLiveWithdrawalFee(exName.toLowerCase(), asset);

                // 2. Fee Seguro (Backup Pesimista)
                double safeFee = feeManager.getWithdrawalFee(exName.toLowerCase(), asset);

                if (rawFee >= 0) {
                    // Si el fee es > 100 (ej. PEPE), usamos formato entero. Si es pequeño (BTC), decimales.
                    String feeFmt = (rawFee > 100) ? cryptoHuge.format(rawFee) : cryptoPrecise.format(rawFee);
                    feeStatusStr = "✅ " + feeFmt;
                } else {
                    feeStatusStr = "⚠️ FALLO API";
                    // Verificamos el paracaídas
                    if (safeFee > 0) {
                        String safeFmt = (safeFee > 100) ? cryptoHuge.format(safeFee) : cryptoPrecise.format(safeFee);
                        feeStatusStr += " (Backup: " + safeFmt + ")";
                    } else {
                        feeStatusStr += " 💀 CRÍTICO";
                        criticalFailure = true; // No podemos operar sin saber costo ni tener backup
                    }
                }

                // D. RESULTADO FINAL
                String finalStatus = criticalFailure ? "❌ REVISAR" : "✅ OK";

                System.out.printf("%-10s | %-12s | %-18s | %-25s | %-10s%n",
                        exName, connectionStatus, priceStatus, feeStatusStr, finalStatus);
            }
            System.out.println("------------------------------------------------------------------------------------------");
        }
        printHeader("AUDITORÍA PROFUNDA FINALIZADA");
    }

    private void printHeader(String title) {
        System.out.println("\n==========================================================================================");
        System.out.println("      " + title);
        System.out.println("==========================================================================================");
    }

    private void printSection(String title) {
        System.out.println("\n " + title);
        System.out.println("==========================================================================================");
    }
}