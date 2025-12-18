package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeConnectorTest {

    @Mock
    private OkHttpClient mockHttpClient;
    @Mock
    private Call mockCall;

    private ExchangeConnector connector;

    // --- FIXTURES (JSONs Simulados) ---

    // Bybit V5
    private static final String BYBIT_PRICE = """
        { "retCode": 0, "result": { "list": [ { "lastPrice": "50000.50" } ] } }
        """;
    private static final String BYBIT_BALANCE = """
        { "retCode": 0, "result": { "list": [ { "coin": [ { "coin": "USDT", "walletBalance": "1000.0" } ] } ] } }
        """;

    // MEXC V3
    private static final String MEXC_PRICE = """
        { "symbol": "BTCUSDT", "price": "50005.00" }
        """;
    private static final String MEXC_BALANCE = """
        { "balances": [ { "asset": "USDT", "free": "2000.0" } ] }
        """;

    // Binance V3
    private static final String BINANCE_PRICE = """
        { "symbol": "BTCUSDT", "price": "50010.00" }
        """;
    private static final String BINANCE_BALANCE = """
        { "balances": [ { "asset": "USDT", "free": "3000.0" } ] }
        """;

    // KuCoin V1
    private static final String KUCOIN_PRICE = """
        { "data": { "price": "50015.00" } }
        """;
    private static final String KUCOIN_BALANCE = """
        {
            "code": "200000",
            "data": [
                { "currency": "USDT", "type": "trade", "balance": "5000.0", "available": "4000.0" }
            ]
        }
        """;

    @BeforeEach
    void setUp() {
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        // Simulamos TODOS los entornos
        ExchangeConnector.EnvProvider mockEnv = key -> switch (key) {
            case "BYBIT_SUB1_KEY" -> "key_bybit";
            case "BYBIT_SUB1_SECRET" -> "secret_bybit";

            case "MEXC_KEY" -> "key_mexc";
            case "MEXC_SECRET" -> "secret_mexc";

            case "BINANCE_KEY" -> "key_binance";
            case "BINANCE_SECRET" -> "secret_binance";

            case "KUCOIN_KEY" -> "key_kucoin";
            case "KUCOIN_SECRET" -> "secret_kucoin";
            case "KUCOIN_PASSPHRASE" -> "pass_kucoin";

            default -> null;
        };
        connector = new ExchangeConnector(mockHttpClient, mockEnv);
    }

    // --- TESTS DE PRECIOS (Públicos) ---

    @Test
    @DisplayName("Bybit: Fetch Price")
    void testPriceBybit() throws IOException {
        mockResponse(200, BYBIT_PRICE);
        assertEquals(50000.50, connector.fetchPrice("bybit_sub1", "BTCUSDT"));
        verifyUrlContains("api.bybit.com");
    }

    @Test
    @DisplayName("MEXC: Fetch Price")
    void testPriceMexc() throws IOException {
        mockResponse(200, MEXC_PRICE);
        assertEquals(50005.00, connector.fetchPrice("mexc", "BTCUSDT"));
        verifyUrlContains("api.mexc.com");
    }

    @Test
    @DisplayName("Binance: Fetch Price")
    void testPriceBinance() throws IOException {
        mockResponse(200, BINANCE_PRICE);
        assertEquals(50010.00, connector.fetchPrice("binance", "BTCUSDT"));
        verifyUrlContains("api.binance.com");
    }

    @Test
    @DisplayName("KuCoin: Fetch Price")
    void testPriceKucoin() throws IOException {
        mockResponse(200, KUCOIN_PRICE);
        assertEquals(50015.00, connector.fetchPrice("kucoin", "BTCUSDT"));
        verifyUrlContains("api.kucoin.com");
    }

    // --- TESTS DE BALANCES (Privados & Firmados) ---

    @Test
    @DisplayName("Bybit: Balance & Firma Header")
    void testBalanceBybit() throws IOException {
        mockResponse(200, BYBIT_BALANCE);
        assertEquals(1000.0, connector.fetchBalance("bybit_sub1"));

        Request req = captureRequest();
        assertNotNull(req.header("X-BAPI-SIGN"), "Falta firma Bybit");
        assertEquals("key_bybit", req.header("X-BAPI-API-KEY"));
    }

    @Test
    @DisplayName("MEXC: Balance & Firma URL")
    void testBalanceMexc() throws IOException {
        mockResponse(200, MEXC_BALANCE);
        assertEquals(2000.0, connector.fetchBalance("mexc"));

        Request req = captureRequest();
        assertTrue(req.url().toString().contains("signature="), "Falta firma en URL MEXC");
        assertEquals("key_mexc", req.header("X-MEXC-APIKEY"));
    }

    @Test
    @DisplayName("Binance: Balance & Firma URL")
    void testBalanceBinance() throws IOException {
        mockResponse(200, BINANCE_BALANCE);
        assertEquals(3000.0, connector.fetchBalance("binance"));

        Request req = captureRequest();
        assertTrue(req.url().toString().contains("signature="), "Falta firma en URL Binance");
        assertEquals("key_binance", req.header("X-MBX-APIKEY"));
    }

    @Test
    @DisplayName("KuCoin: Balance, Passphrase & Firma Base64")
    void testBalanceKucoin() throws IOException {
        mockResponse(200, KUCOIN_BALANCE);

        // Debe extraer "available" (4000.0), no "balance"
        assertEquals(4000.0, connector.fetchBalance("kucoin"));

        Request req = captureRequest();
        // Verificaciones únicas de KuCoin
        assertNotNull(req.header("KC-API-SIGN"), "Falta firma KuCoin");
        assertNotNull(req.header("KC-API-PASSPHRASE"), "Falta Passphrase Header");
        assertNotNull(req.header("KC-API-KEY-VERSION"), "Falta Versión API");

        // Verificamos que la firma NO sea vacía (Base64)
        assertFalse(req.header("KC-API-SIGN").isEmpty());
    }

    // --- HELPERS ---

    private void mockResponse(int code, String body) throws IOException {
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://test.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
        when(mockCall.execute()).thenReturn(response);
    }

    private void verifyUrlContains(String part) {
        Request req = captureRequest();
        assertTrue(req.url().toString().contains(part), "URL incorrecta: " + req.url());
    }

    private Request captureRequest() {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient).newCall(captor.capture());
        return captor.getValue();
    }
}