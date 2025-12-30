package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.MetricsService;
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
        // 🚀 TUNING DE ALTO RENDIMIENTO (BARE METAL NETWORK)
        // 1. Dispatcher: Rompemos el límite de 5 peticiones por host.
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(200);       // Capacidad global del cliente
        dispatcher.setMaxRequestsPerHost(50); // ¡FUEGO LIBRE! Hasta 50 hilos contra Binance/Bybit a la vez.

        // 2. ConnectionPool: Mantenemos las conexiones TCP calientes.
        // Evita el "Handshake SSL" repetitivo que cuesta ~200ms cada vez.
        // Mantenemos 50 conexiones vivas por 5 minutos.
        ConnectionPool connectionPool = new ConnectionPool(50, 5, TimeUnit.MINUTES);

        this.client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                // 3. Timeouts agresivos pero justos
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                // 4. Optimizaciones extra
                .retryOnConnectionFailure(true)
                .build();

        this.mapper = new ObjectMapper();
        Dotenv dotenvInstance = Dotenv.load();

        // Log de IP (Sin cambios)
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
    // 🔫 2. ÓRDENES DE FUEGO REAL (PLACE & VERIFY) - PRODUCCIÓN
    // =========================================================================



    /**
     * Consulta el estado post-mortem de la orden para llenar el certificado.
     */
    /**
     * Consulta el estado post-mortem de la orden para llenar el certificado.
     * Versión 6.0
     */
    // =========================================================================
    // 🕵️ VERIFICACIÓN DE ÓRDENES (POLLING)
    // =========================================================================
    private com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult fetchOrderResult(String exchange, String orderId, String pair) {
        // Configuración de Polling
        int maxRetries = 20;
        long waitTime = 10;
        String lastStatus = "UNKNOWN";

        for (int i = 0; i < maxRetries; i++) {
            try {
                // 🟠 BYBIT V5
                if (exchange.startsWith("bybit")) {
                    String endpoint = "/v5/order/history?category=spot&orderId=" + orderId;
                    Request request = buildSignedRequest(exchange, "GET", endpoint, "");

                    // ⚡ USAMOS FAST_LANE: Si falla la red, lanzará excepción, el catch (abajo) la captura y el bucle for reintenta.
                    try (Response response = executeRequest(request, ExecutionMode.FAST_LANE)) {
                        if (response.isSuccessful() && response.body() != null) {
                            JsonNode root = mapper.readTree(response.body().string());
                            if (root.get("retCode").asInt() == 0) {
                                JsonNode list = root.get("result").get("list");
                                if (list.isArray() && list.size() > 0) {
                                    JsonNode order = list.get(0);
                                    lastStatus = order.get("orderStatus").asText();

                                    if (isFinalStatus(lastStatus)) {
                                        // Extracción de datos (igual que tu código original)
                                        double originalQty = Double.parseDouble(order.get("qty").asText());
                                        double execQty = Double.parseDouble(order.get("cumExecQty").asText());
                                        double execValue = Double.parseDouble(order.get("cumExecValue").asText());
                                        double fee = Double.parseDouble(order.get("cumExecFee").asText());
                                        double limitPrice = order.has("price") ? Double.parseDouble(order.get("price").asText()) : 0.0;

                                        return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult(orderId, lastStatus, originalQty, execQty, execValue, limitPrice, fee, "UNK");
                                    }
                                }
                            }
                        }
                    }
                }
                // 🟡 BINANCE / MEXC
                else if (exchange.equalsIgnoreCase("binance") || exchange.equalsIgnoreCase("mexc")) {
                    String baseUrl = exchange.equalsIgnoreCase("binance") ? BINANCE_URL : MEXC_URL;
                    String query = "symbol=" + pair.replace("-", "").toUpperCase() + "&orderId=" + orderId + "&timestamp=" + System.currentTimeMillis() + "&recvWindow=5000";
                    String signature = hmacSha256(query, getApiSecret(exchange));
                    String finalUrl = baseUrl + "/api/v3/order?" + query + "&signature=" + signature;

                    Request request = new Request.Builder()
                            .url(finalUrl)
                            .header(exchange.equalsIgnoreCase("mexc") ? "X-MEXC-APIKEY" : "X-MBX-APIKEY", getApiKey(exchange))
                            .get().build();

                    try (Response response = executeRequest(request, ExecutionMode.FAST_LANE)) {
                        if (response.isSuccessful() && response.body() != null) {
                            JsonNode root = mapper.readTree(response.body().string());
                            if (root.has("status")) {
                                lastStatus = root.get("status").asText();
                                if (isFinalStatus(lastStatus)) {
                                    double originalQty = root.get("origQty").asDouble();
                                    double execQty = root.get("executedQty").asDouble();
                                    double execValue = root.path("cummulativeQuoteQty").asDouble();
                                    if (execValue == 0 && execQty > 0) {
                                        double price = root.path("price").asDouble();
                                        execValue = execQty * price;
                                    }
                                    double price = root.path("price").asDouble();
                                    return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult(orderId, lastStatus, originalQty, execQty, execValue, price, 0.0, "UNK");
                                }
                            }
                        }
                    }
                }
                // 🟢 KUCOIN
                else if (exchange.equalsIgnoreCase("kucoin")) {
                    String endpoint = "/api/v1/orders/" + orderId;
                    Request request = buildKucoinRequest("GET", endpoint, "");

                    try (Response response = executeRequest(request, ExecutionMode.FAST_LANE)) {
                        if (response.isSuccessful() && response.body() != null) {
                            JsonNode root = mapper.readTree(response.body().string());
                            if (root.path("code").asText().equals("200000")) {
                                JsonNode data = root.get("data");
                                boolean isActive = data.get("isActive").asBoolean();
                                boolean cancelExist = data.get("cancelExist").asBoolean();

                                if (!isActive) {
                                    lastStatus = cancelExist ? "CANCELLED" : "FILLED";
                                    double originalQty = data.get("size").asDouble();
                                    double execQty = data.get("dealSize").asDouble();
                                    double execValue = data.get("dealFunds").asDouble();
                                    double price = data.get("price").asDouble();
                                    double fee = data.get("fee").asDouble();
                                    return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult(orderId, lastStatus, originalQty, execQty, execValue, price, fee, "UNK");
                                } else {
                                    lastStatus = "NEW";
                                    if (data.get("dealSize").asDouble() > 0) lastStatus = "PARTIALLY_FILLED";
                                }
                            }
                        }
                    }
                }

                BotLogger.warn("🔄 Polling orden " + orderId + " (" + (i+1) + "/" + maxRetries + ") Status: " + lastStatus);
                Thread.sleep(waitTime);
                waitTime += 10;
                if (waitTime > 100) waitTime = 100;

            } catch (Exception e) {
                BotLogger.warn("⚠️ Error polling orden " + orderId + ": " + e.getMessage());
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
        }

        BotLogger.error("❌ TIMEOUT verificando orden " + orderId + ". Último estado: " + lastStatus);
        return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult(orderId, lastStatus, 0, 0, 0, 0, 0, "NONE");
    }

    // Helper para saber si dejar de insistir
    // Helper universal para detener el Polling
    // Helper optimizado con Switch Expression (Java 14+)
    private boolean isFinalStatus(String status) {
        if (status == null) return false; // Protección contra NPE

        return switch (status.toUpperCase()) {
            case "FILLED",
                 "CANCELED",
                 "CANCELLED", // Kucoin a veces usa doble L
                 "REJECTED",
                 "EXPIRED" -> true;
            default -> false;
        };
    }
    public Request buildOrderRequest(String exchange, String pair,
                                     String side, String type, double qty, double price) {

        // 🧹 Preparación de datos comunes (Fast Path)
        String cleanPair = pair.replace("-", "").toUpperCase();
        String sideCap = side.equalsIgnoreCase("BUY") ? "Buy" : "Sell";
        // [OPTIMIZACIÓN] Usamos Locale.US para asegurar el punto decimal
        String qtyStr = String.format(java.util.Locale.US, "%.8f", qty);
        String priceStr = String.format(java.util.Locale.US, "%.8f", price);

        // [FOK UPDATE] Detección de intención
        boolean isFOK = type.equalsIgnoreCase("LIMIT_FOK");
        // Si es LIMIT_FOK o LIMIT normal, para la API es un "Limit"
        String apiType = type.toUpperCase().contains("LIMIT") ? "Limit" : "Market";

        // 🚀 SWITCH EXPRESSION (Java 21+ Style)
        return switch (exchange.toLowerCase()) {

            // 🟠 CASO 1: BYBIT V5
            case String s when s.contains("bybit") -> {
                String jsonPayload;
                if (apiType.equalsIgnoreCase("Limit")) {
                    // [FOK UPDATE] Bybit usa timeInForce: "FOK"
                    String tif = isFOK ? "FOK" : "GTC";
                    jsonPayload = """
                {
                    "category": "spot",
                    "symbol": "%s",
                    "side": "%s",
                    "orderType": "Limit",
                    "qty": "%s",
                    "price": "%s",
                    "timeInForce": "%s"
                }
                """.formatted(cleanPair, sideCap, qtyStr, priceStr, tif);
                } else {
                    jsonPayload = """
                {
                    "category": "spot",
                    "symbol": "%s",
                    "side": "%s",
                    "orderType": "Market",
                    "qty": "%s"
                }
                """.formatted(cleanPair, sideCap, qtyStr);
                }
                yield buildSignedRequest(exchange, "POST", "/v5/order/create", jsonPayload);
            }

            // 🟡 CASO 2: BINANCE & MEXC
            case "binance", "mexc" -> {
                String binanceType = apiType.toUpperCase(); // API requiere UPPERCASE (LIMIT, MARKET)

                // Template base
                String queryBase = "symbol=%s&side=%s&type=%s&quantity=%s".formatted(
                        cleanPair, side.toUpperCase(), binanceType, qtyStr
                );
                StringBuilder query = new StringBuilder(queryBase);

                if (binanceType.equals("LIMIT")) {
                    // [FOK UPDATE] Binance/MEXC usan timeInForce en el query param
                    String tif = isFOK ? "FOK" : "GTC";
                    query.append("&price=").append(priceStr).append("&timeInForce=").append(tif);
                }

                long timestamp = java.time.Instant.now().toEpochMilli();

                // Pequeña diferencia interna manejada con if simple
                if (exchange.equalsIgnoreCase("mexc")) {
                    query.append("&timestamp=").append(timestamp);
                } else {
                    query.append("&timestamp=").append(timestamp).append("&recvWindow=5000");
                }

                String signature = hmacSha256(query.toString(), getApiSecret(exchange));
                String baseUrl = exchange.equalsIgnoreCase("binance") ? BINANCE_URL : MEXC_URL;
                // Usamos String.format clásico para evitar líos con % en URLs
                String finalUrl = baseUrl + "/api/v3/order?" + query + "&signature=" + signature;

                yield new Request.Builder()
                        .url(finalUrl)
                        .header(exchange.equalsIgnoreCase("mexc") ? "X-MEXC-APIKEY" : "X-MBX-APIKEY", getApiKey(exchange))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .post(okhttp3.RequestBody.create("", okhttp3.MediaType.parse("application/x-www-form-urlencoded")))
                        .build();
            }

            // 🟢 CASO 3: KUCOIN
            case "kucoin" -> {
                String kPair = pair.contains("-") ? pair : pair.replace("USDT", "-USDT").replace("USDC", "-USDC");
                String clientOid = java.util.UUID.randomUUID().toString();
                String jsonPayload;

                if (apiType.equalsIgnoreCase("Limit")) {
                    // [FOK UPDATE] KuCoin usa timeInForce dentro del JSON
                    String tif = isFOK ? "FOK" : "GTC";
                    jsonPayload = """
                {
                    "clientOid": "%s",
                    "side": "%s",
                    "symbol": "%s",
                    "type": "limit",
                    "price": "%s",
                    "size": "%s",
                    "timeInForce": "%s"
                }
                """.formatted(clientOid, side.toLowerCase(), kPair, priceStr, qtyStr, tif);
                } else {
                    jsonPayload = """
                {
                    "clientOid": "%s",
                    "side": "%s",
                    "symbol": "%s",
                    "type": "market",
                    "size": "%s"
                }
                """.formatted(clientOid, side.toLowerCase(), kPair, qtyStr);
                }
                yield buildKucoinRequest("POST", "/api/v1/orders", jsonPayload);
            }

            // ⚪ DEFAULT
            default -> {
                BotLogger.error("❌ Exchange no soportado para órdenes: " + exchange);
                yield null;
            }
        };
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

            // ⚡ FAST LANE
            try (Response response = executeRequest(request, ExecutionMode.FAST_LANE)) {
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
            // 🔇 Silencio en error de book
        }
        return new OrderBook(bids, asks);
    }

    public Map<String, Double> fetchAllPrices(String exchange) {
        Map<String, Double> marketPrices = new HashMap<>();
        String url = "";
        try {
            // Construcción de URL (Igual que antes)
            if (exchange.equalsIgnoreCase("binance") || exchange.equalsIgnoreCase("mexc")) {
                url = (exchange.equalsIgnoreCase("binance") ? BINANCE_URL : MEXC_URL) + "/api/v3/ticker/price";
            } else if (exchange.toLowerCase().contains("bybit")) {
                url = BYBIT_URL + "/v5/market/tickers?category=spot";
            } else if (exchange.equalsIgnoreCase("kucoin")) {
                url = KUCOIN_URL + "/api/v1/market/allTickers";
            }

            if (url.isEmpty()) return marketPrices;
            Request request = new Request.Builder().url(url).get().build();

            // ⚡ FAST LANE: Batch fetch es pesado, pero si falla, no queremos bloquear 3s.
            try (Response response = executeRequest(request, ExecutionMode.FAST_LANE)) {
                JsonNode root = mapper.readTree(response.body().string());

                // Parsing (Simplificado para brevedad, lógica idéntica a tu original)
                if (exchange.equalsIgnoreCase("binance") || exchange.equalsIgnoreCase("mexc")) {
                    if (root.isArray()) for (JsonNode n : root) marketPrices.put(n.get("symbol").asText(), n.get("price").asDouble());
                } else if (exchange.toLowerCase().contains("bybit")) {
                    if (root.get("retCode").asInt() == 0) {
                        for (JsonNode n : root.get("result").get("list")) marketPrices.put(n.get("symbol").asText(), Double.parseDouble(n.get("lastPrice").asText()));
                    }
                } else if (exchange.equalsIgnoreCase("kucoin")) {
                    if (root.has("data") && root.get("data").has("ticker")) {
                        for (JsonNode n : root.get("data").get("ticker")) {
                            double p = n.has("last") ? n.get("last").asDouble() : (n.has("buy") ? n.get("buy").asDouble() : 0.0);
                            marketPrices.put(n.get("symbol").asText().replace("-", ""), p);
                        }
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error("⚠️ Error Batch Fetch (" + exchange + "): " + e.getMessage());
        }
        return marketPrices;
    }

    public double fetchBid(String exchange, String pair) {
        OrderBook book = fetchOrderBook(exchange, pair, 1);
        if (book.bids() != null && !book.bids().isEmpty()) return book.bids().get(0)[0];
        return fetchPrice(exchange, pair);
    }

    public double fetchAsk(String exchange, String pair) {
        OrderBook book = fetchOrderBook(exchange, pair, 1);
        if (book.asks() != null && !book.asks().isEmpty()) return book.asks().get(0)[0];
        return fetchPrice(exchange, pair);
    }

    // =========================================================================
    // 🛡️ PUBLIC METHODS: TRADING & ACCOUNT (HEAVY DUTY)
    // =========================================================================

    public com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult placeOrder(String exchange, String pair, String side, String type, double qty, double price) {
        String orderId = null;
        try {
            Request request = buildOrderRequest(exchange, pair, side, type, qty, price);
            if (request == null) throw new RuntimeException("Request malformado " + exchange);

            // 🛡️ HEAVY DUTY: Reintentar si hay fallo de red al enviar orden es CRÍTICO
            try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
                String body = response.body().string();
                if (!response.isSuccessful()) {
                    BotLogger.error("❌ RECHAZO HTTP (" + exchange + "): " + body);
                    return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult("ERROR", "FAILED", 0, 0, 0, 0, 0, "NONE");
                }

                JsonNode root = mapper.readTree(body);
                // Parsing específico Bybit
                if (exchange.startsWith("bybit")) {
                    if (root.get("retCode").asInt() != 0) {
                        BotLogger.error("❌ RECHAZO API BYBIT: " + root.get("retMsg").asText());
                        return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult("ERROR", "FAILED", 0, 0, 0, 0, 0, "NONE");
                    }
                    orderId = root.get("result").get("orderId").asText();
                } else if (exchange.equalsIgnoreCase("binance") || exchange.equalsIgnoreCase("mexc")) {
                    // Binance/MEXC suelen dar orderId en root
                    if (root.has("orderId")) orderId = root.get("orderId").asText();
                } else if (exchange.equalsIgnoreCase("kucoin")) {
                    if (root.get("code").asText().equals("200000")) orderId = root.get("data").get("orderId").asText();
                }
            }

            if (orderId == null) throw new RuntimeException("No Order ID received");

            // Polling inmediato para resultado
            return fetchOrderResult(exchange, orderId, pair);

        } catch (Exception e) {
            BotLogger.error("💥 CRITICAL PLACE ORDER: " + e.getMessage());
            return new com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult("ERROR", "FAILED", 0, 0, 0, 0, 0, "NONE");
        }
    }

    public double fetchBalance(String exchange, String asset) {
        if (exchange == null || asset == null) return 0.0;
        try {
            // Implementación simplificada reutilizando lógica existente
            Map<String, Double> balances = fetchBalances(exchange);
            return balances.getOrDefault(asset, 0.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    // 🛡️ FETCH BALANCES (HEAVY DUTY implícito en llamadas internas)
    public Map<String, Double> fetchBalances(String exchangeName) {
        return switch (exchangeName.toLowerCase()) {
            case "binance" -> fetchBinanceBalances();
            case "mexc"    -> fetchMexcBalances();
            case "kucoin"  -> fetchKucoinBalances();
            case String s when s.contains("bybit") -> fetchBybitBalances(s);
            default -> new HashMap<>();
        };
    }

    // =========================================================================
    // 🔌 IMPLEMENTACIONES PRIVADAS DE BALANCES
    // =========================================================================

    private Map<String, Double> fetchBinanceBalances() {
        Map<String, Double> balances = new HashMap<>();
        try {
            Request request = buildBinanceRequest("/api/v3/account");
            // 🛡️ HEAVY DUTY
            try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
                if (response.body() != null) {
                    JsonNode root = mapper.readTree(response.body().string());
                    for (JsonNode b : root.path("balances")) {
                        double free = b.path("free").asDouble(0);
                        if (free > 0) balances.put(b.path("asset").asText(), free);
                    }
                }
            }
        } catch (Exception e) { BotLogger.error("⚠️ Binance Balance Error: " + e.getMessage()); }
        return balances;
    }

    private Map<String, Double> fetchMexcBalances() {
        Map<String, Double> balances = new HashMap<>();
        try {
            Request request = buildMexcRequest("/api/v3/account");
            try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
                if (response.body() != null) {
                    JsonNode root = mapper.readTree(response.body().string());
                    for (JsonNode b : root.path("balances")) {
                        double free = b.path("free").asDouble(0);
                        if (free > 0) balances.put(b.path("asset").asText(), free);
                    }
                }
            }
        } catch (Exception e) { BotLogger.error("⚠️ MEXC Balance Error: " + e.getMessage()); }
        return balances;
    }

    private Map<String, Double> fetchBybitBalances(String exchange) {
        Map<String, Double> balances = new HashMap<>();
        try {
            String targetName = exchange.equals("bybit") ? "bybit_sub1" : exchange;
            Request request = buildSignedRequest(targetName, "GET", "/v5/account/wallet-balance?accountType=UNIFIED", "");
            try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
                if (response.body() != null) {
                    JsonNode root = mapper.readTree(response.body().string());
                    if (root.path("retCode").asInt() == 0) {
                        for (JsonNode c : root.path("result").path("list").get(0).path("coin")) {
                            double val = Double.parseDouble(c.path("walletBalance").asText("0"));
                            if (val > 0) balances.put(c.path("coin").asText(), val);
                        }
                    }
                }
            }
        } catch (Exception e) { /* Silent */ }
        return balances;
    }

    private Map<String, Double> fetchKucoinBalances() {
        Map<String, Double> balances = new HashMap<>();
        try {
            Request request = buildKucoinRequest("GET", "/api/v1/accounts", "");
            try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
                if (response.body() != null) {
                    JsonNode root = mapper.readTree(response.body().string());
                    if (root.path("code").asText().equals("200000")) {
                        for (JsonNode acc : root.path("data")) {
                            double avail = acc.path("available").asDouble(0);
                            if (avail > 0) balances.merge(acc.path("currency").asText(), avail, Double::sum);
                        }
                    }
                }
            }
        } catch (Exception e) { BotLogger.error("⚠️ Kucoin Balance Error: " + e.getMessage()); }
        return balances;
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
    // 🕯️ 3. VELAS (CANDLES) - USAMOS HEAVY DUTY
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

            // 🛡️ HEAVY DUTY: Queremos asegurar que la data llegue para el análisis técnico
            try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
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
// =========================================================================
    // 📊 4. GESTIÓN DE FEES (TRADING) - HEAVY DUTY
    // =========================================================================
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
            return new double[]{0.001, 0.001};
        } catch (Exception e) {
            BotLogger.warn("⚠️ Error Fee Trading (" + exchange + "): " + e.getMessage() + ". Usando 0.1% Default.");
            return new double[]{0.001, 0.001};
        }
    }
    private double[] getBybitTradingFee(String pair) throws Exception {
        String cleanPair = pair.replace("-", "").toUpperCase();
        String endpoint = "/v5/account/fee-rate?category=spot&symbol=" + cleanPair;
        Request request = buildSignedRequest("bybit_sub1", "GET", endpoint, "");
        if (request == null) return new double[]{0.001, 0.001};

        try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
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
        String cleanPair = pair.replace("-", "").toUpperCase();
        String queryString = "symbol=" + cleanPair + "&timestamp=" + System.currentTimeMillis() + "&recvWindow=5000";
        String secret = getApiSecret("binance");
        String apiKey = getApiKey("binance");
        if (secret == null || apiKey == null) return new double[]{0.001, 0.001};

        String signature = hmacSha256(queryString, secret);
        String url = BINANCE_URL + "/sapi/v1/asset/tradeFee?" + queryString + "&signature=" + signature;

        Request request = new Request.Builder().url(url).header("X-MBX-APIKEY", apiKey).get().build();

        try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
            if (!response.isSuccessful()) return new double[]{0.001, 0.001};
            JsonNode root = mapper.readTree(response.body().string());
            if (root.isArray() && root.size() > 0) {
                JsonNode data = root.get(0);
                return new double[]{data.path("takerCommission").asDouble(0.001), data.path("makerCommission").asDouble(0.001)};
            }
        }
        return new double[]{0.001, 0.001};
    }

    private double[] getMexcTradingFee(String pair) throws Exception {
        Request request = buildBinanceMexcRequest("mexc", "/api/v3/account");
        try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
            if (!response.isSuccessful()) return new double[]{0.001, 0.001};
            JsonNode root = mapper.readTree(response.body().string());
            if (root.has("takerCommission") && root.has("makerCommission")) {
                double takerRaw = root.path("takerCommission").asDouble();
                double makerRaw = root.path("makerCommission").asDouble();
                double taker = (takerRaw > 1.0) ? takerRaw / 10000.0 : takerRaw;
                double maker = (makerRaw > 1.0) ? makerRaw / 10000.0 : makerRaw;
                return new double[]{taker, maker};
            }
        }
        return new double[]{0.0, 0.0};
    }

    private double[] getKucoinTradingFee(String pair) throws Exception {
        String kPair = pair.contains("-") ? pair : pair.replace("USDT", "-USDT");
        String endpoint = "/api/v1/base-fee?symbol=" + kPair;
        Request request = buildKucoinRequest("GET", endpoint, "");

        try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
            if (!response.isSuccessful()) return new double[]{0.001, 0.001};
            JsonNode root = mapper.readTree(response.body().string());
            if (root.path("code").asText().equals("200000")) {
                JsonNode data = root.path("data");
                return new double[]{data.path("takerFeeRate").asDouble(0.001), data.path("makerFeeRate").asDouble(0.001)};
            }
        }
        return new double[]{0.001, 0.001};
    }

    // =========================================================================
    // 💸 FEES DE RETIRO (HEAVY DUTY)
    // =========================================================================
    public double fetchLiveWithdrawalFee(String exchange, String coin) {
        try {
            if (exchange.equalsIgnoreCase("binance")) return getBinanceWithdrawFee(coin);
            else if (exchange.toLowerCase().contains("bybit")) return getBybitWithdrawFee(coin);
            else if (exchange.equalsIgnoreCase("mexc")) return getMexcWithdrawFee(coin);
            else if (exchange.equalsIgnoreCase("kucoin")) return getKucoinWithdrawFee(coin);
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

        Request request = new Request.Builder().url(url).header("X-MBX-APIKEY", apiKey).get().build();
        try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
            if (!response.isSuccessful()) return -1.0;
            String body = response.body().string();
            // Lógica de parsing manual original (funciona bien)
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
        try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
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

        Request request = new Request.Builder().url(url).header("X-MEXC-APIKEY", apiKey).header("Content-Type", "application/json").get().build();

        try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
            if (!response.isSuccessful()) return -1.0;
            JsonNode root = mapper.readTree(response.body().string());
            if (root.isArray()) {
                for (JsonNode asset : root) {
                    if (asset.get("coin").asText().equalsIgnoreCase(coin)) {
                        JsonNode networks = asset.get("networkList");
                        if (networks != null && networks.isArray()) {
                            for (JsonNode net : networks) {
                                String netName = net.get("network").asText();
                                if (netName.contains(coin) || netName.equalsIgnoreCase(coin)) return net.get("withdrawFee").asDouble();
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
        try (Response response = executeRequest(request, ExecutionMode.HEAVY_DUTY)) {
            if (!response.isSuccessful()) return -1.0;
            JsonNode root = mapper.readTree(response.body().string());
            if (root.has("code") && root.get("code").asText().equals("200000")) {
                JsonNode data = root.get("data");
                if (data != null && data.has("chains")) {
                    double bestFee = 99999.0;
                    boolean found = false;
                    for (JsonNode chain : data.get("chains")) {
                        if (chain.has("isWithdrawEnabled") && chain.get("isWithdrawEnabled").asBoolean()) {
                            double fee = chain.get("withdrawalMinFee").asDouble();
                            if (fee < bestFee) { bestFee = fee; found = true; }
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

        // 1. FIRMA
        String signature = hmacSha256Base64(timestamp + method + endpoint + body, apiSecret);
        String encryptedPassphrase = hmacSha256Base64(rawPassphrase, apiSecret);

        // 2. CONSTRUCCIÓN DEL BUILDER
        Request.Builder builder = new Request.Builder()
                .url(KUCOIN_URL + endpoint)
                .header("KC-API-KEY", getApiKey("kucoin"))
                .header("KC-API-SIGN", signature)
                .header("KC-API-PASSPHRASE", encryptedPassphrase)
                .header("KC-API-TIMESTAMP", String.valueOf(timestamp))
                .header("KC-API-KEY-VERSION", "2")
                .header("Content-Type", "application/json"); // Header explícito siempre ayuda

        // 3. DECISIÓN DE MÉTODO (GET vs POST)
        if ("POST".equalsIgnoreCase(method)) {
            // Aquí inyectamos el cuerpo JSON que faltaba
            builder.post(RequestBody.create(body, MediaType.parse("application/json")));
        } else {
            builder.get();
        }

        return builder.build();
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
    // 🛡️ NÚCLEO DE RESILIENCIA (MÉTODO PRIVADO NUEVO)
    // =========================================================================
    /**
     * Envuelve la llamada de red con lógica de reintentos y espera exponencial.
     * Maneja automáticamente errores 429 (Rate Limit) y 5xx.
     */
    /**
     * NÚCLEO DE EJECUCIÓN HÍBRIDO (v3.0)
     * Separa la lógica de lectura rápida (Data) de la lógica transaccional (Orders).
     */
// Importa esto arriba:
    // import com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry.MetricsService;

    private Response executeRequest(Request request, ExecutionMode mode) throws IOException {
        String exchangeHost = request.url().host().replace("api.", "").replace(".com", ""); // Limpieza simple del nombre
        long startTime = System.nanoTime(); // ⏱️ Reloj de alta precisión
        boolean success = false;

        try {
            if (mode == ExecutionMode.FAST_LANE) {
                // 🏎️ CARRIL RÁPIDO
                try {
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        response.close();
                        // 🔴 Registro de Error
                        MetricsService.get().recordError(exchangeHost);
                        throw new IOException("FastLane Fail: " + response.code());
                    }
                    success = true;
                    return response;
                } catch (IOException e) {
                    MetricsService.get().recordError(exchangeHost);
                    throw e;
                }
            } else {
                // 🛡️ CARRIL PESADO (Con reintentos internos)
                // Nota: Medimos la latencia del bloque completo de reintentos
                Response response = executeWithRetry(request);
                success = true;
                return response;
            }
        } finally {
            // 📏 CÁLCULO DE LATENCIA (Siempre se ejecuta, éxito o fallo)
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            // Solo registramos latencia si hubo éxito o intento de conexión real.
            // Si falló por timeout, cuenta como latencia alta.
            MetricsService.get().recordLatency(exchangeHost, durationMs);
            recordLatency(exchangeHost, durationMs);
            // Si el modo pesado falló tras todos los reintentos, el catch interno ya lo manejó,
            // pero aquí aseguramos el registro si el flag success sigue en false.
            if (!success && mode == ExecutionMode.HEAVY_DUTY) {
                MetricsService.get().recordError(exchangeHost);
            }
        }
    }
    // Tu método executeWithRetry original, pero optimizado para no ser tan agresivo
    private Response executeWithRetry(Request request) throws IOException {
        int attempt = 0;
        long backoff = 200; // Reducido de 500ms a 200ms inicial
        IOException lastException = null;

        while (attempt < MAX_RETRIES) {
            long startTime = System.currentTimeMillis();
            try {
                Response response = client.newCall(request).execute();

                // Métrica de RTT
                recordLatency(request.url().host(), System.currentTimeMillis() - startTime);

                if (response.isSuccessful() || response.code() == 500) { // A veces 500 es body útil en algunos exchanges
                    return response;
                }

                // Manejo de Rate Limit
                if (response.code() == 429) {
                    BotLogger.warn("🚦 RATE LIMIT " + request.url().host());
                    backoff = 2000; // Castigo
                }

                response.close();
            } catch (IOException e) {
                lastException = e;
            }

            attempt++;
            if (attempt >= MAX_RETRIES) break;

            try {
                // En Java 25 Virtual Threads, esto es barato
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted");
            }
            backoff *= 1.5; // Crecimiento más suave
        }
        throw (lastException != null) ? lastException : new IOException("Failed after retries");
    }

    private void recordLatency(String host, long rtt) {
        if (rtt <= 0) return; // Ignorar
        if (host.contains("binance")) exchangeRTT.put("binance", rtt);
        else if (host.contains("bybit")) exchangeRTT.put("bybit", rtt);
        else if (host.contains("mexc")) exchangeRTT.put("mexc", rtt);
        else if (host.contains("kucoin")) exchangeRTT.put("kucoin", rtt);
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


    public long getRTT(String exchange) {
        return exchangeRTT.getOrDefault(exchange.toLowerCase(), -1L);
    }
    public enum ExecutionMode {
        FAST_LANE,   // Para precios/libros: Sin reintentos, Fail-Fast.
        HEAVY_DUTY   // Para órdenes/saldos: Reintentos robustos.
    }
}