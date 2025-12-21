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
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    // 💰 1. GESTIÓN DE SALDOS
    // =========================================================================
    public double fetchBalance(String exchange, String asset) {
        try {
            if (exchange.startsWith("bybit")) {
                String targetExchange = exchange.equals("bybit") ? "bybit_sub1" : exchange;
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
    // 🔫 2. ÓRDENES Y DATOS
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
    // 🕯️ 3. VELAS Y HISTORIAL (Para DynamicSelector)
    // =========================================================================
    public List<double[]> fetchCandles(String exchange, String pair, String interval, int limit) {
        List<double[]> candles = new ArrayList<>();
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

    // =========================================================================
    // 📊 4. GESTIÓN DE FEES (REAL & DINÁMICA)
    // =========================================================================

    /**
     * Consulta fee de trading dinámico. (Requerido por FeeManager)
     * @return double[] {MakerFee, TakerFee}
     */
    public double[] fetchDynamicTradingFee(String exchange, String pair) {
        // MVP: Retorna 0.1% estándar.
        // TODO: Implementar /api/v3/account o /v5/account/fee-rate para obtener VIP levels.
        return new double[]{0.001, 0.001};
    }

    /**
     * Obtiene el Fee de Retiro Real desde la cuenta del usuario.
     */
    public double fetchLiveWithdrawalFee(String exchange, String coin) {
        try {
            if (exchange.equalsIgnoreCase("binance")) {
                return getBinanceWithdrawFee(coin);
            } else if (exchange.toLowerCase().contains("bybit")) {
                return getBybitWithdrawFee(coin);
            } else if (exchange.equalsIgnoreCase("mexc")) {
                return getMexcWithdrawFee(coin);
            } else if (exchange.equalsIgnoreCase("kucoin")) {
                return getKucoinWithdrawFee(coin);
            }
            return -1.0;
        } catch (Exception e) {
            BotLogger.error("Error obteniendo Fee Retiro " + exchange + ": " + e.getMessage());
            return -1.0;
        }
    }

    // --- LÓGICA ESPECÍFICA BINANCE (SAPI V1) ---
    private double getBinanceWithdrawFee(String coin) throws Exception {
        // Validar Keys
        String apiKey = getApiKey("binance");
        String secret = getApiSecret("binance");
        if(apiKey == null || secret == null) return -1.0;

        String endpoint = "/sapi/v1/capital/config/getall";
        String queryString = "timestamp=" + System.currentTimeMillis() + "&recvWindow=5000";
        String signature = hmacSha256(queryString, secret);

        String url = BINANCE_URL + endpoint + "?" + queryString + "&signature=" + signature;

        Request request = new Request.Builder()
                .url(url)
                .header("X-MBX-APIKEY", apiKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return -1.0;
            String body = response.body().string();

            // Parseo Manual (String) para evitar estructuras complejas de Jackson para este caso
            if (!body.contains("\"coin\":\"" + coin + "\"")) return -1.0;

            int coinIndex = body.indexOf("\"coin\":\"" + coin + "\"");
            String coinBlock = body.substring(coinIndex);

            // Buscar coincidencias de red
            String networkSearch = "\"network\":\"" + coin + "\"";
            int netIndex = coinBlock.indexOf(networkSearch);
            if (netIndex == -1) netIndex = coinBlock.indexOf("\"withdrawFee\":"); // Fallback

            if (netIndex != -1) {
                String sub = coinBlock.substring(netIndex);
                int startFee = sub.indexOf("\"withdrawFee\":\"") + 15;
                int endFee = sub.indexOf("\"", startFee);
                return Double.parseDouble(sub.substring(startFee, endFee));
            }
        }
        return -1.0;
    }

    // --- LÓGICA ESPECÍFICA BYBIT (V5) ---
    private double getBybitWithdrawFee(String coin) throws Exception {
        // Reutilizamos buildSignedRequest que ya maneja Headers V5 correctamente
        String endpoint = "/v5/asset/coin/query-info?coin=" + coin;

        // Si usamos subcuenta "bybit_sub1", asegurar que tenemos permiso.
        // Si es cuenta main, usar "bybit". Por defecto usamos el conector configurado.
        Request request = buildSignedRequest("bybit_sub1", "GET", endpoint, "");

        if (request == null) return -1.0;

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return -1.0;
            String body = response.body().string();

            // Parseo Manual
            String feeTag = "\"withdrawFee\":\"";
            int feeIndex = body.indexOf(feeTag);

            if (feeIndex != -1) {
                int start = feeIndex + feeTag.length();
                int end = body.indexOf("\"", start);
                return Double.parseDouble(body.substring(start, end));
            }
        }
        return -1.0;
    }

    // =========================================================================
    // 🔐 5. FIRMA CRIPTOGRÁFICA
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

        String paramStr = "";
        if ("GET".equals(method)) {
            if (endpoint.contains("?")) {
                paramStr = endpoint.substring(endpoint.indexOf("?") + 1);
            }
        } else {
            paramStr = (jsonPayload == null) ? "" : jsonPayload;
        }

        String strToSign = timestamp + apiKey + recvWindow + paramStr;
        String signature = hmacSha256(strToSign, secretKey);

        String fullUrl = BYBIT_URL + endpoint;

        Request.Builder builder = new Request.Builder()
                .url(fullUrl)
                .header("X-BAPI-API-KEY", apiKey)
                .header("X-BAPI-SIGN", signature)
                .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                .header("X-BAPI-RECV-WINDOW", recvWindow)
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
    // ==========================================
    // 🏛️ IMPLEMENTACIÓN MEXC (AUDITADA V3)
    // ==========================================
// ==========================================
    // 🏛️ IMPLEMENTACIÓN MEXC (LIMPIA)
    // ==========================================
    private double getMexcWithdrawFee(String coin) throws Exception {
        String apiKey = getApiKey("mexc");
        String secret = getApiSecret("mexc");
        if (apiKey == null || secret == null) return -1.0;

        String endpoint = "/api/v3/capital/config/getall";
        String queryString = "timestamp=" + System.currentTimeMillis() + "&recvWindow=10000";
        String signature = hmacSha256(queryString, secret);
        String url = "https://api.mexc.com" + endpoint + "?" + queryString + "&signature=" + signature;

        // BotLogger.info("📡 CONECTANDO A MEXC... (" + url + ")"); <--- COMENTADO

        Request request = new Request.Builder()
                .url(url)
                .header("X-MEXC-APIKEY", apiKey)
                .header("Content-Type", "application/json")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return -1.0;

            // Leemos el body una sola vez
            String rawBody = response.body().string();
            // BotLogger.info("🔎 [MEXC RAW] " + coin + ": " + rawBody); <--- ¡SILENCIADO!

            JsonNode root = mapper.readTree(rawBody);
            if (root.isArray()) {
                for (JsonNode asset : root) {
                    if (asset.get("coin").asText().equalsIgnoreCase(coin)) {
                        JsonNode networks = asset.get("networkList");
                        if (networks != null && networks.isArray()) {
                            for (JsonNode net : networks) {
                                String netName = net.get("network").asText();
                                // Buscamos la red que coincida con la moneda (ej. SOL)
                                if (netName.contains(coin) || netName.equalsIgnoreCase(coin)) {
                                    return net.get("withdrawFee").asDouble();
                                }
                            }
                            if (networks.size() > 0) return networks.get(0).get("withdrawFee").asDouble();
                        }
                    }
                }
            }
        }
        return -1.0;
    }

    // ==========================================
    // 🏛️ IMPLEMENTACIÓN KUCOIN (LIMPIA)
    // ==========================================
    private double getKucoinWithdrawFee(String coin) throws Exception {
        // BotLogger.info("📡 CONECTANDO A KUCOIN..."); <--- COMENTADO

        String url = "https://api.kucoin.com/api/v2/currencies/" + coin;
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return -1.0;

            String rawBody = response.body().string();
            // BotLogger.info("🔎 [KUCOIN RAW] " + coin + ": " + rawBody); <--- ¡SILENCIADO!

            JsonNode root = mapper.readTree(rawBody);
            if (root.has("code") && root.get("code").asText().equals("200000")) {
                JsonNode data = root.get("data");
                if (data != null && data.has("chains")) {
                    JsonNode chains = data.get("chains");
                    double bestFee = 99999.0;
                    boolean found = false;
                    for (JsonNode chain : chains) {
                        if (chain.has("isWithdrawEnabled") && chain.get("isWithdrawEnabled").asBoolean()) {
                            double fee = chain.get("withdrawalMinFee").asDouble();
                            if (fee < bestFee) {
                                bestFee = fee;
                                found = true;
                            }
                        }
                    }
                    if (found) return bestFee;
                }
            }
        }
        return -1.0;
    }
    // =========================================================================
    // 🚀 6. BATCH FETCHING (OPTIMIZACIÓN SENIOR 10/10)
    // =========================================================================
    /**
     * Descarga TODOS los precios del exchange en 1 sola llamada HTTP.
     * Reduce el estrés de la API y evita bloqueos por Rate Limit.
     */
    public Map<String, Double> fetchAllPrices(String exchange) {
        Map<String, Double> marketPrices = new HashMap<>();
        String url = "";

        try {
            if (exchange.equalsIgnoreCase("binance") || exchange.equalsIgnoreCase("mexc")) {
                // V3 Standard: Devuelve array [{symbol: "BTCUSDT", price: "90000"}, ...]
                url = (exchange.equalsIgnoreCase("binance") ? BINANCE_URL : MEXC_URL) + "/api/v3/ticker/price";

                Request request = new Request.Builder().url(url).get().build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                marketPrices.put(node.get("symbol").asText(), node.get("price").asDouble());
                            }
                        }
                    }
                }
            } else if (exchange.toLowerCase().contains("bybit")) {
                // Bybit V5: category=spot
                url = BYBIT_URL + "/v5/market/tickers?category=spot";
                Request request = new Request.Builder().url(url).get().build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        if (root.get("retCode").asInt() == 0) {
                            JsonNode list = root.get("result").get("list");
                            for (JsonNode node : list) {
                                marketPrices.put(node.get("symbol").asText(), Double.parseDouble(node.get("lastPrice").asText()));
                            }
                        }
                    }
                }
            } else if (exchange.equalsIgnoreCase("kucoin")) {
                // Kucoin V1: /api/v1/market/allTickers
                url = KUCOIN_URL + "/api/v1/market/allTickers";
                Request request = new Request.Builder().url(url).get().build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        if (root.has("data") && root.get("data").has("ticker")) {
                            JsonNode tickers = root.get("data").get("ticker");
                            for (JsonNode node : tickers) {
                                // Kucoin usa guión (BTC-USDT), lo normalizamos a BTCUSDT para el mapa
                                String symbol = node.get("symbol").asText().replace("-", "");
                                double price = 0.0;
                                if (node.has("last")) price = node.get("last").asDouble(); // A veces es 'last', a veces 'buy'
                                marketPrices.put(symbol, price);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error("⚠️ Error Batch Fetch (" + exchange + "): " + e.getMessage());
        }
        return marketPrices;
    }
}