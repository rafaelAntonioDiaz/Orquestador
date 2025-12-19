package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 🏛️ CONECTOR MAESTRO DE EXCHANGES 🏛️
 * Versión Final: Incluye Auth, Precios, Fees, Balance y Ejecución de Órdenes.
 */
public class ExchangeConnector {

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final Dotenv dotenv;

    // Constantes de URLs Base
    private static final String BYBIT_URL = "https://api.bybit.com";
    private static final String BINANCE_URL = "https://api.binance.com";
    private static final String MEXC_URL = "https://api.mexc.com";
    private static final String KUCOIN_URL = "https://api.kucoin.com";

    public ExchangeConnector() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
        this.dotenv = Dotenv.load();
    }

    // =========================================================================
    // 💰 1. GESTIÓN DE SALDOS (MÉTODO QUE FALTABA)
    // =========================================================================

    /**
     * Consulta el saldo DISPONIBLE de una moneda en la cuenta Spot/Unified.
     */
    public double fetchBalance(String exchange, String asset) {
        try {
            if (exchange.startsWith("bybit")) {
                // Bybit V5 Unified Account
                String endpoint = "/v5/account/wallet-balance?accountType=UNIFIED&coin=" + asset;
                Request request = buildSignedRequest(exchange, "GET", endpoint, "");

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        // Parseo profundo: result -> list[0] -> coin[0] -> walletBalance
                        if (root.get("retCode").asInt() == 0) {
                            JsonNode list = root.get("result").get("list");
                            if (list.isArray() && list.size() > 0) {
                                JsonNode coins = list.get(0).get("coin");
                                for (JsonNode c : coins) {
                                    if (c.get("coin").asText().equals(asset)) {
                                        return Double.parseDouble(c.get("walletBalance").asText());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // TODO: Implementar Binance/Mexc balances aquí
            return 0.0;

        } catch (Exception e) {
            BotLogger.error("Error leyendo balance " + exchange + ": " + e.getMessage());
            return 0.0;
        }
    }

    // =========================================================================
    // 🔫 2. EJECUCIÓN DE ÓRDENES (MÉTODO QUE FALTABA)
    // =========================================================================

    /**
     * Envía una orden al mercado y devuelve el Order ID si fue exitosa.
     */
    public String placeOrder(String exchange, String pair, String side, String type, double qty, double price) {
        try {
            Request request = buildOrderRequest(exchange, pair, side, type, qty, price);
            if (request == null) return null;

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    BotLogger.error("❌ Error Order " + exchange + ": " + responseBody);
                    return null;
                }

                JsonNode root = mapper.readTree(responseBody);

                if (exchange.startsWith("bybit")) {
                    if (root.get("retCode").asInt() == 0) {
                        String orderId = root.get("result").get("orderId").asText();
                        BotLogger.info("✅ Orden EXITOSA en " + exchange + " ID: " + orderId);
                        return orderId;
                    } else {
                        BotLogger.error("❌ Bybit Rechazo: " + root.get("retMsg").asText());
                    }
                }
                return null;
            }
        } catch (Exception e) {
            BotLogger.error("❌ Excepción enviando orden: " + e.getMessage());
            return null;
        }
    }

    public Request buildOrderRequest(String exchange, String pair, String side, String type, double qty, double price) {
        if (exchange.startsWith("bybit")) {
            // Bybit V5 Order Payload
            // type debe ser 'Market' o 'Limit' (Capitalizado para Bybit)
            String orderType = type.equalsIgnoreCase("LIMIT") ? "Limit" : "Market";
            String sideCap = side.equalsIgnoreCase("BUY") ? "Buy" : "Sell";

            // Para Market orders, qty es la cantidad de moneda base.
            String json = String.format(
                    "{\"category\":\"spot\",\"symbol\":\"%s\",\"side\":\"%s\",\"orderType\":\"%s\",\"qty\":\"%s\"%s}",
                    pair, sideCap, orderType, String.valueOf(qty),
                    orderType.equals("Limit") ? ",\"price\":\"" + price + "\"" : ""
            );
            return buildSignedRequest(exchange, "POST", "/v5/order/create", json);
        }
        return null;
    }

    // =========================================================================
    // 📡 3. MÉTODOS PÚBLICOS DE LECTURA (PRECIOS Y VELAS)
    // =========================================================================

    public double fetchPrice(String exchange, String pair) {
        String cleanPair = pair.replace("-", "").toUpperCase();
        String url;

        try {
            Request request;
            if (exchange.startsWith("bybit")) {
                url = BYBIT_URL + "/v5/market/tickers?category=spot&symbol=" + cleanPair;
                request = new Request.Builder().url(url).get().build();
            } else if (exchange.equals("binance")) {
                url = BINANCE_URL + "/api/v3/ticker/price?symbol=" + cleanPair;
                request = new Request.Builder().url(url).get().build();
            } else if (exchange.equals("mexc")) {
                url = MEXC_URL + "/api/v3/ticker/price?symbol=" + cleanPair;
                request = new Request.Builder().url(url).get().build();
            } else if (exchange.equals("kucoin")) {
                String kPair = pair.contains("-") ? pair : pair.replace("USDT", "-USDT");
                url = KUCOIN_URL + "/api/v1/market/orderbook/level1?symbol=" + kPair;
                request = new Request.Builder().url(url).get().build();
            } else {
                return 0.0;
            }

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return 0.0;
                return parsePrice(exchange, response.body().string());
            }

        } catch (Exception e) {
            return 0.0;
        }
    }

    public double[][] fetchCandles(String exchange, String pair, String interval, int limit) {
        if (exchange.startsWith("bybit")) {
            try {
                String url = BYBIT_URL + "/v5/market/kline?category=spot&symbol=" + pair + "&interval=" + interval + "&limit=" + limit;
                Request request = new Request.Builder().url(url).get().build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) return new double[0][0];
                    JsonNode root = mapper.readTree(response.body().string());
                    JsonNode list = root.get("result").get("list");

                    if (list == null || !list.isArray()) return new double[0][0];

                    double[][] candles = new double[list.size()][3];
                    for (int i = 0; i < list.size(); i++) {
                        JsonNode candle = list.get(i);
                        candles[i][0] = Double.parseDouble(candle.get(2).asText()); // High
                        candles[i][1] = Double.parseDouble(candle.get(3).asText()); // Low
                        candles[i][2] = Double.parseDouble(candle.get(4).asText()); // Close
                    }
                    return candles;
                }
            } catch (Exception e) { return new double[0][0]; }
        }
        return new double[0][0];
    }

    // =========================================================================
    // 💰 4. GESTIÓN DINÁMICA DE FEES
    // =========================================================================

    public double[] fetchDynamicTradingFee(String exchange, String symbol) {
        double[] defaultFees = {0.001, 0.001};
        if (exchange.startsWith("bybit")) {
            try {
                String endpoint = "/v5/account/fee-rate?category=spot&symbol=" + symbol;
                Request request = buildSignedRequest(exchange, "GET", endpoint, "");

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        if (root.get("retCode").asInt() == 0) {
                            JsonNode item = root.get("result").get("list").get(0);
                            return new double[]{
                                    Double.parseDouble(item.get("takerFeeRate").asText()),
                                    Double.parseDouble(item.get("makerFeeRate").asText())
                            };
                        }
                    }
                }
            } catch (Exception e) { }
        }
        return defaultFees;
    }

    public double fetchLiveWithdrawalFee(String exchange, String coin) {
        if (exchange.startsWith("bybit")) {
            try {
                String endpoint = "/v5/asset/coin-info?coin=" + coin;
                Request request = buildSignedRequest(exchange, "GET", endpoint, "");

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        if (root.get("retCode").asInt() == 0) {
                            double minFee = 1000.0;
                            boolean found = false;
                            for (JsonNode row : root.get("result").get("rows")) {
                                for (JsonNode chain : row.get("chains")) {
                                    if (chain.get("chainWithdraw").asInt() == 1) {
                                        double fee = Double.parseDouble(chain.get("withdrawFee").asText());
                                        if (fee < minFee) minFee = fee;
                                        found = true;
                                    }
                                }
                            }
                            return found ? minFee : 1.0;
                        }
                    }
                }
            } catch (Exception e) { }
        }
        return 1.0;
    }

    // =========================================================================
    // 🔐 5. LÓGICA DE FIRMA (CORE)
    // =========================================================================

    public Request buildSignedRequest(String exchange, String method, String endpoint, String jsonPayload) {
        String apiKey = getApiKey(exchange);
        String secretKey = getApiSecret(exchange);

        if (apiKey == null || secretKey == null) throw new RuntimeException("Sin claves para " + exchange);

        if (exchange.startsWith("bybit")) {
            long timestamp = Instant.now().toEpochMilli();
            String recvWindow = "5000";
            String strToSign = timestamp + apiKey + recvWindow + jsonPayload;
            String signature = hmacSha256(strToSign, secretKey);

            Request.Builder builder = new Request.Builder()
                    .url(BYBIT_URL + endpoint)
                    .header("X-BAPI-API-KEY", apiKey)
                    .header("X-BAPI-SIGN", signature)
                    .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                    .header("X-BAPI-RECV-WINDOW", recvWindow)
                    .header("Content-Type", "application/json");

            if (method.equals("POST")) builder.post(RequestBody.create(jsonPayload, MediaType.get("application/json")));
            else builder.get();

            return builder.build();
        }

        // Binance/MEXC fallbacks (Query param signature)
        long timestamp = Instant.now().toEpochMilli();
        String baseUrl = exchange.equals("binance") ? BINANCE_URL : MEXC_URL;
        String qs = "timestamp=" + timestamp;
        String sig = hmacSha256(qs, secretKey);
        String url = baseUrl + endpoint + "?" + qs + "&signature=" + sig;

        return new Request.Builder().url(url).header("X-MBX-APIKEY", apiKey).get().build();
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            return bytesToHex(sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private double parsePrice(String exchange, String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        if (exchange.startsWith("bybit")) {
            return Double.parseDouble(root.get("result").get("list").get(0).get("lastPrice").asText());
        } else if (exchange.equals("binance") || exchange.equals("mexc")) {
            return Double.parseDouble(root.get("price").asText());
        } else if (exchange.equals("kucoin")) {
            return Double.parseDouble(root.get("data").get("price").asText());
        }
        return 0.0;
    }

    // =========================================================================
    // 🔑 5. GESTIÓN DE CREDENCIALES (ACTUALIZADO)
    // =========================================================================

    private String getApiKey(String exchange) {
        return switch (exchange) {
            case "bybit_sub1" -> dotenv.get("BYBIT_SUB1_KEY");
            case "bybit_sub2" -> dotenv.get("BYBIT_SUB2_KEY"); // <--- Faltaba este
            case "bybit_sub3" -> dotenv.get("BYBIT_SUB3_KEY"); // <--- Y este
            case "binance" -> dotenv.get("BINANCE_KEY");
            case "mexc" -> dotenv.get("MEXC_KEY");
            case "kucoin" -> dotenv.get("KUCOIN_KEY");
            default -> null;
        };
    }

    private String getApiSecret(String exchange) {
        return switch (exchange) {
            case "bybit_sub1" -> dotenv.get("BYBIT_SUB1_SECRET");
            case "bybit_sub2" -> dotenv.get("BYBIT_SUB2_SECRET"); // <--- Faltaba este
            case "bybit_sub3" -> dotenv.get("BYBIT_SUB3_SECRET"); // <--- Y este
            case "binance" -> dotenv.get("BINANCE_SECRET");
            case "mexc" -> dotenv.get("MEXC_SECRET");
            case "kucoin" -> dotenv.get("KUCOIN_SECRET");
            default -> null;
        };
    }
}