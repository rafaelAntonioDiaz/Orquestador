package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.SignatureUtil;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class KucoinAdapter implements ExchangeAdapter {
    private final String apiKey;
    private final String secret;
    private final String passphrase; // Exclusivo de KuCoin
    private final String baseUrl;
    private static final AtomicLong oidCounter = new AtomicLong(System.currentTimeMillis());

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
        // KuCoin V2 Endpoint
        String endpoint = "/api/v1/orders"; // Endpoint relativo para la firma
        String url = baseUrl + endpoint;

        long timestamp = Instant.now().toEpochMilli();

        // 1. Passphrase Firmada (Header KC-API-PASSPHRASE)
        // KuCoin pide: Base64(HmacSHA256(passphrase, secret))
        String signedPassphrase = SignatureUtil.generateSignatureBase64(secret, passphrase);

        // 2. Construcción del Body JSON
        // KuCoin es estricto con el JSON.
        String clientOid = Long.toHexString(oidCounter.getAndIncrement());

        String sideLower = side.toLowerCase(); // KuCoin usa minúsculas (buy/sell)
        String typeLower = type.toLowerCase(); // limit/market

        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"clientOid\":\"").append(clientOid).append("\",")
                .append("\"side\":\"").append(sideLower).append("\",")
                .append("\"symbol\":\"").append(pair).append("\",")
                .append("\"type\":\"").append(typeLower).append("\",");

        if (typeLower.equals("limit")) {
            json.append("\"price\":\"").append(price).append("\",");
            json.append("\"size\":\"").append(qty).append("\""); // KuCoin usa 'size' para cantidad base
        } else {
            // Market order: size (amount base) or funds (amount quote)
            json.append("\"size\":\"").append(qty).append("\"");
        }
        json.append("}");

        String jsonBodyString = json.toString();

        // 3. Firma del Request (Header KC-API-SIGN)
        // String to sign: timestamp + method + endpoint + body
        String strToSign = timestamp + "POST" + endpoint + jsonBodyString;
        String signature = SignatureUtil.generateSignatureBase64(secret, strToSign);

        RequestBody body = RequestBody.create(jsonBodyString, MediaType.parse("application/json"));

        return new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("KC-API-KEY", apiKey)
                .addHeader("KC-API-SIGN", signature)
                .addHeader("KC-API-TIMESTAMP", String.valueOf(timestamp))
                .addHeader("KC-API-PASSPHRASE", signedPassphrase)
                .addHeader("KC-API-KEY-VERSION", "2") // Importante para API V2
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