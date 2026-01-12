package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector.EnvProvider;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeConnectorTest {

    private ExchangeConnector connector;

    @BeforeEach
    void setUp() {
        // 1. SIMULAMOS UN ENTORNO AISLADO (SIN INTERNET NI BASE DE DATOS)
        OkHttpClient mockClient = mock(OkHttpClient.class);
        when(mockClient.newCall(any(Request.class))).thenReturn(mock(Call.class));

        // Simulamos claves falsas para que la firma HMAC no falle
        EnvProvider mockEnv = key -> "TEST_SECRET_KEY_12345";

        // Inyectamos los mocks
        connector = new ExchangeConnector(mockClient, mockEnv);
    }

    // =========================================================================
    // 🟠 BYBIT V5 (Ya probado, mantenemos la vigilancia)
    // =========================================================================
    @Test
    @DisplayName("🟠 BYBIT: Verifica 'marketUnit: quoteCoin' (Gastar USDT)")
    void testBybit_QuoteOrder() {
        Request request = buildRequest("bybit", "IMX-USDT", 50.0, true);
        String body = getBodyAsString(request);

        System.out.println("🟠 BYBIT JSON: " + body);
        assertTrue(body.contains("\"marketUnit\": \"quoteCoin\""), "❌ Bybit falló: Falta 'quoteCoin'");
        assertTrue(body.contains("\"qty\": \"50.0000\""), "❌ Bybit falló: Cantidad incorrecta");
    }

    // =========================================================================
    // 🟡 BINANCE (V3 API)
    // =========================================================================
    @Test
    @DisplayName("🟡 BINANCE: Verifica 'quoteOrderQty' en Query Params")
    void testBinance_QuoteOrder() {
        // Binance envía los datos en la URL, no en el JSON Body
        Request request = buildRequest("binance", "IMX-USDT", 50.0, true);
        String url = request.url().toString();

        System.out.println("🟡 BINANCE URL: " + url);
        assertTrue(url.contains("quoteOrderQty=50.0000"), "❌ Binance falló: Falta 'quoteOrderQty' (Gastar USDT)");
        assertTrue(!url.contains("quantity="), "❌ Binance falló: No debería tener 'quantity' (Tokens)");
    }

    // =========================================================================
    // 🟢 KUCOIN (V1 API)
    // =========================================================================
    @Test
    @DisplayName("🟢 KUCOIN: Verifica parámetro 'funds' (Gastar USDT)")
    void testKucoin_QuoteOrder() {
        Request request = buildRequest("kucoin", "IMX-USDT", 50.0, true);
        String body = getBodyAsString(request);

        System.out.println("🟢 KUCOIN JSON: " + body);
        assertTrue(body.contains("\"funds\": \"50.0000\""), "❌ KuCoin falló: Falta 'funds' (USDT)");
        assertTrue(!body.contains("\"size\":"), "❌ KuCoin falló: No debería tener 'size' (Tokens)");
    }

    // =========================================================================
    // 🔵 OKX (V5 API)
    // =========================================================================
    @Test
    @DisplayName("🔵 OKX: Verifica 'tgtCcy: quote_ccy' (Target Currency)")
    void testOkx_QuoteOrder() {
        Request request = buildRequest("okx", "IMX-USDT", 50.0, true);
        String body = getBodyAsString(request);

        System.out.println("🔵 OKX JSON: " + body);
        assertTrue(body.contains("\"tgtCcy\": \"quote_ccy\""), "❌ OKX falló: Falta 'tgtCcy: quote_ccy'");
        assertTrue(body.contains("\"sz\": \"50.0000\""), "❌ OKX falló: Cantidad 'sz' incorrecta");
    }

    // =========================================================================
    // 🛡️ PRUEBA DE REGRESIÓN (MODO CLÁSICO)
    // Verifica que si pedimos comprar TOKENS, siga funcionando como antes
    // =========================================================================
    @Test
    @DisplayName("🛡️ LEGACY MODE: Verifica compra por Tokens (Base Order)")
    void testLegacyMode_Tokens() {
        // Caso: Binance comprando 25.5 Tokens
        Request request = buildRequest("binance", "IMX-USDT", 25.5, false); // FALSE = Base Order
        String url = request.url().toString();

        System.out.println("🛡️ LEGACY URL: " + url);
        assertTrue(url.contains("quantity=25.5"), "❌ Legacy falló: Debería usar 'quantity' para tokens");
        assertTrue(!url.contains("quoteOrderQty"), "❌ Legacy falló: No debería usar 'quoteOrderQty'");
    }

    // --- UTILIDADES ---
    private Request buildRequest(String exchange, String pair, double qty, boolean isQuote) {
        try {
            return connector.buildOrderRequest(exchange, pair, "BUY", "MARKET", qty, 0, isQuote);
        } catch (Exception e) {
            fail("Excepción construyendo request: " + e.getMessage());
            return null;
        }
    }

    private String getBodyAsString(Request request) {
        try {
            if (request.body() == null) return "";
            final Request copy = request.newBuilder().build();
            final Buffer buffer = new Buffer();
            copy.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "Error reading JSON";
        }
    }
}