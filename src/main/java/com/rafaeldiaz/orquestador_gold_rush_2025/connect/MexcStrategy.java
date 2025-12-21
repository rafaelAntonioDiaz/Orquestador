package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MexcStrategy implements ExchangeStrategy {
    private final ExchangeConnector connector; // Para fees y firma
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private static final String BASE_URL = "https://api.mexc.com";

    public MexcStrategy(ExchangeConnector connector) {
        this.connector = connector;
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String getName() { return "Mexc"; }

    @Override
    public double fetchBid(String pair) {
        // MEXC usa formato estándar "SOLUSDT"
        return fetchBookPrice(pair, "bidPrice");
    }

    @Override
    public double fetchAsk(String pair) {
        return fetchBookPrice(pair, "askPrice");
    }

    @Override
    public double getTradingFee(String pair) {
        // MEXC suele tener promociones de 0% maker, pero asumimos 0.1% por seguridad
        // O intentamos leer del conector si implementaste dynamic fees para MEXC
        return 0.001;
    }

    @Override
    public double getWithdrawalFee(String coin) {
        // Como aun no implementamos lectura de fee de retiro para MEXC en el Connector,
        // devolvemos -1.0 para que el FeeManager use la tabla PESIMISTA (Safety First)
        return -1.0;
    }

    /**
     * MEXC API V3: GET /api/v3/ticker/bookTicker
     */
    private double fetchBookPrice(String pair, String field) {
        String cleanPair = pair.replace("-", "").toUpperCase();
        String url = BASE_URL + "/api/v3/ticker/bookTicker?symbol=" + cleanPair;

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return 0.0;
            JsonNode root = mapper.readTree(response.body().string());
            // Estructura: {"symbol": "SOLUSDT", "bidPrice": "145.00", "askPrice": "145.10", ...}
            return root.get(field).asDouble();
        } catch (Exception e) {
            BotLogger.error("❌ Error MexcStrategy (" + field + "): " + e.getMessage());
            return 0.0;
        }
    }
}