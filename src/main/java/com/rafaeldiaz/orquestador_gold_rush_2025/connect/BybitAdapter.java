package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.SignatureUtil;
import okhttp3.*;
import java.util.UUID;

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
    @Override
    public double[][] fetchCandles(String pair, String interval, int limit) {
        // Mapeo de intervalos: Bybit usa "60" para 1h. "1" para 1m.
        String url = BASE_URL + "/v5/market/kline?category=spot&symbol=" + pair + "&interval=" + interval + "&limit=" + limit;
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) throw new RuntimeException("Error fetching candles: " + response.code());

            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body().string());
            JsonNode list = root.at("/result/list");

            if (list.isArray()) {
                double[][] candles = new double[list.size()][3];
                for (int i = 0; i < list.size(); i++) {
                    JsonNode kline = list.get(i);
                    // Bybit V5 kline order: [startTime, open, high, low, close, volume, turnover]
                    // Necesitamos High(2), Low(3), Close(4)
                    candles[i][0] = Double.parseDouble(kline.get(2).asText()); // High
                    candles[i][1] = Double.parseDouble(kline.get(3).asText()); // Low
                    candles[i][2] = Double.parseDouble(kline.get(4).asText()); // Close
                }
                return candles;
            }
        } catch (Exception e) {
            throw new RuntimeException("Fallo al obtener velas Bybit", e);
        }
        return new double[0][0];
    }

    @Override
    public String transferFunds(String fromAccountType, String toAccountType, double amount, String coin, String toMemberId) {
        // Endpoint V5 para mover entre Main-Sub o Sub-Sub
        String url = BASE_URL + "/v5/asset/transfer/universal-transfer";

        long timestamp = System.currentTimeMillis();
        String transferId = UUID.randomUUID().toString();

        // Construimos JSON Body
        // fromAccountType: "UNIFIED" (generalmente)
        // toMemberId: El UID numérico de la subcuenta destino
        String jsonBody = String.format(
                "{\"transferId\":\"%s\",\"coin\":\"%s\",\"amount\":\"%s\",\"fromAccountType\":\"%s\",\"toAccountType\":\"%s\",\"toMemberId\":\"%s\"}",
                transferId, coin, amount, fromAccountType, toAccountType, toMemberId
        );

        // Firma (POST)
        String recvWindow = "5000";
        String payloadToSign = timestamp + apiKey + recvWindow + jsonBody;
        String signature = SignatureUtil.generateSignature(secret, payloadToSign);

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("X-BAPI-API-KEY", apiKey)
                .addHeader("X-BAPI-SIGN", signature)
                .addHeader("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                .addHeader("X-BAPI-RECV-WINDOW", recvWindow)
                .addHeader("Content-Type", "application/json")
                .build();

        // Ejecutamos aquí mismo (o delegamos al connector, pero el adapter construye)
        // Por consistencia con tu arquitectura, el adapter construye el Request,
        // pero como definimos que retorna String, vamos a permitir que el Connector ejecute.
        // CORRECCIÓN ARQUITECTÓNICA:
        // Tus otros métodos devuelven 'Request'. Cambiemos la interfaz para devolver 'Request'
        // y que ExchangeConnector ejecute. Es más limpio.
        return null; // Ver abajo corrección paso 1.1
    }

    // CORRECCIÓN: Implementa este método devolviendo Request
    public Request buildTransferRequest(String fromType, String toType, double amount, String coin, String toMemberId) {
        String url = BASE_URL + "/v5/asset/transfer/universal-transfer";
        long timestamp = System.currentTimeMillis();
        String transferId = UUID.randomUUID().toString();

        String jsonBody = String.format(
                "{\"transferId\":\"%s\",\"coin\":\"%s\",\"amount\":\"%s\",\"fromAccountType\":\"%s\",\"toAccountType\":\"%s\",\"toMemberId\":\"%s\"}",
                transferId, coin, amount, fromType, toType, toMemberId
        );

        String recvWindow = "5000";
        String payloadToSign = timestamp + apiKey + recvWindow + jsonBody;
        String signature = SignatureUtil.generateSignature(secret, payloadToSign);

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

        return new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("X-BAPI-API-KEY", apiKey)
                .addHeader("X-BAPI-SIGN", signature)
                .addHeader("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                .addHeader("X-BAPI-RECV-WINDOW", recvWindow)
                .addHeader("Content-Type", "application/json")
                .build();
    }
}