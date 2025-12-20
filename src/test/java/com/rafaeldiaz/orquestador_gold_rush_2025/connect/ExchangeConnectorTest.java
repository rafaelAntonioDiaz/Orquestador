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
    private static final String BYBIT_PRICE = "{ \"retCode\": 0, \"result\": { \"list\": [ { \"lastPrice\": \"50000.50\" } ] } }";
    private static final String BYBIT_BALANCE = "{ \"retCode\": 0, \"result\": { \"list\": [ { \"coin\": [ { \"coin\": \"USDT\", \"walletBalance\": \"1000.0\" } ] } ] } }";

    private static final String MEXC_PRICE = "{ \"symbol\": \"BTCUSDT\", \"price\": \"50005.00\" }";
    private static final String MEXC_BALANCE = "{ \"balances\": [ { \"asset\": \"USDT\", \"free\": \"2000.0\" } ] }"; // Ajustado estructura común

    private static final String BINANCE_PRICE = "{ \"symbol\": \"BTCUSDT\", \"price\": \"50010.00\" }";

    // KuCoin V1
    private static final String KUCOIN_PRICE = "{ \"data\": { \"price\": \"50015.00\" } }";
    private static final String KUCOIN_BALANCE = "{ \"code\": \"200000\", \"data\": [ { \"currency\": \"USDT\", \"type\": \"trade\", \"balance\": \"5000.0\", \"available\": \"4000.0\" } ] }";

    @BeforeEach
    void setUp() {
        // Configuración Lenient para evitar errores si no se llama a execute en algún test específico
        lenient().when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        // Simulamos el proveedor de variables de entorno
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

        // Inyectamos el Mock y el EnvProvider
        connector = new ExchangeConnector(mockHttpClient, mockEnv);
    }

    // --- TESTS ---

    @Test
    @DisplayName("Bybit: Fetch Price")
    void testPriceBybit() throws IOException {
        mockResponse(200, BYBIT_PRICE);
        double price = connector.fetchPrice("bybit_sub1", "BTCUSDT");
        assertEquals(50000.50, price);
    }

    @Test
    @DisplayName("Bybit: Balance & Firma")
    void testBalanceBybit() throws IOException {
        mockResponse(200, BYBIT_BALANCE);
        double balance = connector.fetchBalance("bybit_sub1", "USDT"); // Agregado parámetro asset

        assertEquals(1000.0, balance);

        // Verificar Headers
        Request req = captureRequest();
        assertNotNull(req.header("X-BAPI-SIGN"), "Falta firma Bybit");
        assertEquals("key_bybit", req.header("X-BAPI-API-KEY"));
    }

    @Test
    @DisplayName("KuCoin: Balance & Passphrase")
    void testBalanceKucoin() throws IOException {
        mockResponse(200, KUCOIN_BALANCE);
        double balance = connector.fetchBalance("kucoin", "USDT"); // Debe buscar 'available'

        // Según tu lógica Kucoin, debe retornar available (4000.0)
        assertEquals(4000.0, balance);

        Request req = captureRequest();
        assertNotNull(req.header("KC-API-PASSPHRASE"), "Falta Passphrase");
        assertNotNull(req.header("KC-API-SIGN"), "Falta firma");
    }

    // --- HELPERS ---

    private void mockResponse(int code, String body) throws IOException {
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://test.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(ResponseBody.create(body, MediaType.parse("application/json")))
                .build();

        when(mockCall.execute()).thenReturn(response);
    }

    private Request captureRequest() {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient, atLeastOnce()).newCall(captor.capture());
        return captor.getValue();
    }
}