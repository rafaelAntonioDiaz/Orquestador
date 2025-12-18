package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Request;

public interface ExchangeAdapter {
    /** Construye petición pública de precio */
    Request buildPriceRequest(String pair);

    /** Parsea el precio del JSON de respuesta */
    double parsePrice(JsonNode json);

    /** Construye petición privada (firmada) de balance */
    Request buildBalanceRequest(long timestamp);

    /** Parsea el balance USDT del JSON de respuesta */
    double parseBalance(JsonNode json);
    /**
     * Construye una petición POST para colocar una orden.
     * @param pair Par de trading (ej. BTCUSDT)
     * @param side "Buy" o "Sell"
     * @param type "Limit" o "Market"
     * @param qty Cantidad a operar
     * @param price Precio límite (ignorar si es Market)
     */
    Request buildOrderRequest(String pair, String side, String type, double qty, double price);
}