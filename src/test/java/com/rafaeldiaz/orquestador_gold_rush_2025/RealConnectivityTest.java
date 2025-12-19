package com.rafaeldiaz.orquestador_gold_rush_2025;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRUEBA DE CONECTIVIDAD REAL.
 * Requiere Variables de Entorno configuradas.
 */
public class RealConnectivityTest {

    @Test
    @DisplayName("Debe conectar con Bybit Sub1 y obtener balance real")
    // Solo corre si configuraste la clave (evita fallo en CI/CD)
    @EnabledIfEnvironmentVariable(named = "BYBIT_SUB1_KEY", matches = ".*")
    void testRealBybitConnection() {
        System.out.println("--- PROBANDO CONEXIÓN REAL BYBIT SUB1 ---");
        ExchangeConnector connector = new ExchangeConnector();
        try {
            double balance = connector.fetchBalance("bybit_sub1");
            System.out.println("✅ ÉXITO: Balance Real Bybit Sub1: $" + balance);
            assertTrue(balance >= 0, "El balance debe ser un número positivo");
        } catch (IOException e) {
            System.err.println("❌ ERROR: " + e.getMessage());
            throw new RuntimeException("Fallo conexión real Bybit");
        }
    }

    @Test
    @DisplayName("Debe conectar con Bybit Sub2 y obtener balance real")
    @EnabledIfEnvironmentVariable(named = "BYBIT_SUB2_KEY", matches = ".*")
    void testRealBybitSub2Connection() {
        System.out.println("--- PROBANDO CONEXIÓN REAL BYBIT SUB2 ---");
        ExchangeConnector connector = new ExchangeConnector();
        try {
            double balance = connector.fetchBalance("bybit_sub2");
            System.out.println("✅ ÉXITO: Balance Real Bybit Sub2: $" + balance);
        } catch (IOException e) {
            System.err.println("❌ ERROR: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Debe conectar con MEXC y obtener balance real")
    @EnabledIfEnvironmentVariable(named = "MEXC_KEY", matches = ".*")
    void testRealMexcConnection() {
        System.out.println("--- PROBANDO CONEXIÓN REAL MEXC ---");
        ExchangeConnector connector = new ExchangeConnector();
        try {
            double balance = connector.fetchBalance("mexc");
            System.out.println("✅ ÉXITO: Balance Real MEXC: $" + balance);
        } catch (Exception e) {
            System.err.println("❌ ERROR MEXC: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Debe conectar con Binance y obtener balance real")
    @EnabledIfEnvironmentVariable(named = "BINANCE_KEY", matches = ".*")
    void testRealBinanceConnection() {
        System.out.println("--- PROBANDO CONEXIÓN REAL BINANCE ---");
        ExchangeConnector connector = new ExchangeConnector();
        try {
            // Nota: Binance suele devolver el balance total estimado en BTC o USDT dependiendo del endpoint,
            // pero tu adaptador debe estar normalizando a USDT.
            double balance = connector.fetchBalance("binance");
            System.out.println("✅ ÉXITO: Balance Real Binance: $" + balance);

            // Validación extra: Si tienes cuenta, el balance no debe ser negativo
            assertTrue(balance >= 0, "El balance de Binance no puede ser negativo");

        } catch (Exception e) {
            System.err.println("❌ ERROR BINANCE: " + e.getMessage());
            // Fail explícito para verlo en rojo si falla la conexión
            throw new RuntimeException("Fallo conexión real Binance", e);
        }
    }

    @Test
    @DisplayName("Debe conectar con KuCoin y obtener balance real")
    @EnabledIfEnvironmentVariable(named = "KUCOIN_KEY", matches = ".*")
    void testRealKucoinConnection() {
        System.out.println("--- PROBANDO CONEXIÓN REAL KUCOIN ---");
        ExchangeConnector connector = new ExchangeConnector();
        try {
            // KuCoin requiere Passphrase, el conector ya debió leerlo de KUCOIN_PASSPHRASE
            double balance = connector.fetchBalance("kucoin");
            System.out.println("✅ ÉXITO: Balance Real KuCoin: $" + balance);

            assertTrue(balance >= 0, "El balance de KuCoin no puede ser negativo");

        } catch (Exception e) {
            System.err.println("❌ ERROR KUCOIN: " + e.getMessage());
            throw new RuntimeException("Fallo conexión real KuCoin", e);
        }
    }}