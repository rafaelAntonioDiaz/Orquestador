package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ExchangeConnector {

    public interface EnvProvider {
        String get(String key);
    }

    // 📦 ESTRUCTURA DE DATOS PARA EL LIBRO DE ÓRDENES
    public record OrderBook(List<double[]> bids, List<double[]> asks) {}

    private final Map<String, Long> exchangeRTT = new ConcurrentHashMap<>();
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final EnvProvider envProvider;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 500;
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
    // 💰 1. GESTIÓN DE SALDOS (BLINDADO v2.0)
    // =========================================================================
    public double fetchBalance(String exchange, String asset) {
        // Validación rápida para evitar llamadas tontas
        if (exchange == null || asset == null) return 0.0;

        try {
            // Lógica específica para Bybit
            if (exchange.startsWith("bybit")) {
                String targetExchange = exchange.equals("bybit") ? "bybit_sub1" : exchange;
                String endpoint = "/v5/account/wallet-balance?accountType=UNIFIED&coin=" + asset;

                // Construimos request
                Request request = buildSignedRequest(targetExchange, "GET", endpoint, "");
                if (request == null) return 0.0; // Si falla la firma

                try (Response response = client.newCall(request).execute()) {
                    // 🛡️ BLINDAJE 1: Respuesta Nula o Vacía
                    if (response.body() == null) return 0.0;
                    String body = response.body().string();

                    if (body.isEmpty()) {
                        // Log advertencia suave en lugar de SEVERE
                        BotLogger.warn("⚠️ Bybit devolvió respuesta vacía para " + asset + " (Posible Rate Limit). Asumiendo saldo 0.");
                        return 0.0;
                    }

                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(body);
                        if (root.path("retCode").asInt() == 0) {
                            JsonNode list = root.path("result").path("list");
                            if (list.isArray() && list.size() > 0) {
                                JsonNode coins = list.get(0).path("coin");
                                for (JsonNode c : coins) {
                                    if (c.path("coin").asText().equals(asset)) {
                                        return Double.parseDouble(c.path("walletBalance").asText("0"));
                                    }
                                }
                            }
                        }
                    } else {
                        // 🛡️ BLINDAJE 2: Manejo de Errores HTTP (429, 500)
                        BotLogger.warn("⚠️ HTTP " + response.code() + " leyendo balance " + exchange);
                    }
                }
            }
            // ... (El resto de bloques para Kucoin, Binance, Mexc se mantienen igual) ...
            else if (exchange.equals("binance") || exchange.equals("mexc")) {
                // ... (Código existente de Binance/Mexc) ...
                // Solo asegúrese de agregar check de body() != null
                Request request = buildBinanceMexcRequest(exchange, "/api/v3/account");
                try (Response response = client.newCall(request).execute()) {
                    if (response.body() != null && response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        JsonNode balances = root.path("balances");
                        for (JsonNode b : balances) {
                            if (b.path("asset").asText().equals(asset)) {
                                return b.path("free").asDouble();
                            }
                        }
                    }
                }
            }

            return 0.0;
        } catch (Exception e) {
            // 🥋 ZEN MODE (Singular): Silenciando el ruido también aquí
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : "Unknown";

            boolean isNoise = errorMsg.contains("empty String") ||
                    errorMsg.contains("timeout") ||
                    errorMsg.contains("SocketTimeout") ||
                    errorMsg.contains("502") ||
                    errorMsg.contains("504");

            if (!isNoise) {
                BotLogger.warn("⚠️ Fallo lectura balance (" + exchange + "): " + errorMsg);
            }
            return 0.0;
        }
    }

    // =========================================================================
    // 🔫 2. ÓRDENES DE FUEGO REAL (PLACE & VERIFY) - PRODUCCIÓN
    // =========================================================================


    /**
     * Ejecuta una orden y ESPERA la confirmación de la verdad.
     * NO devuelve hasta saber exactamente qué pasó.
     */
    public com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult placeOrder(String exchange, String pair, String side, String type, double qty, double price) {
        String orderId = null;
        try {
            // 1. DISPARAR LA ORDEN
            Request request = buildOrderRequest(exchange, pair, side, type, qty, price);
            if (request == null) throw new RuntimeException("Request malformado para " + exchange);

            try (Response response = client.newCall(request).execute()) {
                String body = response.body().string();

                // Manejo de rechazos HTTP
                if (!response.isSuccessful()) {
                    BotLogger.error("❌ RECHAZO HTTP (" + exchange + "): " + body);
                    // Devolvemos un resultado fallido vacío
                    return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult(
                            "ERROR", "FAILED", 0, 0, 0, 0, 0, "NONE");
                }

                JsonNode root = mapper.readTree(body);

                // Parsing específico para Bybit V5
                if (exchange.startsWith("bybit")) {
                    if (root.get("retCode").asInt() != 0) {
                        String msg = root.get("retMsg").asText();
                        BotLogger.error("❌ RECHAZO API BYBIT: " + msg);
                        return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult(
                                "ERROR", "FAILED", 0, 0, 0, 0, 0, "NONE");
                    }
                    orderId = root.get("result").get("orderId").asText();
                }
                // (Aquí agregaríamos Binance/Mexc si se usaran activamente)
            }

            if (orderId == null) throw new RuntimeException("No se obtuvo Order ID");

            // 2. VERIFICAR LA VERDAD (Polling inmediato)
            return fetchOrderResult(exchange, orderId, pair);

        } catch (Exception e) {
            BotLogger.error("💥 CRITICAL PLACE ORDER: " + e.getMessage());
            return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult(
                    "ERROR", "FAILED", 0, 0, 0, 0, 0, "NONE");
        }
    }

    /**
     * Consulta el estado post-mortem de la orden para llenar el certificado.
     */
    /**
     * Consulta el estado post-mortem de la orden para llenar el certificado.
     * Versión 5.0: Soporte completo para Average Price Real.
     */
    private com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult fetchOrderResult(String exchange, String orderId, String pair) {
        // Implementación BYBIT V5
        if (exchange.startsWith("bybit")) {
            // Breve espera para propagación en motor de matching
            try { Thread.sleep(200); } catch (InterruptedException e) {}

            String endpoint = "/v5/order/history?category=spot&orderId=" + orderId;
            Request request = buildSignedRequest(exchange, "GET", endpoint, "");

            try (Response response = executeWithRetry(request)) {
                JsonNode root = mapper.readTree(response.body().string());
                if (root.get("retCode").asInt() == 0) {
                    JsonNode list = root.get("result").get("list");
                    if (list.isArray() && list.size() > 0) {
                        JsonNode order = list.get(0);

                        String status = order.get("orderStatus").asText(); // "Filled", "PartiallyFilled"
                        double originalQty = Double.parseDouble(order.get("qty").asText());
                        double execQty = Double.parseDouble(order.get("cumExecQty").asText());

                        // 💰 EL DATO CLAVE: Valor total ejecutado en USDT (Quote Currency)
                        double execValue = Double.parseDouble(order.get("cumExecValue").asText());

                        double fee = Double.parseDouble(order.get("cumExecFee").asText());
                        double limitPrice = order.has("price") ? Double.parseDouble(order.get("price").asText()) : 0.0;

                        // Retornamos el nuevo OrderResult de 8 parámetros
                        return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult(
                                orderId,
                                status,
                                originalQty,
                                execQty,
                                execValue, // <--- Aquí va el cummulativeQuoteQty
                                limitPrice,
                                fee,
                                "UNK" // Fee Asset (Bybit no siempre lo da fácil aquí, lo dejamos UNK)
                        );
                    }
                }
            } catch (Exception e) {
                BotLogger.warn("⚠️ No se pudo verificar orden Bybit " + orderId + ": " + e.getMessage());
            }
        }

        // (Aquí iría la implementación de Binance/Mexc si la usáramos activamente)

        // Retorno de fallo / incertidumbre
        return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult(
                orderId, "UNKNOWN", 0, 0, 0, 0, 0, "NONE");
    }

    public Request buildOrderRequest(String exchange, String pair, String side, String type, double qty, double price) {
        // BYBIT V5 (JSON)
        if (exchange.toLowerCase().contains("bybit")) {
            String sideCap = side.equalsIgnoreCase("BUY") ? "Buy" : "Sell";
            String orderType = type.equalsIgnoreCase("LIMIT") ? "Limit" : "Market";
            String timeInForce = (type.equalsIgnoreCase("LIMIT")) ? ",\"timeInForce\":\"FOK\"" : "";

            // 🇺🇸 CURA PARA EL VIRUS DE LA COMA: Locale.US
            String priceStr = String.format(java.util.Locale.US, "%.8f", price);
            String qtyStr = String.format(java.util.Locale.US, "%.8f", qty);

            // Construcción directa
            String json = String.format(java.util.Locale.US,
                    "{\"category\":\"spot\",\"symbol\":\"%s\",\"side\":\"%s\",\"orderType\":\"%s\",\"qty\":\"%s\"%s%s}",
                    pair.replace("-", "").toUpperCase(),
                    sideCap,
                    orderType,
                    qtyStr,
                    orderType.equals("Limit") ? ",\"price\":\"" + priceStr + "\"" : "",
                    timeInForce);

            // 🚀 SIN LOGS, SOLO ACCIÓN
            return buildSignedRequest(exchange, "POST", "/v5/order/create", json);
        }
        // BINANCE / MEXC (Query String)
        if (exchange.equalsIgnoreCase("binance") || exchange.equalsIgnoreCase("mexc")) {
            String cleanPair = pair.replace("-", "").toUpperCase();

            // 🇺🇸 CURA PARA EL VIRUS DE LA COMA
            String qtyStr = String.format(java.util.Locale.US, "%.8f", qty);

            String query = "symbol=" + cleanPair + "&side=" + side.toUpperCase() + "&type=" + type.toUpperCase() + "&quantity=" + qtyStr;

            if (type.equalsIgnoreCase("LIMIT")) {
                String priceStr = String.format(java.util.Locale.US, "%.8f", price);
                query += "&price=" + priceStr + "&timeInForce=GTC";
            }

            long timestamp = Instant.now().toEpochMilli();
            query += "&timestamp=" + timestamp + "&recvWindow=5000";

            // Log para verificar que salen puntos
            BotLogger.info("📤 [" + exchange.toUpperCase() + "] Payload seguro: " + query);

            String signature = hmacSha256(query, getApiSecret(exchange));
            String finalUrl = (exchange.equalsIgnoreCase("binance") ? BINANCE_URL : MEXC_URL)
                    + "/api/v3/order?" + query + "&signature=" + signature;

            return new Request.Builder()
                    .url(finalUrl)
                    .header(exchange.equalsIgnoreCase("mexc") ? "X-MEXC-APIKEY" : "X-MBX-APIKEY", getApiKey(exchange))
                    .post(RequestBody.create("", MediaType.parse("application/x-www-form-urlencoded")))
                    .build();
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
            try (Response response = executeWithRetry(request)) {
                if (!response.isSuccessful()) return 0.0;
                JsonNode root = mapper.readTree(response.body().string());
                if (exchange.startsWith("bybit")) return Double.parseDouble(root.get("result").get("list").get(0).get("lastPrice").asText());
                if (exchange.equals("kucoin")) return root.get("data").get("price").asDouble();
                return root.get("price").asDouble();
            }
        } catch (Exception e) { return 0.0; }
    }
    // =========================================================================
    // 📖 2.5 VISIÓN DE AMPLIO ESPECTRO (ORDER BOOK)
    // =========================================================================
    /**
     * Descarga la profundidad del mercado (Bids y Asks) para calcular Slippage.
     */
    public OrderBook fetchOrderBook(String exchange, String pair, int depth) {
        depth = (depth == 0) ? BotConfig.BOOK_DEPTH : depth;
        String cleanPair = pair.replace("-", "").toUpperCase();
        List<double[]> bids = new ArrayList<>();
        List<double[]> asks = new ArrayList<>();

        try {
            String url = "";
            if (exchange.equalsIgnoreCase("binance") || exchange.equalsIgnoreCase("mexc")) {
                url = (exchange.equalsIgnoreCase("binance") ? BINANCE_URL : MEXC_URL)
                        + "/api/v3/depth?symbol=" + cleanPair + "&limit=" + depth;
            } else if (exchange.toLowerCase().contains("bybit")) {
                url = BYBIT_URL + "/v5/market/orderbook?category=spot&symbol=" + cleanPair + "&limit=" + depth;
            } else if (exchange.equalsIgnoreCase("kucoin")) {
                String kPair = pair.contains("-") ? pair : pair.replace("USDT", "-USDT");
                url = KUCOIN_URL + "/api/v1/market/orderbook/level2_20?symbol=" + kPair;
            }

            Request request = new Request.Builder().url(url).get().build();
            try (Response response = executeWithRetry(request)) {
                if (!response.isSuccessful()) return new OrderBook(bids, asks);

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode bNode = null, aNode = null;

                if (exchange.equalsIgnoreCase("binance") || exchange.equalsIgnoreCase("mexc")) {
                    bNode = root.get("bids");
                    aNode = root.get("asks");
                } else if (exchange.toLowerCase().contains("bybit")) {
                    bNode = root.get("result").get("b");
                    aNode = root.get("result").get("a");
                } else if (exchange.equalsIgnoreCase("kucoin")) {
                    bNode = root.get("data").get("bids");
                    aNode = root.get("data").get("asks");
                }

                if (bNode != null) for (JsonNode n : bNode) bids.add(new double[]{n.get(0).asDouble(), n.get(1).asDouble()});
                if (aNode != null) for (JsonNode n : aNode) asks.add(new double[]{n.get(0).asDouble(), n.get(1).asDouble()});
            }
        } catch (Exception e) {
            BotLogger.error("📚 Error Fetch OrderBook " + exchange + ": " + e.getMessage());
        }
        return new OrderBook(bids, asks);
    }

    /**
     * Calcula el precio promedio real simulando una compra/venta contra el libro.
     * @param book El libro de órdenes descargado.
     * @param side "BUY" (come del Ask) o "SELL" (come del Bid).
     * @param amount La cantidad de moneda base (ej. SOL) que quieres mover.
     * @return El precio promedio por unidad incluyendo Slippage.
     */
    public double calculateWeightedPrice(OrderBook book, String side, double amount) {
        List<double[]> orders = side.equalsIgnoreCase("BUY") ? book.asks() : book.bids();
        if (orders == null || orders.isEmpty()) return 0.0;

        double filledQty = 0.0;
        double totalCost = 0.0;

        for (double[] order : orders) {
            double price = order[0];
            double qty = order[1];
            double needed = amount - filledQty;

            if (qty >= needed) {
                totalCost += needed * price;
                filledQty += needed;
                break;
            } else {
                totalCost += qty * price;
                filledQty += qty;
            }
        }

        if (filledQty < amount * 0.9) return 0.0; // No hay suficiente liquidez
        return totalCost / filledQty;
    }

    // =========================================================================
    // 🕯️ 3. VELAS Y HISTORIAL
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
            try (Response response = executeWithRetry(request)) {
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
    // 📊 4. GESTIÓN DE FEES (TRADING REAL)
    // =========================================================================
    /**
     * Consulta la comisión de trading real (Maker/Taker) para un par específico.
     * Implementa endpoints reales para Bybit, Binance, MEXC y KuCoin.
     * @return double[] {takerFee, makerFee} (Ej: 0.001, 0.001)
     */
    public double[] fetchDynamicTradingFee(String exchange, String pair) {
        try {
            if (exchange.toLowerCase().contains("bybit")) {
                return getBybitTradingFee(pair);
            } else if (exchange.equalsIgnoreCase("binance")) {
                return getBinanceTradingFee(pair);
            } else if (exchange.equalsIgnoreCase("mexc")) {
                return getMexcTradingFee(pair);
            } else if (exchange.equalsIgnoreCase("kucoin")) {
                return getKucoinTradingFee(pair);
            }
            // Fallback seguro si no reconocemos el exchange
            return new double[]{0.001, 0.001};
        } catch (Exception e) {
            BotLogger.warn("⚠️ Error Fee Trading (" + exchange + "): " + e.getMessage() + ". Usando 0.1% Default.");
            return new double[]{0.001, 0.001};
        }
    }

    private double[] getBybitTradingFee(String pair) throws Exception {
        // Bybit V5: Fee Rate endpoint
        String cleanPair = pair.replace("-", "").toUpperCase();
        String endpoint = "/v5/account/fee-rate?category=spot&symbol=" + cleanPair;
        // Importante: Usamos una cuenta real (sub1) para firmar.
        // Si no tienes configurada sub1, asegúrate de usar las credenciales correctas.
        Request request = buildSignedRequest("bybit_sub1", "GET", endpoint, "");
        if (request == null) return new double[]{0.001, 0.001};

        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) return new double[]{0.001, 0.001};
            JsonNode root = mapper.readTree(response.body().string());

            if (root.path("retCode").asInt() == 0) {
                JsonNode list = root.path("result").path("list");
                if (list.isArray() && list.size() > 0) {
                    JsonNode data = list.get(0);
                    double taker = Double.parseDouble(data.path("takerFeeRate").asText("0.001"));
                    double maker = Double.parseDouble(data.path("makerFeeRate").asText("0.001"));
                    return new double[]{taker, maker};
                }
            }
        }
        return new double[]{0.001, 0.001};
    }

    private double[] getBinanceTradingFee(String pair) throws Exception {
        // Binance: Trade Fee endpoint (SAPI) da el fee específico del par (incluyendo descuento BNB)
        String cleanPair = pair.replace("-", "").toUpperCase();
        String queryString = "symbol=" + cleanPair + "&timestamp=" + System.currentTimeMillis() + "&recvWindow=5000";
        String secret = getApiSecret("binance");
        String apiKey = getApiKey("binance");

        if (secret == null || apiKey == null) return new double[]{0.001, 0.001};

        String signature = hmacSha256(queryString, secret);
        String url = BINANCE_URL + "/sapi/v1/asset/tradeFee?" + queryString + "&signature=" + signature;

        Request request = new Request.Builder()
                .url(url)
                .header("X-MBX-APIKEY", apiKey)
                .get()
                .build();

        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) return new double[]{0.001, 0.001};
            JsonNode root = mapper.readTree(response.body().string());
            // Binance devuelve un array directamente
            if (root.isArray() && root.size() > 0) {
                JsonNode data = root.get(0);
                double taker = data.path("takerCommission").asDouble(0.001);
                double maker = data.path("makerCommission").asDouble(0.001);
                return new double[]{taker, maker};
            }
        }
        return new double[]{0.001, 0.001};
    }

    private double[] getMexcTradingFee(String pair) throws Exception {
        // MEXC: Usamos Account info (/api/v3/account) que devuelve el tier global.
        // Endpoint específico de tradeFee en MEXC requiere permisos especiales a veces, account es más seguro.
        Request request = buildBinanceMexcRequest("mexc", "/api/v3/account");

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return new double[]{0.001, 0.001};
            JsonNode root = mapper.readTree(response.body().string());

            // MEXC a veces devuelve enteros (basis points) o decimales.
            if (root.has("takerCommission") && root.has("makerCommission")) {
                double takerRaw = root.path("takerCommission").asDouble();
                double makerRaw = root.path("makerCommission").asDouble();

                // Normalización: Si es > 1, asumimos basis points (ej 10 = 0.1%) y dividimos por 10000
                // Si es <= 1, asumimos decimal directo.
                double taker = (takerRaw > 1.0) ? takerRaw / 10000.0 : takerRaw;
                double maker = (makerRaw > 1.0) ? makerRaw / 10000.0 : makerRaw;

                // MEXC tiene promos de 0% maker a veces
                return new double[]{taker, maker};
            }
        }
        return new double[]{0.0, 0.0}; // Asumimos 0% maker/taker en MEXC si falla (riesgo calculado, son agresivos en fees)
    }

    private double[] getKucoinTradingFee(String pair) throws Exception {
        // Kucoin: Base Fee endpoint
        String kPair = pair.contains("-") ? pair : pair.replace("USDT", "-USDT");
        String endpoint = "/api/v1/base-fee?symbol=" + kPair;
        // Nota: Kucoin requiere firmar incluso para ver fees base específicos de tu cuenta
        Request request = buildKucoinRequest("GET", endpoint, "");

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return new double[]{0.001, 0.001};
            JsonNode root = mapper.readTree(response.body().string());

            if (root.path("code").asText().equals("200000")) {
                JsonNode data = root.path("data");
                double taker = data.path("takerFeeRate").asDouble(0.001);
                double maker = data.path("makerFeeRate").asDouble(0.001);
                return new double[]{taker, maker};
            }
        }
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

    private double getBinanceWithdrawFee(String coin) throws Exception {
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

        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) return -1.0;
            String body = response.body().string();
            if (!body.contains("\"coin\":\"" + coin + "\"")) return -1.0;

            int coinIndex = body.indexOf("\"coin\":\"" + coin + "\"");
            String coinBlock = body.substring(coinIndex);
            String networkSearch = "\"network\":\"" + coin + "\"";
            int netIndex = coinBlock.indexOf(networkSearch);
            if (netIndex == -1) netIndex = coinBlock.indexOf("\"withdrawFee\":");

            if (netIndex != -1) {
                String sub = coinBlock.substring(netIndex);
                int startFee = sub.indexOf("\"withdrawFee\":\"") + 15;
                int endFee = sub.indexOf("\"", startFee);
                return Double.parseDouble(sub.substring(startFee, endFee));
            }
        }
        return -1.0;
    }

    private double getBybitWithdrawFee(String coin) throws Exception {
        String endpoint = "/v5/asset/coin/query-info?coin=" + coin;
        Request request = buildSignedRequest("bybit_sub1", "GET", endpoint, "");
        if (request == null) return -1.0;

        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) return -1.0;
            String body = response.body().string();
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

    private double getMexcWithdrawFee(String coin) throws Exception {
        String apiKey = getApiKey("mexc");
        String secret = getApiSecret("mexc");
        if (apiKey == null || secret == null) return -1.0;

        String endpoint = "/api/v3/capital/config/getall";
        String queryString = "timestamp=" + System.currentTimeMillis() + "&recvWindow=10000";
        String signature = hmacSha256(queryString, secret);
        String url = "https://api.mexc.com" + endpoint + "?" + queryString + "&signature=" + signature;

        Request request = new Request.Builder()
                .url(url)
                .header("X-MEXC-APIKEY", apiKey)
                .header("Content-Type", "application/json")
                .get()
                .build();

        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) return -1.0;
            String rawBody = response.body().string();
            JsonNode root = mapper.readTree(rawBody);
            if (root.isArray()) {
                for (JsonNode asset : root) {
                    if (asset.get("coin").asText().equalsIgnoreCase(coin)) {
                        JsonNode networks = asset.get("networkList");
                        if (networks != null && networks.isArray()) {
                            for (JsonNode net : networks) {
                                String netName = net.get("network").asText();
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

    private double getKucoinWithdrawFee(String coin) throws Exception {
        String url = "https://api.kucoin.com/api/v2/currencies/" + coin;
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) return -1.0;
            String rawBody = response.body().string();
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
    // 🔐  FIRMA CRIPTOGRÁFICA
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
            if (endpoint.contains("?")) paramStr =
                    endpoint.substring(endpoint.indexOf("?") + 1);
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

    // =========================================================================
    // 🔐 CONSTRUCTOR DE PETICIONES (BINANCE - MEXC - KUCOIN
    // =========================================================================
    private Request buildBinanceMexcRequest(String exchange, String endpoint) {
        long timestamp = Instant.now().toEpochMilli();
        String query;

        // 🔧 LÓGICA DIFERENCIADA (Según Documentación Oficial)
        if (exchange.equalsIgnoreCase("mexc")) {
            // MEXC V3: recvWindow es opcional.
            // Para evitar errores 400 por desincronización de reloj,
            // probamos PRIMERO enviando SOLO el timestamp. Menos es más.
            query = "timestamp=" + timestamp;
        } else {
            // Binance: Estándar estricto
            query = "timestamp=" + timestamp + "&recvWindow=5000";
        }

        // La firma DEBE coincidir byte a byte con la query string
        String signature = hmacSha256(query, getApiSecret(exchange));

        String baseUrl = exchange.equals("binance") ? BINANCE_URL : MEXC_URL;
        String fullUrl = baseUrl + endpoint + "?" + query + "&signature=" + signature;

        return new Request.Builder()
                .url(fullUrl)
                .header(exchange.equals("mexc") ? "X-MEXC-APIKEY" : "X-MBX-APIKEY", getApiKey(exchange))
                .header("Content-Type", "application/json") // Buena práctica para MEXC
                .get()
                .build();
    }
    // =========================================================================
    // 🔐 CONSTRUCTOR KUCOIN (CORREGIDO PARA V2 - PASSPHRASE ENCRIPTADA)
    // =========================================================================
    private Request buildKucoinRequest(String method, String endpoint, String body) {
        long timestamp = System.currentTimeMillis();
        String apiSecret = getApiSecret("kucoin");
        String rawPassphrase = envProvider.get("KUCOIN_PASSPHRASE");

        // 1. FIRMA DE LA PETICIÓN (Endpoint + Body)
        String signature = hmacSha256Base64(timestamp + method + endpoint + body, apiSecret);

        // 2. ENCRIPTACIÓN DE LA PASSPHRASE
        // KuCoin V2 requiere que la passphrase se firme con el Secret y se pase a Base64
        String encryptedPassphrase = hmacSha256Base64(rawPassphrase, apiSecret);

        return new Request.Builder()
                .url(KUCOIN_URL + endpoint)
                .header("KC-API-KEY", getApiKey("kucoin"))
                .header("KC-API-SIGN", signature)
                .header("KC-API-PASSPHRASE", encryptedPassphrase) // <--- AHORA VA ENCRIPTADA
                .header("KC-API-TIMESTAMP", String.valueOf(timestamp))
                .header("KC-API-KEY-VERSION", "2") // Estamos forzando V2
                .header("Content-Type", "application/json")
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
        // Normalizamos a minúsculas para evitar errores
        return switch (ex.toLowerCase()) {
            case "bybit", "bybit_sub1" -> envProvider.get("BYBIT_SUB1_KEY");
            case "bybit_sub2" -> envProvider.get("BYBIT_SUB2_KEY"); // <--- NUEVO
            case "bybit_sub3" -> envProvider.get("BYBIT_SUB3_KEY"); // <--- NUEVO
            case "binance" -> envProvider.get("BINANCE_KEY");
            case "mexc" -> envProvider.get("MEXC_KEY");
            case "kucoin" -> envProvider.get("KUCOIN_KEY");
            default -> envProvider.get(ex.toUpperCase() + "_KEY");
        };
    }

    private String getApiSecret(String ex) {
        return switch (ex.toLowerCase()) {
            case "bybit", "bybit_sub1" -> envProvider.get("BYBIT_SUB1_SECRET");
            case "bybit_sub2" -> envProvider.get("BYBIT_SUB2_SECRET"); // <--- NUEVO
            case "bybit_sub3" -> envProvider.get("BYBIT_SUB3_SECRET"); // <--- NUEVO
            case "binance" -> envProvider.get("BINANCE_SECRET");
            case "mexc" -> envProvider.get("MEXC_SECRET");
            case "kucoin" -> envProvider.get("KUCOIN_SECRET");
            default -> envProvider.get(ex.toUpperCase() + "_SECRET");
        };
    }

    // =========================================================================
    // 🚀 BATCH FETCHING (OPTIMIZACIÓN SENIOR 10/10)
    // =========================================================================
    public Map<String, Double> fetchAllPrices(String exchange) {
        Map<String, Double> marketPrices = new HashMap<>();
        String url = "";

        try {
            if (exchange.equalsIgnoreCase("binance") || exchange.equalsIgnoreCase("mexc")) {
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
                url = KUCOIN_URL + "/api/v1/market/allTickers";
                Request request = new Request.Builder().url(url).get().build();
                try (Response response = executeWithRetry(request)) {
                    if (response.isSuccessful()) {
                        JsonNode root = mapper.readTree(response.body().string());
                        if (root.has("data") && root.get("data").has("ticker")) {
                            JsonNode tickers = root.get("data").get("ticker");
                            for (JsonNode node : tickers) {
                                String symbol = node.get("symbol").asText().replace("-", "");
                                double price = 0.0;
                                if (node.has("last")) price = node.get("last").asDouble();
                                else if (node.has("buy")) price = node.get("buy").asDouble();
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
    // =========================================================================
    // 🎯 2.6 PRECISIÓN QUIRÚRGICA (BID/ASK INSTANTÁNEO)
    // =========================================================================
    public double fetchBid(String exchange, String pair) {
        // Pedimos profundidad mínima (limit=1) para ser ultra-rápidos
        OrderBook book = fetchOrderBook(exchange, pair, 1);
        if (book.bids() != null && !book.bids().isEmpty()) {
            return book.bids().get(0)[0]; // El primer Bid es el más alto (mejor precio de venta para nosotros)
        }
        return fetchPrice(exchange, pair); // Fallback al Last Price si falla el libro
    }
    public double fetchAsk(String exchange, String pair) {
        OrderBook book = fetchOrderBook(exchange, pair, 1);
        if (book.asks() != null && !book.asks().isEmpty()) {
            return book.asks().get(0)[0]; // El primer Ask es el más bajo (mejor precio de compra para nosotros)
        }
        return fetchPrice(exchange, pair); // Fallback
    }
    // =========================================================================
    // 🛡️ NÚCLEO DE RESILIENCIA (MÉTODO PRIVADO NUEVO)
    // =========================================================================
    /**
     * Envuelve la llamada de red con lógica de reintentos y espera exponencial.
     * Maneja automáticamente errores 429 (Rate Limit) y 5xx.
     */
    private Response executeWithRetry(Request request) throws IOException {
     int attempt = 0;
     long backoff = INITIAL_BACKOFF_MS;
     IOException lastException = null;

     while (attempt < MAX_RETRIES) {
              // 🔥 CRONÓMETRO DE INICIO
             long startTime = System.currentTimeMillis();

             try {
                Response response = client.newCall(request).execute();

                          // 🔥 CÁLCULO DE RTT (Ida y Vuelta)
                long rtt = System.currentTimeMillis() - startTime;
                String host = request.url().host();
                          // Identificamos el exchange por el host y guardamos la latencia
                if (host.contains("binance")) exchangeRTT.put("binance", rtt);
                          else if (host.contains("bybit")) exchangeRTT.put("bybit", rtt);
                          else if (host.contains("mexc")) exchangeRTT.put("mexc", rtt);
                          else if (host.contains("kucoin")) exchangeRTT.put("kucoin", rtt);
                if (response.isSuccessful() || (response.code() >= 400 && response.code() != 429 && response.code() < 500)) {
                        exchangeRTT.put(host.split("\\.")[0], rtt);
                return response;
                }
                    // Si llegamos aquí, es un error recuperable (429 Rate Limit o 5xx Server Error)
                if (response.code() == 429) {
                    BotLogger.warn("🚦 RATE LIMIT DETECTADO (" + request.url().host() + "). Enfriando motores...");
                    backoff = 5000; // Castigo mayor (5s) si nos piden calmar
                } else {
                    BotLogger.warn("⚠️ Error Servidor " + response.code() + ". Reintentando...");
                }
                    response.close(); // Cerramos para limpiar recursos antes de reintentar

            } catch (IOException e) {
                 lastException = e;
                 BotLogger.warn("⚠️ Fallo de red (Intento " + (attempt + 1) + "/" + MAX_RETRIES + "): " + e.getMessage());
             }
             // Aumentamos contador y esperamos
             attempt++;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrumpido durante backoff");
            }
            backoff *= 2; // Backoff Exponencial: 500ms -> 1s -> 2s
        }
         throw (lastException != null) ? lastException : new IOException("Max retries exceeded for " + request.url());
    }

    // =========================================================================
    // 📏 7. NORMALIZACIÓN DE ÓRDENES (CALIBRADO PARA BYBIT V5 SPOT)
    // =========================================================================
    /**
     * Obtiene el "Paso Mínimo" de cantidad permitido por el exchange.
     * Ej: Para BTCUSDT en Binance es 0.00001.
     * Si intentas comprar 0.000015, te rechazará. Debes enviar 0.00001 o 0.00002.
     */
    // Caché en memoria
    private final Map<String, Double> stepSizeCache = new ConcurrentHashMap<>();
    public double getStepSize(String exchange, String pair) {
        String key = exchange + "_" + pair;
        if (stepSizeCache.containsKey(key)) return stepSizeCache.get(key);

        double stepSize = 0.01; // Valor seguro por defecto

        try {
            String cleanPair = pair.replace("-", "").toUpperCase();
            String url = "";

            if (exchange.equalsIgnoreCase("binance")) url = BINANCE_URL
                    + "/api/v3/exchangeInfo?symbol=" + cleanPair;
            else if (exchange.equalsIgnoreCase("mexc")) url = MEXC_URL
                    + "/api/v3/exchangeInfo?symbol=" + cleanPair;
            else if (exchange.toLowerCase().contains("bybit")) url = BYBIT_URL
                    + "/v5/market/instruments-info?category=spot&symbol=" + cleanPair;
            else if (exchange.equalsIgnoreCase("kucoin")) url = KUCOIN_URL
                    + "/api/v2/symbols/" + (pair.contains("-") ? pair
                    : pair.replace("USDT", "-USDT"));

            Request request = new Request.Builder().url(url).get().build();

            try (Response response = executeWithRetry(request)) {
                if (response.isSuccessful()) {
                    JsonNode root = mapper.readTree(response.body().string());

                    // --- BINANCE / MEXC ---
                    if (exchange.equalsIgnoreCase("binance")
                            || exchange.equalsIgnoreCase("mexc")) {
                        JsonNode symbols = root.get("symbols");
                        if (symbols != null && !symbols.isEmpty()) {
                            for (JsonNode f : symbols.get(0).get("filters")) {
                                if (f.get("filterType").asText().equals("LOT_SIZE")) {
                                    stepSize = Double.parseDouble(f.get("stepSize").asText());
                                    break;
                                }
                            }
                        }
                    }
                    // --- BYBIT V5 (CALIBRADO) ---
                    else if (exchange.toLowerCase().contains("bybit")) {
                        JsonNode result = root.get("result");
                        if (result != null && result.has("list")) {
                            JsonNode list = result.get("list");
                            if (list.isArray() && !list.isEmpty()) {
                                JsonNode item = list.get(0);
                                if (item.has("lotSizeFilter")) {
                                    JsonNode filter = item.get("lotSizeFilter");
                                    // Prioirdad 1: Spot usa 'basePrecision'
                                    if (filter.has("basePrecision")) {
                                        stepSize = Double.parseDouble(filter.get("basePrecision").asText());
                                    }
                                    // Prioridad 2: Futuros usa 'qtyStep' (por si acaso)
                                    else if (filter.has("qtyStep")) {
                                        stepSize = Double.parseDouble(filter.get("qtyStep").asText());
                                    }
                                }
                            }
                        }
                    }
                    // --- KUCOIN ---
                    else if (exchange.equalsIgnoreCase("kucoin")) {
                        JsonNode data = root.get("data");
                        if (data != null) {
                            JsonNode item = data.isArray() ? data.get(0) : data;
                            if (item.has("baseIncrement")) {
                                stepSize = Double.parseDouble(item.get("baseIncrement").asText());
                            }
                        }
                    }

                    BotLogger.info("📏 StepSize para " + pair
                            + " en " + exchange + ": " + String.format("%.8f", stepSize));
                    stepSizeCache.put(key, stepSize);
                    return stepSize;
                }
            }
        } catch (Exception e) {
            BotLogger.warn("⚠️ Error fetch stepSize "
                    + key + ": " + e.getMessage() + ". Usando Default 0.01");
        }
        return 0.01;
    }

// =========================================================================
// 🏦 GESTIÓN DE SALDOS (ARQUITECTURA SEPARADA)
// =========================================================================
public Map<String, Double> fetchBalances(String exchangeName) {
    String exchange = exchangeName.toLowerCase();

    // 1. RUTA BINANCE (Código Sagrado - NO TOCAR)
    if (exchange.equals("binance")) {
        return fetchBinanceBalances();
    }
    // 2. RUTA MEXC (Zona de Experimentos)
    else if (exchange.equals("mexc")) {
        return fetchMexcBalances();
    }
    // 3. RUTA BYBIT
    else if (exchange.contains("bybit")) {
        return fetchBybitBalances(exchange);
    }
    // 4. RUTA KUCOIN
    else if (exchange.equals("kucoin")) {
        return fetchKucoinBalances();
    }

    return new HashMap<>();
}

    // 🟢 BINANCE (ESTABLE)
    private Map<String, Double> fetchBinanceBalances() {
        Map<String, Double> balances = new HashMap<>();
        try {
            Request request = buildBinanceRequest("/api/v3/account"); // Usa su propio constructor
            try (Response response = client.newCall(request).execute()) {
                if (response.body() != null && response.isSuccessful()) {
                    JsonNode root = mapper.readTree(response.body().string());
                    JsonNode balNode = root.path("balances");
                    if (balNode.isArray()) {
                        for (JsonNode b : balNode) {
                            String asset = b.path("asset").asText();
                            double free = b.path("free").asDouble(0);

                            if (free > 0) balances.put(asset, free);
                        }
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error("⚠️ Error Binance Balance: " + e.getMessage());
        }
        return balances;
    }

    // 🟠 MEXC (EXPERIMENTAL - AISLADO)
    private Map<String, Double> fetchMexcBalances() {
        Map<String, Double> balances = new HashMap<>();
        try {
            // Usamos el constructor EXCLUSIVO para MEXC
            Request request = buildMexcRequest("/api/v3/account");

            try (Response response = client.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    BotLogger.warn("⚠️ [MEXC FAIL] Código: " + response.code() + " | Body: " + body);
                    return balances;
                }

                JsonNode root = mapper.readTree(body);
                JsonNode balNode = root.path("balances");

                if (balNode.isArray()) {
                    boolean foundSomething = false;
                    for (JsonNode b : balNode) {
                        String asset = b.path("asset").asText();
                        double free = b.path("free").asDouble(0);
                        double locked = b.path("locked").asDouble(0);

                        // 🕵️ SONDA ESPÍA MEXC (Sensibilidad Máxima)
                        if (free > 0 || locked > 0) {
                            foundSomething = true;
                            // BotLogger.info("🕵️ [SPY-MEXC] Activo: " + asset + " | Free: " + free);
                            if (free > 0) balances.put(asset, free);
                        }
                    }
                    if (!foundSomething) BotLogger.warn("⚠️ [SPY-MEXC] Conexión OK, pero saldo vacío (0 activos).");
                }
            }
        } catch (Exception e) {
            BotLogger.error("⚠️ Error MEXC Balance: " + e.getMessage());
        }
        return balances;
    }
// =========================================================================
    // 🔐 CONSTRUCTORES DE PETICIONES (SEPARADOS)
    // =========================================================================

    // ✅ BINANCE REQUEST (Original y Funcional)
    private Request buildBinanceRequest(String endpoint) {
        long timestamp = Instant.now().toEpochMilli();
        String query = "timestamp=" + timestamp + "&recvWindow=5000";
        String signature = hmacSha256(query, getApiSecret("binance"));
        String fullUrl = BINANCE_URL + endpoint + "?" + query + "&signature=" + signature;

        return new Request.Builder()
                .url(fullUrl)
                .header("X-MBX-APIKEY", getApiKey("binance"))
                .get()
                .build();
    }

    // ⚠️ MEXC REQUEST (Nueva Lógica Anti-Error 400)
    private Request buildMexcRequest(String endpoint) {
        long timestamp = Instant.now().toEpochMilli();

        // TRUCO: Ampliamos la ventana a 60 segundos (60000ms)
        // MEXC permite esto y soluciona desajustes de reloj local vs servidor.
        String query = "timestamp=" + timestamp + "&recvWindow=60000";

        String signature = hmacSha256(query, getApiSecret("mexc"));
        String fullUrl = MEXC_URL + endpoint + "?" + query + "&signature=" + signature;

        return new Request.Builder()
                .url(fullUrl)
                .header("X-MEXC-APIKEY", getApiKey("mexc"))
                .header("Content-Type", "application/json")
                .get()
                .build();
    }
    // 🔵 BYBIT
    private Map<String, Double> fetchBybitBalances(String exchange) {
        Map<String, Double> balances = new HashMap<>();
        try {
            String targetName = exchange.equals("bybit") ? "bybit_sub1" : exchange;
            Request request = buildSignedRequest(targetName, "GET", "/v5/account/wallet-balance?accountType=UNIFIED", "");
            try (Response response = executeWithRetry(request)) {
                if (response.body() != null) {
                    JsonNode root = mapper.readTree(response.body().string());
                    if (root.path("retCode").asInt() == 0) {
                        JsonNode list = root.path("result").path("list");
                        if (list.isArray() && list.size() > 0) {
                            for (JsonNode c : list.get(0).path("coin")) {
                                double val = Double.parseDouble(c.path("walletBalance").asText("0"));
                                if (val > 0) balances.put(c.path("coin").asText(), val);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { /* Silent */ }
        return balances;
    }

    // 🟣 KUCOIN
    private Map<String, Double> fetchKucoinBalances() {
        Map<String, Double> balances = new HashMap<>();
        try {
            // Construimos la petición
            Request request = buildKucoinRequest("GET", "/api/v1/accounts", "");

            // Ejecutamos (Usamos client directo para ver el error crudo sin reintentos que oculten la info)
            try (Response response = client.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "NO_BODY";

                // 🕵️ CASO 1: ERROR HTTP (Auth, IP, etc)
                if (!response.isSuccessful()) {
                    return balances;
                }

                JsonNode root = mapper.readTree(body);

                // 🕵️ CASO 2: ERROR LÓGICO API (Passphrase mal, etc)
                if (!root.path("code").asText().equals("200000")) {
                    BotLogger.warn("⚠️ [SPY-KUCOIN] API Error: " + root.path("msg").asText() + " (Code: " + root.path("code").asText() + ")");
                } else {
                    // 🕵️ CASO 3: ÉXITO - BUSCANDO ACTIVOS
                    JsonNode data = root.path("data");
                    boolean foundSomething = false;

                    if (data.isArray()) {
                        for (JsonNode acc : data) {
                            String currency = acc.path("currency").asText();
                            String type = acc.path("type").asText(); // "main" o "trade"
                            double available = acc.path("available").asDouble(0);
                            double balance = acc.path("balance").asDouble(0);

                            // SONDA: Reportar CUALQUIER cosa mayor a 0
                            if (balance > 0) {
                                foundSomething = true;
                            }

                            // Lógica de Agregación: Sumamos todo lo disponible
                            if (available > 0) balances.merge(currency, available, Double::sum);
                        }
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error("❌ [SPY-KUCOIN] Excepción Técnica: " + e.getMessage());
            e.printStackTrace();
        }
        return balances;
    }

    public long getRTT(String exchange) {
        return exchangeRTT.getOrDefault(exchange.toLowerCase(), -1L);
    }
}