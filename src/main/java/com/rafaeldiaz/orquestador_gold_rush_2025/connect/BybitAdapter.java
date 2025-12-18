package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Request;
import okhttp3.MediaType;
import okhttp3.RequestBody;

public class BybitAdapter implements ExchangeAdapter {
    private final String apiKey;
    private final String secret;
    private static final String BASE_URL = "https://api.bybit.com";


    public BybitAdapter(String apiKey, String secret, String baseUrl) {
        this.apiKey = apiKey;
        this.secret = secret;
    }

    @Override
    public Request buildPriceRequest(String pair) {
        // Bybit V5 Spot Ticker
        return new Request.Builder()
                .url(BASE_URL + "/v5/market/tickers?category=spot&symbol=" + pair)
                .get()
                .build();
    }

    @Override
    public double parsePrice(JsonNode json) {
        return json.at("/result/list/0/lastPrice").asDouble();
    }

    @Override
    public Request buildBalanceRequest(long timestamp) {
        String recvWindow = "5000";
        String queryString = "accountType=UNIFIED&coin=USDT";

        // Firma: timestamp + key + recvWindow + params
        String payload = timestamp + apiKey + recvWindow + queryString;
        String signature = SignatureUtil.generateSignature(secret, payload);

        return new Request.Builder()
                .url(BASE_URL + "/v5/account/wallet-balance?" + queryString)
                .get()
                .addHeader("X-BAPI-API-KEY", apiKey)
                .addHeader("X-BAPI-SIGN", signature)
                .addHeader("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                .addHeader("X-BAPI-RECV-WINDOW", recvWindow)
                .build();
    }

    @Override
    public double parseBalance(JsonNode json) {
        return json.at("/result/list/0/coin/0/walletBalance").asDouble();
    }


    @Override
    public Request buildOrderRequest(String pair, String side, String type, double qty, double price) {
        // 1. Construir el JSON Body (Manual para evitar overhead de Jackson en cada orden)
        // Nota: Bybit V5 requiere "orderType" (CamelCase) y valores en String a veces preferibles
        String orderType = type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase(); // Limit / Market
        String sideCap = side.substring(0, 1).toUpperCase() + side.substring(1).toLowerCase();   // Buy / Sell

        // Construcción eficiente del JSON String
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"category\":\"spot\",");
        json.append("\"symbol\":\"").append(pair).append("\",");
        json.append("\"side\":\"").append(sideCap).append("\",");
        json.append("\"orderType\":\"").append(orderType).append("\",");
        json.append("\"qty\":\"").append(qty).append("\",");

        if ("Limit".equalsIgnoreCase(orderType)) {
            json.append("\"price\":\"").append(price).append("\",");
            json.append("\"timeInForce\":\"FOK\""); // Fill Or Kill (Task 2.2.2 Requisito)
        } else {
            // Si es Market, no enviamos precio ni timeInForce (o GTC por defecto)
            json.append("\"timeInForce\":\"IOC\""); // Immediate Or Cancel para Market
        }
        json.append("}");

        String jsonBody = json.toString();
        long timestamp = System.currentTimeMillis();
        String recvWindow = "5000";

        // 2. Firmar (POST Payload = timestamp + key + recvWindow + jsonBody)
        String payloadToSign = timestamp + apiKey + recvWindow + jsonBody;
        String signature = SignatureUtil.generateSignature(secret, payloadToSign);

        // 3. Construir Request
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

        return new Request.Builder()
                .url(BASE_URL + "/v5/order/create")
                .post(body)
                .addHeader("X-BAPI-API-KEY", apiKey)
                .addHeader("X-BAPI-SIGN", signature)
                .addHeader("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                .addHeader("X-BAPI-RECV-WINDOW", recvWindow)
                .addHeader("Content-Type", "application/json") // Crítico para POST
                .build();
    }
}