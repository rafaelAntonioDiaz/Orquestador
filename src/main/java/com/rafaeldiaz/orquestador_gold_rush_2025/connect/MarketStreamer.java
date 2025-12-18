package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

public interface MarketStreamer {
    /**
     * Inicia la conexión WebSocket.
     */
    void connect();

    /**
     * Se suscribe a un par específico (ej. "BTCUSDT") para recibir actualizaciones de precio.
     */
    void subscribe(String pair);

    /**
     * Cierra la conexión de forma limpia.
     */
    void disconnect();

    /**
     * Interfaz funcional para recibir los precios en tiempo real.
     * Quien use el streamer (el Bot) implementará esto para reaccionar.
     */
    @FunctionalInterface
    interface PriceListener {
        void onPriceUpdate(String exchange, String pair, double price, long timestamp);
    }
}