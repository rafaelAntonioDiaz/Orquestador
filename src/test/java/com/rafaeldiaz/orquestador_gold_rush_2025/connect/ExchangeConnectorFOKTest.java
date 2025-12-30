package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🕵️ INSPECTOR DE PROTOCOLOS DE INTERCAMBIO
 * Verifica que las órdenes salientes cumplan estrictamente con los requisitos de Atomicidad (FOK).
 */
class ExchangeConnectorFOKTest {

    // Mock simple del proveedor de entorno para evitar cargar .env real
    private final ExchangeConnector.EnvProvider mockEnv = key -> "test_secret_key";

    // Instancia "desconectada" (no necesita red real para probar la construcción del request)
    private final ExchangeConnector connector = new ExchangeConnector(null, mockEnv);

    @Test
    @DisplayName("🟠 BYBIT: Debe inyectar 'timeInForce': 'FOK' en el JSON")
    void shouldBuildFokForBybit() throws IOException {
        Request request = connector.buildOrderRequest(
                "bybit", "BTC-USDT", "BUY", "LIMIT_FOK", 0.1, 50000.0
        );

        String body = bodyToString(request);

        // Verificación Forense
        assertThat(body)
                .as("Bybit requiere timeInForce en el cuerpo JSON")
                .contains("\"timeInForce\": \"FOK\"")
                .contains("\"orderType\": \"Limit\"");

        System.out.println("✅ BYBIT FOK CHECK: " + body);
    }

    @Test
    @DisplayName("🟡 BINANCE: Debe inyectar '&timeInForce=FOK' en la URL")
    void shouldBuildFokForBinance() {
        Request request = connector.buildOrderRequest(
                "binance", "BTC-USDT", "BUY", "LIMIT_FOK", 0.001, 50000.0
        );

        String url = request.url().toString();

        // Verificación Forense
        assertThat(url)
                .as("Binance requiere timeInForce como Query Param")
                .contains("timeInForce=FOK")
                .contains("type=LIMIT");

        System.out.println("✅ BINANCE FOK CHECK: " + url);
    }

    @Test
    @DisplayName("🟢 KUCOIN: Debe inyectar 'timeInForce': 'FOK' en el JSON")
    void shouldBuildFokForKucoin() throws IOException {
        Request request = connector.buildOrderRequest(
                "kucoin", "SOL-USDT", "SELL", "LIMIT_FOK", 10.0, 150.0
        );

        String body = bodyToString(request);

        // Verificación Forense
        assertThat(body)
                .as("KuCoin requiere timeInForce en el cuerpo JSON")
                .contains("\"timeInForce\": \"FOK\"")
                .contains("\"type\": \"limit\"");

        System.out.println("✅ KUCOIN FOK CHECK: " + body);
    }

    // Helper para leer el cuerpo del Request sin enviarlo
    private String bodyToString(Request request) throws IOException {
        if (request.body() == null) return "";
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readString(StandardCharsets.UTF_8);
    }
}