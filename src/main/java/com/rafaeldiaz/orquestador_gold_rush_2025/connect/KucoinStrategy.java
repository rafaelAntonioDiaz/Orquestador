package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KucoinStrategy implements ExchangeStrategy {
    private final ExchangeConnector connector;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private static final String BASE_URL = "https://api.kucoin.com";

    public KucoinStrategy(ExchangeConnector connector) {
        this.connector = connector;
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String getName() { return "Kucoin"; }

    @Override
    public double fetchBid(String pair) {
        return fetchBookPrice(pair, "bestBid");
    }

    @Override
    public double fetchAsk(String pair) {
        return fetchBookPrice(pair, "bestAsk");
    }

    @Override
    public double getTradingFee(String pair) {
        // KuCoin Base Fee: 0.1%
        return 0.001;
    }

    @Override
    public double getWithdrawalFee(String coin) {
        // Delegamos al FeeManager el uso del valor pesimista si no hay API directa
        return -1.0;
    }

    /**
     * KUCOIN API V1: GET /api/v1/market/orderbook/level1
     * Requiere formato con guión: BTC-USDT
     */
    private double fetchBookPrice(String pair, String field) {
        // Adaptador de formato: Si viene "SOLUSDT", lo convierte a "SOL-USDT"
        String formattedPair = pair.contains("-") ? pair : pair.replace("USDT", "-USDT");

        String url = BASE_URL + "/api/v1/market/orderbook/level1?symbol=" + formattedPair;

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return 0.0;
            JsonNode root = mapper.readTree(response.body().string());

            // Estructura Kucoin: { "code": "200000", "data": { "bestBid": "...", "bestAsk": "..." } }
            if (root.has("data")) {
                return root.get("data").get(field).asDouble();
            }
            return 0.0;
        } catch (Exception e) {
            BotLogger.error("❌ Error KucoinStrategy (" + field + "): " + e.getMessage());
            return 0.0;
        }
    }
}