package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Request;

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
    @Override
    public Request buildOrderRequest(String pair, String side, String type, double qty, double price) {
        // TODO: Implementar en Epic 4 (Cross Selectivo)
        throw new UnsupportedOperationException("Trading no implementado aún para este exchange");
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