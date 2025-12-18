package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ExchangeConnector {

    private static final String USER_AGENT = "SoloGoldRushBot/1.0";
    private static final long TIMEOUT_SEC = 3;

    @FunctionalInterface
    public interface EnvProvider {
        String get(String key);
    }

    private final Map<String, ExchangeAdapter> adapters = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient httpClient;
    private final EnvProvider envProvider;

    public ExchangeConnector() {
        this(
                new OkHttpClient.Builder()
                        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
                        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .build(),
                System::getenv
        );
    }

    protected ExchangeConnector(OkHttpClient httpClient, EnvProvider envProvider) {
        this.httpClient = httpClient;
        this.envProvider = envProvider;
        initializeAdapters();
    }

    private void initializeAdapters() {
        registerBybit("bybit_sub1", "BYBIT_SUB1_KEY", "BYBIT_SUB1_SECRET");
        registerMexc("mexc", "MEXC_KEY", "MEXC_SECRET");
        registerBinance("binance", "BINANCE_KEY", "BINANCE_SECRET");
        registerKucoin("kucoin", "KUCOIN_KEY", "KUCOIN_SECRET", "KUCOIN_PASSPHRASE");
    }

    private void registerBybit(String id, String keyEnv, String secretEnv) {
        String key = envProvider.get(keyEnv);
        String secret = envProvider.get(secretEnv);
        if (key != null && secret != null) {
            adapters.put(id, new BybitAdapter(key, secret, "https://api.bybit.com"));
        }
    }

    private void registerMexc(String id, String keyEnv, String secretEnv) {
        String key = envProvider.get(keyEnv);
        String secret = envProvider.get(secretEnv);
        if (key != null && secret != null) {
            adapters.put(id, new MexcAdapter(key, secret, "https://api.mexc.com"));
        }
    }

    private void registerBinance(String id, String keyEnv, String secretEnv) {
        String key = envProvider.get(keyEnv);
        String secret = envProvider.get(secretEnv);
        if (key != null && secret != null) {
            adapters.put(id, new BinanceAdapter(key, secret, "https://api.binance.com"));
        }
    }

    private void registerKucoin(String id, String keyEnv, String secretEnv, String passEnv) {
        String key = envProvider.get(keyEnv);
        String secret = envProvider.get(secretEnv);
        String pass = envProvider.get(passEnv);
        if (key != null && secret != null && pass != null) {
            adapters.put(id, new KucoinAdapter(key, secret, pass, "https://api.kucoin.com"));
        }
    }

    // --- API PÚBLICA ---

    public double fetchPrice(String exchangeId, String pair) throws IOException {
        ExchangeAdapter adapter = getAdapter(exchangeId);
        Request request = adapter.buildPriceRequest(pair);
        // Aquí T se infiere como Double
        return execute(request, adapter::parsePrice);
    }

    public double fetchBalance(String exchangeId) throws IOException {
        ExchangeAdapter adapter = getAdapter(exchangeId);
        long timestamp = Instant.now().toEpochMilli();
        Request request = adapter.buildBalanceRequest(timestamp);
        // Aquí T se infiere como Double
        return execute(request, adapter::parseBalance);
    }

    /**
     * Ejecuta una orden.
     * Task 2.2.2
     */
    public String placeOrder(String exchangeId, String pair, String side, String type, double qty, double price) throws IOException {
        ExchangeAdapter adapter = getAdapter(exchangeId);
        Request request = adapter.buildOrderRequest(pair, side, type, qty, price);

        // Aquí T se infiere como String porque el parser devuelve String
        return execute(request, json -> json.toString());
    }

    // --- INTERNOS ---

    private ExchangeAdapter getAdapter(String id) {
        ExchangeAdapter adapter = adapters.get(id);
        if (adapter == null) throw new IllegalArgumentException("Exchange no configurado: " + id);
        return adapter;
    }

    /**
     * Método GENÉRICO <T>.
     * Puede devolver Double, String, o lo que el parser decida.
     */
    private <T> T execute(Request request, Function<JsonNode, T> parser) throws IOException {
        Request finalRequest = request.newBuilder()
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = httpClient.newCall(finalRequest).execute()) {
            if (!response.isSuccessful()) {
                // Leemos el error body para debug
                String errorBody = response.body() != null ? response.body().string() : "No body";
                throw new IOException("HTTP Error en " + finalRequest.url() + ": " + response.code() + " -> " + errorBody);
            }
            // Parseamos el JSON
            String responseStr = response.body().string();
            JsonNode root = mapper.readTree(responseStr);

            // Aplicamos la función parser
            return parser.apply(root);
        }
    }
}