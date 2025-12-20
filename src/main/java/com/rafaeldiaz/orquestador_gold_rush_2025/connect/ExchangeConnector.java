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
import java.util.Base64;

public class ExchangeConnector {

    public interface EnvProvider {
        String get(String key);
    }

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final EnvProvider envProvider;

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

        Dotenv dotenvInstance = Dotenv.load();
        String currentIp = com.rafaeldiaz.orquestador_gold_rush_2025.utils.ExternalIpFetcher.getMyPublicIp();
        BotLogger.info("🌐 IP PÚBLICA DETECTADA: " + currentIp + " (Asegúrate de que esta IP esté en Bybit)");
        this.envProvider = dotenvInstance::get;
    }

    public ExchangeConnector(OkHttpClient client, EnvProvider envProvider) {
        this.client = client;
        this.mapper = new ObjectMapper();
        this.envProvider = envProvider;
    }

    // =========================================================================
    // 💰 1. GESTIÓN DE SALDOS (CON AJUSTE SENIOR)
    // =========================================================================
    public double fetchBalance(String exchange, String asset) {
        try {
            if (exchange.startsWith("bybit")) {
                String targetExchange = exchange.equals("bybit") ? "bybit_sub1" : exchange;
                // 🚀 ADVISOR: Parámetro accountType=UNIFIED mandatorio para subcuentas en V5
                String endpoint = "/v5/account/wallet-balance?accountType=UNIFIED&coin=" + asset;
                Request request = buildSignedRequest(targetExchange, "GET", endpoint, "");

                try (Response response = client.newCall(request).execute()) {
                    String body = response.body().string();
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(body);
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
                        } else {
                            BotLogger.error("❌ BYBIT BALANCE ERROR: " + body);
                        }
                    }
                }
            } else if (exchange.equals("kucoin")) {
                Request request = buildKucoinRequest("GET", "/api/v1/accounts?currency=" + asset, "");
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        if (root.get("code").asText().equals("200000")) {
                            for (JsonNode acc : root.get("data")) {
                                if (acc.get("currency").asText().equals(asset)) {
                                    return acc.get("available").asDouble();
                                }
                            }
                        }
                    }
                }
            } else if (exchange.equals("binance") || exchange.equals("mexc")) {
                Request request = buildBinanceMexcRequest(exchange, "/api/v3/account");
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        JsonNode balances = root.get("balances");
                        for (JsonNode b : balances) {
                            if (b.get("asset").asText().equals(asset)) {
                                return b.get("free").asDouble();
                            }
                        }
                    }
                }
            }
            return 0.0;
        } catch (Exception e) {
            BotLogger.error("Error leyendo balance " + exchange + ": " + e.getMessage());
            return 0.0;
        }
    }

    // =========================================================================
    // 🔫 2. ÓRDENES Y DATOS (STAY THE SAME)
    // =========================================================================
    public String placeOrder(String exchange, String pair, String side, String type, double qty, double price) {
        try {
            Request request = buildOrderRequest(exchange, pair, side, type, qty, price);
            if (request == null) return null;
            try (Response response = client.newCall(request).execute()) {
                String body = response.body().string();
                if (!response.isSuccessful()) { BotLogger.error("❌ Error Order " + exchange + ": " + body); return null; }
                JsonNode root = mapper.readTree(body);
                if (exchange.startsWith("bybit") && root.get("retCode").asInt() == 0) return root.get("result").get("orderId").asText();
                return null;
            }
        } catch (Exception e) { return null; }
    }

    public Request buildOrderRequest(String exchange, String pair, String side, String type, double qty, double price) {
        if (exchange.startsWith("bybit")) {
            String sideCap = side.equalsIgnoreCase("BUY") ? "Buy" : "Sell";
            String orderType = type.equalsIgnoreCase("LIMIT") ? "Limit" : "Market";
            String json = String.format("{\"category\":\"spot\",\"symbol\":\"%s\",\"side\":\"%s\",\"orderType\":\"%s\",\"qty\":\"%s\"%s}",
                    pair, sideCap, orderType, String.valueOf(qty), orderType.equals("Limit") ? ",\"price\":\"" + price + "\"" : "");
            return buildSignedRequest(exchange, "POST", "/v5/order/create", json);
        }
        return null;
    }

    public double fetchPrice(String exchange, String pair) {
        String cleanPair = pair.replace("-", "").toUpperCase();
        try {
            String url = switch (exchange) {
                case "binance" -> BINANCE_URL + "/api/v3/ticker/price?symbol=" + cleanPair;
                case "mexc" -> MEXC_URL + "/api/v3/ticker/price?symbol=" + cleanPair;
                case "kucoin" -> KUCOIN_URL + "/api/v1/market/orderbook/level1?symbol=" + (pair.contains("-") ? pair : pair.replace("USDT", "-USDT"));
                default -> BYBIT_URL + "/v5/market/tickers?category=spot&symbol=" + cleanPair;
            };
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return 0.0;
                JsonNode root = mapper.readTree(response.body().string());
                if (exchange.startsWith("bybit")) return Double.parseDouble(root.get("result").get("list").get(0).get("lastPrice").asText());
                if (exchange.equals("kucoin")) return root.get("data").get("price").asDouble();
                return root.get("price").asDouble();
            }
        } catch (Exception e) { return 0.0; }
    }

    // =========================================================================
    // 🔐 5. FIRMA CRIPTOGRÁFICA (EL BLINDAJE FINAL)
    // =========================================================================
