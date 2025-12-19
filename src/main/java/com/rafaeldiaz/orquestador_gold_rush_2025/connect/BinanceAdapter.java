package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Request;

public class BinanceAdapter implements ExchangeAdapter {
    private final String apiKey;
    private final String secret;
    private final String baseUrl;

    public BinanceAdapter(String apiKey, String secret, String baseUrl) {
        this.apiKey = apiKey;
        this.secret = secret;
        this.baseUrl = baseUrl;
    }

    @Override
    public Request buildPriceRequest(String pair) {
        // Binance API V3
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
        // Binance requiere firma en Query String
        String queryString = "timestamp=" + timestamp;
        String signature = SignatureUtil.generateSignature(secret, queryString);

        return new Request.Builder()
                .url(baseUrl + "/api/v3/account?" + queryString + "&signature=" + signature)
                .get()
                .addHeader("X-MBX-APIKEY", apiKey)
                .build();
    }

    @Override
    public double parseBalance(JsonNode json) {
        // Binance retorna lista de balances. Iteramos buscando USDT.
        JsonNode balances = json.get("balances");
        if (balances != null && balances.isArray()) {
            for (JsonNode asset : balances) {
                if ("USDT".equalsIgnoreCase(asset.get("asset").asText())) {
                    return asset.get("free").asDouble(); // "free" es el disponible
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