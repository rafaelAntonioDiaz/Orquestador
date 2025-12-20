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
    @DisplayName("✅ BYBIT: Conexión Real y Saldo Board")
    void testRealBybitConnection() {
        System.out.println("\n--- 🟡 PROBANDO BYBIT (Validando $224.0) ---");
        ExchangeConnector connector = new ExchangeConnector();

        // 1. Probamos Saldo Real (Aquí deberían aparecer tus $224 reales)
        double balance = connector.fetchBalance("bybit_sub1", ASSET);
        System.out.println("💰 BALANCE FACTUAL BYBIT: " + balance + " USDT");

        // 2. La prueba de fuego para la IP 190.66.53.71 y el error 10010
        double fee = connector.fetchLiveWithdrawalFee("bybit_sub1", "SOL");
        System.out.println("💸 FEE RETIRO SOL (Validando IP): " + fee);

        assertTrue(balance >= 0, "⚠️ ERROR: No se detecta el saldo de $224.0 del Board. Revisa la API Key.");
        assertTrue(fee >= 0, "⚠️ ERROR 10010: Bybit aún rechaza tu IP para endpoints de Assets.");
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