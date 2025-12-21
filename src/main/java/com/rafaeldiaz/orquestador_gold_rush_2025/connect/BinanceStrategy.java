package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BinanceStrategy implements ExchangeStrategy {
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private static final String BASE_URL = "https://api.binance.com"; //

    public BinanceStrategy() {
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String getName() { return "Binance"; }

    @Override
    public double fetchBid(String pair) {
        return fetchBookPrice(pair, "bidPrice");
    }

    @Override
    public double fetchAsk(String pair) {
        return fetchBookPrice(pair, "askPrice");
    }

    @Override
    public double getTradingFee(String pair) {
        // Binance estándar es 0.1% (0.001).
        // Se puede dinamizar con /api/v3/account en el futuro.
        return 0.001;
    }

    @Override
    public double getWithdrawalFee(String coin) {
        // Placeholder dinámico o constante según la red.
        return 0.0005; // Estimación base para redes como SOL o similares.
    }

    /**
     * 🛰️ CAPTURA DE PRECISIÓN: Obtiene el Top of Book (L1) para evitar el lag del lastPrice.
     */
    private double fetchBookPrice(String pair, String side) {
        String cleanPair = pair.replace("-", "").toUpperCase();
        String url = BASE_URL + "/api/v3/ticker/bookTicker?symbol=" + cleanPair;

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return 0.0;
            JsonNode root = mapper.readTree(response.body().string());
            // Retornamos el Bid o Ask real del libro de órdenes
            return root.get(side).asDouble();
        } catch (Exception e) {
            BotLogger.error("❌ Error en BinanceStrategy (" + side + "): " + e.getMessage());
            return 0.0;
        }
    }
}