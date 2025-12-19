package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.SignatureUtil;
import okhttp3.Request;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import java.time.Instant;

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
        // Binance V3 Endpoint
        String url = baseUrl + "/api/v3/order";
        long timestamp = Instant.now().toEpochMilli();

        String sideUpper = side.toUpperCase();
        String typeUpper = type.toUpperCase();

        StringBuilder query = new StringBuilder();
        query.append("symbol=").append(pair);
        query.append("&side=").append(sideUpper);
        query.append("&type=").append(typeUpper);

        // Binance también usa 'quantity' (Bybit usa 'qty')
        query.append("&quantity=").append(qty);

        if (typeUpper.equals("LIMIT")) {
            query.append("&price=").append(price);
            query.append("&timeInForce=GTC");
        }

        query.append("&timestamp=").append(timestamp);
        // RecvWindow recomendado para evitar errores de sync
        query.append("&recvWindow=5000");

        // FIRMA (Usando tu nuevo y veloz SignatureUtil)
        String signature = SignatureUtil.generateSignature(secret, query.toString());

        // Binance envía todo en el Query String para POST
        String finalUrl = url + "?" + query.toString() + "&signature=" + signature;

        // Body vacío
        RequestBody body = RequestBody.create("", MediaType.parse("application/x-www-form-urlencoded"));

        return new Request.Builder()
                .url(finalUrl)
                .post(body)
                .addHeader("X-MBX-APIKEY", apiKey) // <--- OJO: Header de Binance es diferente al de MEXC
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
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