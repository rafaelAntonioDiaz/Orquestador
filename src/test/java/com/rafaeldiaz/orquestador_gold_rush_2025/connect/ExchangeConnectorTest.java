package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 🌍 GLOBAL EXCHANGE PROTOCOL TEST
 * Valida la integridad de la construcción de órdenes (FOK, Firmas, Headers)
 * para Binance, MEXC, Bybit y KuCoin.
 */
class ExchangeConnectorTest {

    @Mock
    private OkHttpClient mockClient;
    @Mock
    private Call mockCall;
    @Mock
    private ExchangeConnector.EnvProvider mockEnv;

    private ExchangeConnector connector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 1. Simulación de Bóveda de Claves (Todas las plataformas)
        when(mockEnv.get(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (key.contains("PASSPHRASE")) return "secret_passphrase"; // Solo KuCoin
            if (key.contains("KEY")) return "test_api_key";
            if (key.contains("SECRET")) return "test_secret_key";
            return null;
        });

        // 2. Mock Básico (No necesitamos respuestas de red para validar la construcción del Request)
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);

        connector = new ExchangeConnector(mockClient, mockEnv);
    }

    // =========================================================================
    // 🟡 BINANCE (El Estándar)
    // =========================================================================
    @Test
    @DisplayName("🟡 BINANCE: Protocolo FOK (Query Param + Header MBX)")
    void shouldBuildCorrectly_Binance() {
        // Ejecución
        Request request = connector.buildOrderRequest("binance", "BTC-USDT", "BUY", "LIMIT_FOK", 0.5, 60000.0);
        String url = request.url().toString();

        // 1. Validación FOK (Query String)
        assertThat(url)
                .as("Binance debe llevar timeInForce en la URL")
                .contains("timeInForce=FOK");

        // 2. Validación Firma
        assertThat(url).contains("signature=");
        assertThat(url).contains("timestamp=");

        // 3. Validación Header Específico
        assertThat(request.header("X-MBX-APIKEY"))
                .as("Header de Binance incorrecto")
                .isEqualTo("test_api_key");

        System.out.println("✅ BINANCE CHECK: OK");
    }

    // =========================================================================
    // 🔵 MEXC (El Clon)
    // =========================================================================
    @Test
    @DisplayName("🔵 MEXC: Protocolo FOK (Query Param + Header MEXC)")
    void shouldBuildCorrectly_Mexc() {
        // Ejecución
        Request request = connector.buildOrderRequest("mexc", "MX-USDT", "SELL", "LIMIT_FOK", 100.0, 2.5);
        String url = request.url().toString();

        // 1. Validación FOK (Query String)
        assertThat(url)
                .as("MEXC debe llevar timeInForce en la URL")
                .contains("timeInForce=FOK");

        // 2. Validación Header Específico (Diferente a Binance)
        assertThat(request.header("X-MEXC-APIKEY"))
                .as("Header de MEXC incorrecto")
                .isEqualTo("test_api_key");

        // 3. Validación Content-Type (MEXC a veces lo exige)
        assertThat(request.header("Content-Type"))
                .contains("application/x-www-form-urlencoded");

        System.out.println("✅ MEXC CHECK: OK");
    }

    // =========================================================================
    // 🟠 BYBIT (V5 JSON)
    // =========================================================================
    @Test
    @DisplayName("🟠 BYBIT: Protocolo FOK (JSON Body + Headers V5)")
    void shouldBuildCorrectly_Bybit() throws IOException {
        // Ejecución
        Request request = connector.buildOrderRequest("bybit", "SOL-USDT", "BUY", "LIMIT_FOK", 10.0, 150.0);
        String body = bodyToString(request);

        // 1. Validación FOK (Dentro del JSON)
        assertThat(body)
                .as("Bybit V5 requiere timeInForce en el JSON")
                .contains("\"timeInForce\": \"FOK\"");

        // 2. Validación Estructura V5
        assertThat(body).contains("\"category\": \"spot\"");
        assertThat(body).contains("\"orderType\": \"Limit\"");

        // 3. Validación Headers de Firma
        assertThat(request.header("X-BAPI-API-KEY")).isEqualTo("test_api_key");
        assertThat(request.header("X-BAPI-SIGN")).isNotNull();
        assertThat(request.header("X-BAPI-TIMESTAMP")).isNotNull();

        System.out.println("✅ BYBIT CHECK: OK");
    }

    // =========================================================================
    // 🟢 KUCOIN (El Complejo)
    // =========================================================================
    @Test
    @DisplayName("🟢 KUCOIN: Protocolo FOK (Headers KC + Passphrase Encriptada)")
    void shouldBuildCorrectly_Kucoin() throws IOException {
        // Ejecución
        Request request = connector.buildOrderRequest("kucoin", "PEPE-USDT", "BUY", "LIMIT_FOK", 100000.0, 0.00001);
        String body = bodyToString(request);

        // 1. Validación FOK (JSON)
        assertThat(body)
                .as("KuCoin requiere timeInForce en el JSON")
                .contains("\"timeInForce\": \"FOK\"");

        // 2. Validación de UUID (ClientOid)
        assertThat(body).contains("\"clientOid\":");

        // 3. Validación Headers Específicos
        assertThat(request.header("KC-API-KEY")).isEqualTo("test_api_key");
        assertThat(request.header("KC-API-KEY-VERSION")).isEqualTo("2"); // Importante V2

        // 4. 🔥 CRÍTICO: La Passphrase NO debe ir en texto plano
        String passHeader = request.header("KC-API-PASSPHRASE");
        assertThat(passHeader)
                .as("La Passphrase de KuCoin debe estar encriptada (Base64), no texto plano")
                .isNotEqualTo("secret_passphrase")
                .isNotNull();

        System.out.println("✅ KUCOIN CHECK: OK");
    }

    // --- HELPER DE LECTURA DE BODY ---
    private String bodyToString(Request request) throws IOException {
        if (request.body() == null) return "";
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readString(StandardCharsets.UTF_8);
    }
}