// =========================================================================
    // 🔐 5. FIRMA CRIPTOGRÁFICA (EL BLINDAJE FINAL - REAL PROOF)
    // =========================================================================
    public Request buildSignedRequest(String exchange, String method, String endpoint, String jsonPayload) {
        String apiKey = getApiKey(exchange);
        String secretKey = getApiSecret(exchange);
        if (apiKey == null) {
            BotLogger.error("🔑 KEY MISSING: " + exchange);
            return null;
        }

        long timestamp = Instant.now().toEpochMilli();
        String recvWindow = "5000";

        // 🚀 MEJORA FACTUAL: Aseguramos la extracción limpia de parámetros para la firma
        String paramStr = "";
        if ("GET".equals(method)) {
            if (endpoint.contains("?")) {
                paramStr = endpoint.substring(endpoint.indexOf("?") + 1);
            }
        } else {
            paramStr = (jsonPayload == null) ? "" : jsonPayload;
        }

        // 🚀 FIRMA V5: Concatenación estricta exigida por Bybit
        String strToSign = timestamp + apiKey + recvWindow + paramStr;
        String signature = hmacSha256(strToSign, secretKey);

        // Construimos la URL completa antes del Builder para mayor limpieza
        String fullUrl = BYBIT_URL + endpoint;

        Request.Builder builder = new Request.Builder()
                .url(fullUrl)
                .header("X-BAPI-API-KEY", apiKey)
                .header("X-BAPI-SIGN", signature)
                .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                .header("X-BAPI-RECV-WINDOW", recvWindow)
                // 🚀 ADVISOR: Tipo "2" indica firma para UTA/Subcuentas y acceso a Assets
                .header("X-BAPI-SIGN-TYPE", "2")
                .header("Content-Type", "application/json");

        if ("POST".equals(method)) {
            RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json"));
            builder.post(body);
        } else {
            builder.get();
        }

        return builder.build();
    }
    private Request buildBinanceMexcRequest(String exchange, String endpoint) {
        long timestamp = Instant.now().toEpochMilli();
        String query = "timestamp=" + timestamp + "&recvWindow=5000";
        String signature = hmacSha256(query, getApiSecret(exchange));
        return new Request.Builder()
                .url((exchange.equals("binance") ? BINANCE_URL : MEXC_URL) + endpoint + "?" + query + "&signature=" + signature)
                .header(exchange.equals("mexc") ? "X-MEXC-APIKEY" : "X-MBX-APIKEY", getApiKey(exchange))
                .get().build();
    }

    private Request buildKucoinRequest(String method, String endpoint, String body) {
        long timestamp = System.currentTimeMillis();
        String signature = hmacSha256Base64(timestamp + method + endpoint + body, getApiSecret("kucoin"));
        return new Request.Builder()
                .url(KUCOIN_URL + endpoint)
                .header("KC-API-KEY", getApiKey("kucoin"))
                .header("KC-API-SIGN", signature)
                .header("KC-API-PASSPHRASE", envProvider.get("KUCOIN_PASSPHRASE"))
                .header("KC-API-TIMESTAMP", String.valueOf(timestamp))
                .header("KC-API-KEY-VERSION", "2")
                .get().build();
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private String hmacSha256Base64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private String getApiKey(String ex) {
        return switch (ex) {
            case "bybit", "bybit_sub1" -> envProvider.get("BYBIT_SUB1_KEY");
            case "binance" -> envProvider.get("BINANCE_KEY");
            case "mexc" -> envProvider.get("MEXC_KEY");
            case "kucoin" -> envProvider.get("KUCOIN_KEY");
            default -> envProvider.get(ex.toUpperCase() + "_KEY");
        };
    }

    private String getApiSecret(String ex) {
        return switch (ex) {
            case "bybit", "bybit_sub1" -> envProvider.get("BYBIT_SUB1_SECRET");
            case "binance" -> envProvider.get("BINANCE_SECRET");
            case "mexc" -> envProvider.get("MEXC_SECRET");
            case "kucoin" -> envProvider.get("KUCOIN_SECRET");
            default -> envProvider.get(ex.toUpperCase() + "_SECRET");
        };
    }
    // =========================================================================
    // 📊 3. GESTIÓN DINÁMICA DE FEES
    // =========================================================================

    /**
     * Consulta las tasas de trading (Taker/Maker) en tiempo real.
     * Requerido por FeeManager.java:88
     */
    public double[] fetchDynamicTradingFee(String exchange, String pair) {
        try {
            if (exchange.startsWith("bybit")) {
                // 🚀 ADVISOR: Usamos accountType=UNIFIED para evitar error 10010
                String endpoint = "/v5/account/fee-rate?category=spot&symbol=" + pair;
                Request request = buildSignedRequest(exchange, "GET", endpoint, "");

                try (Response response = client.newCall(request).execute()) {
                    JsonNode root = mapper.readTree(response.body().string());
                    if (root.get("retCode").asInt() == 0) {
                        JsonNode list = root.get("result").get("list");
                        if (list.isArray() && list.size() > 0) {
                            double taker = list.get(0).get("takerFeeRate").asDouble();
                            double maker = list.get(0).get("makerFeeRate").asDouble();
                            return new double[]{taker, maker};
                        }
                    }
                }
            }
            // Fallback estándar si no es Bybit o falla
            return new double[]{0.001, 0.001};
        } catch (Exception e) {
            return new double[]{0.001, 0.001};
        }
    }

    /**
     * Consulta el costo de retiro (gas/network fee) en tiempo real.
     * Requerido por FeeManager.java:105
     */
    public double fetchLiveWithdrawalFee(String exchange, String coin) {
        try {
            if (exchange.startsWith("bybit")) {
                // 🚀 ADVISOR: Este endpoint de ASSETS es el más sensible a la IP
                String endpoint = "/v5/asset/coin/query-info?coin=" + coin;
                Request request = buildSignedRequest(exchange, "GET", endpoint, "");

                try (Response response = client.newCall(request).execute()) {
                    JsonNode root = mapper.readTree(response.body().string());
                    if (root.get("retCode").asInt() == 0) {
                        JsonNode rows = root.get("result").get("rows");
                        if (rows.isArray() && rows.size() > 0) {
                            // Tomamos el primer network disponible
                            return rows.get(0).get("chains").get(0).get("withdrawFee").asDouble();
                        }
                    }
                }
            }
            return -1.0; // Indica error para que FeeManager use el modo pesimista
        } catch (Exception e) {
            return -1.0;
        }
    }
    /**
     * Consulta el historial de precios (Klines/Candlesticks).
     * Requerido por DynamicPairSelector para calcular ATR.
     */
    public java.util.List<double[]> fetchCandles(String exchange, String pair, String interval, int limit) {
        java.util.List<double[]> candles = new java.util.ArrayList<>();
        String cleanPair = pair.replace("-", "").toUpperCase();
        try {
            String url = switch (exchange) {
                case "binance" -> BINANCE_URL + "/api/v3/klines?symbol=" + cleanPair + "&interval=" + interval + "&limit=" + limit;
                case "mexc" -> MEXC_URL + "/api/v3/klines?symbol=" + cleanPair + "&interval=" + interval + "&limit=" + limit;
                case "kucoin" -> KUCOIN_URL + "/api/v1/market/candles?symbol=" + (pair.contains("-") ? pair : pair.replace("USDT", "-USDT")) + "&type=" + interval;
                default -> BYBIT_URL + "/v5/market/kline?category=spot&symbol=" + cleanPair + "&interval=" + (interval.equals("1h") ? "60" : interval) + "&limit=" + limit;
            };

            Request request = new Request.Builder().url(url).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return candles;
                JsonNode root = mapper.readTree(response.body().string());
                JsonNode list = exchange.startsWith("bybit") ? root.get("result").get("list") :
                        (exchange.equals("kucoin") ? root.get("data") : root);

                if (list != null && list.isArray()) {
                    for (JsonNode n : list) {
                        // Formato Bybit/Binance/MEXC: [time, open, high, low, close, ...]
                        // Formato Kucoin: [time, open, close, high, low, ...]
                        double high = exchange.equals("kucoin") ? n.get(3).asDouble() : n.get(2).asDouble();
                        double low = exchange.equals("kucoin") ? n.get(4).asDouble() : n.get(3).asDouble();
                        double close = exchange.equals("kucoin") ? n.get(2).asDouble() : n.get(4).asDouble();
                        candles.add(new double[]{high, low, close});
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error("❌ Error fetchCandles " + exchange + ": " + e.getMessage());
        }
        return candles;
    }
}