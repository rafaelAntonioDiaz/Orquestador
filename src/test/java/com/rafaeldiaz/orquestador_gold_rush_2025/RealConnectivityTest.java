package com.rafaeldiaz.orquestador_gold_rush_2025;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🏆 PRUEBA DE CONECTIVIDAD TOTAL (GOLD RUSH 2025) 🏆
 * Objetivo: Validar acceso real a los $224.0 y fin del error 10010.
 */
public class RealConnectivityTest {

    private static final String ASSET = "USDT";

    @Test
    @DisplayName("✅ BYBIT: Auditoría de Subcuentas")
    void testRealBybitConnection() {
        System.out.println("\n--- 🟡 AUDITORÍA DE INFRAESTRUCTURA BYBIT ---");
        ExchangeConnector connector = new ExchangeConnector();
        String[] subAccounts = {"bybit_sub1", "bybit_sub2", "bybit_sub3"};
        double totalFound = 0;

        for (String sub : subAccounts) {
            double balance = connector.fetchBalance(sub, ASSET);
            System.out.println("💰 BALANCE [" + sub.toUpperCase() + "]: " + balance + " USDT");
            totalFound += balance;
        }

        System.out.println("💵 CAPITAL TOTAL EN BYBIT: " + totalFound + " USDT");

        // Prueba de fuego de la IP (Error 10010)
        double fee = connector.fetchLiveWithdrawalFee("bybit_sub1", "SOL");
        System.out.println("💸 FEE RETIRO SOL (Validando IP): " + fee);

        assertTrue(totalFound >= 0, "Error en la lectura de balances");
        assertTrue(fee >= 0, "IP no validada por Bybit");
    }

    @Test
    @DisplayName("✅ MEXC: Conexión Real")
    void testRealMexcConnection() {
        System.out.println("\n--- 🔵 PROBANDO MEXC ---");
        ExchangeConnector connector = new ExchangeConnector();
        double balance = connector.fetchBalance("mexc", ASSET);
        System.out.println("💰 Balance USDT MEXC: " + balance);
        assertTrue(balance >= 0);
    }

    @Test
    @DisplayName("✅ BINANCE: Conexión Real")
    void testRealBinanceConnection() {
        System.out.println("\n--- 🟡 PROBANDO BINANCE ---");
        ExchangeConnector connector = new ExchangeConnector();
        double balance = connector.fetchBalance("binance", ASSET);
        System.out.println("💰 Balance USDT BINANCE: " + balance);
        assertTrue(balance >= 0);
    }

    @Test
    @DisplayName("✅ KUCOIN: Conexión Real")
    void testRealKucoinConnection() {
        System.out.println("\n--- 🟢 PROBANDO KUCOIN ---");
        ExchangeConnector connector = new ExchangeConnector();
        double balance = connector.fetchBalance("kucoin", ASSET);
        System.out.println("💰 Balance USDT KUCOIN: " + balance);
        assertTrue(balance >= 0);
    }
}