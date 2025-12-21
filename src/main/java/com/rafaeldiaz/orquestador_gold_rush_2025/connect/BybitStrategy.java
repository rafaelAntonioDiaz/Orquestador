package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import okhttp3.OkHttpClient;

public class BybitStrategy implements ExchangeStrategy {
    private final ExchangeConnector connector; // Reutilizamos tu lógica de firma

    public BybitStrategy(ExchangeConnector connector) {
        this.connector = connector;
    }

    @Override
    public String getName() { return "Bybit"; }

    @Override
    public double fetchBid(String pair) {
        // En el futuro aquí usaremos WebSockets para "velocidad de la luz"
        return connector.fetchPrice("bybit", pair);
    }

    @Override
    public double fetchAsk(String pair) {
        return connector.fetchPrice("bybit", pair);
    }

    @Override
    public double getTradingFee(String pair) {
        return connector.fetchDynamicTradingFee("bybit", pair)[0]; // Taker Fee
    }

    @Override
    public double getWithdrawalFee(String coin) {
        return connector.fetchLiveWithdrawalFee("bybit", coin); // Fee real de red
    }
}