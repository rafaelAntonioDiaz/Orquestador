package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.SignatureUtil;
import okhttp3.Request;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import java.time.Instant;

public class MexcAdapter implements ExchangeAdapter {
    private final String apiKey;
    private final String secret;
    private final String baseUrl;

    public MexcAdapter(String apiKey, String secret, String baseUrl) {
        this.apiKey = apiKey;
        this.secret = secret;
        this.baseUrl = baseUrl;
    }

    @Override
    public Request buildPriceRequest(String pair) {
        return new Request.Builder()
                .url(baseUrl + "/api/v3/ticker/price?symbol=" + pair)
                .get()
                .build();
    }

    @Override
    public double parsePrice(JsonNode json) {
        return json.get("price").asDouble();
    }

    @Override
    public Request buildBalanceRequest(long timestamp) {
        // MEXC Account Info V3
        String params = "timestamp=" + timestamp;
        String signature = SignatureUtil.generateSignature(secret, params);

        return new Request.Builder()
                .url(baseUrl + "/api/v3/account?" + params + "&signature=" + signature)
                .get()
                .addHeader("X-MEXC-APIKEY", apiKey)
                .build();
    }

    @Override
    public double parseBalance(JsonNode json) {
        // MEXC devuelve lista de 'balances'. Buscamos 'asset': 'USDT'
        JsonNode balances = json.get("balances");
        if (balances != null && balances.isArray()) {
            for (JsonNode asset : balances) {
                if ("USDT".equalsIgnoreCase(asset.get("asset").asText())) {
                    return asset.get("free").asDouble();
                }
            }
        }
        return 0.0;
    }
    // Asegúrate de tener estos imports


    // ... dentro de la clase ...

    @Override
    public Request buildOrderRequest(String pair, String side, String type, double qty, double price) {
        // MEXC V3 Endpoint
        String url = baseUrl + "/api/v3/order";
        long timestamp = Instant.now().toEpochMilli();

        // 1. Normalización de Parámetros (MEXC es estricto con mayúsculas)
        String sideUpper = side.toUpperCase(); // BUY o SELL
        String typeUpper = type.toUpperCase(); // LIMIT o MARKET

        // 2. Construcción del Query String (Payload)
        // MEXC V3 prefiere los parámetros en la URL (Query String) firmados
        StringBuilder query = new StringBuilder();
        query.append("symbol=").append(pair);
        query.append("&side=").append(sideUpper);
        query.append("&type=").append(typeUpper);
        query.append("&quantity=").append(qty); // Ojo: MEXC usa 'quantity', Bybit usa 'qty'

        if (typeUpper.equals("LIMIT")) {
            query.append("&price=").append(price);
            query.append("&timeInForce=GTC"); // Good Till Cancel por defecto
        }

        // Timestamp es obligatorio para la firma
        query.append("&timestamp=").append(timestamp);
        // RecvWindow opcional pero recomendado
        query.append("&recvWindow=5000");

        // 3. Firma (HMAC SHA256 del Query String completo)
        String signature = SignatureUtil.generateSignature(secret, query.toString());

        // Agregamos la firma al final del query
        String finalQuery = query.toString() + "&signature=" + signature;
        String finalUrl = url + "?" + finalQuery;

        // 4. Construcción del Request
        // Aunque los datos van en la URL, debe ser POST. El body puede ir vacío.
        RequestBody body = RequestBody.create("", MediaType.parse("application/x-www-form-urlencoded"));

        return new Request.Builder()
                .url(finalUrl)
                .post(body)
                .addHeader("X-MEXC-APIKEY", apiKey) // Header específico de MEXC
                .addHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public double[][] fetchCandles(String pair, String interval, int limit) {
        return new double[0][0]; // TODO: Implementar en Epic 3.x
    }

    @Override
    public String transferFunds(String fromAccountType, String toAccountType, double amount, String coin, String toMemberId) {
        return "";
    }

    @Override
    public Request buildTransferRequest(String fromType, String toType, double amount, String coin, String toMemberId) {
        return null;
    }
}