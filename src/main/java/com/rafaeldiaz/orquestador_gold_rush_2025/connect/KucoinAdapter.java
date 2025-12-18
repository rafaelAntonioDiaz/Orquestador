package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Request;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class KucoinAdapter implements ExchangeAdapter {
    private final String apiKey;
    private final String secret;
    private final String passphrase; // Exclusivo de KuCoin
    private final String baseUrl;

    public KucoinAdapter(String apiKey, String secret, String passphrase, String baseUrl) {
        this.apiKey = apiKey;
        this.secret = secret;
        this.passphrase = passphrase;
        this.baseUrl = baseUrl;
    }

    @Override
    public Request buildPriceRequest(String pair) {
        // KuCoin Level 1 Data (Mejor Bid/Ask/Price)
        return new Request.Builder()
                .url(baseUrl + "/api/v1/market/orderbook/level1?symbol=" + pair)
                .get()
                .build();
    }

    @Override
    public double parsePrice(JsonNode json) {
        // KuCoin envuelve la respuesta en "data"
        return json.at("/data/price").asDouble();
    }

    @Override
    public Request buildBalanceRequest(long timestamp) {
        String endpoint = "/api/v1/accounts";
        String queryString = "?currency=USDT"; // Ojo: el endpoint completo lleva query params

        // Lógica de firma KuCoin: timestamp + method + endpoint + body
        String strToSign = timestamp + "GET" + endpoint + queryString;
        String signature = generateBase64Signature(secret, strToSign);

        // La passphrase también debe firmarse para el header KC-API-PASSPHRASE
        String passphraseSignature = generateBase64Signature(secret, passphrase);

        return new Request.Builder()
                .url(baseUrl + endpoint + queryString)
                .get()
                .addHeader("KC-API-KEY", apiKey)
                .addHeader("KC-API-SIGN", signature)
                .addHeader("KC-API-TIMESTAMP", String.valueOf(timestamp))
                .addHeader("KC-API-PASSPHRASE", passphraseSignature)
                .addHeader("KC-API-KEY-VERSION", "2") // Importante para cuentas nuevas
                .build();
    }

    @Override
    public double parseBalance(JsonNode json) {
        // KuCoin retorna lista. Buscamos 'available' (disponible)
        JsonNode list = json.get("data");
        if (list != null && list.isArray()) {
            for (JsonNode account : list) {
                if ("USDT".equalsIgnoreCase(account.get("currency").asText())
                        && "trade".equalsIgnoreCase(account.get("type").asText())) { // Solo cuenta de trading
                    return account.get("available").asDouble();
                }
            }
        }
        return 0.0;
    }

    // KuCoin requiere Base64, no Hex. Implementación local segura.
    private String generateBase64Signature(String secret, String data) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Error firmando para KuCoin", e);
        }
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
}