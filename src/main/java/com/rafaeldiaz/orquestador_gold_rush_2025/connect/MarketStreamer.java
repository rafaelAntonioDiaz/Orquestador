package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import java.util.ArrayList;
import java.util.List;

/**
 * CONTRATO BASE (Abstract): Define el estándar para cualquier Streamer.
 * Ahora incluye gestión completa de suscripciones (Alta y Baja).
 */
public abstract class MarketStreamer {

    protected final List<PriceListener> listeners = new ArrayList<>();

    // --- MÉTODOS ABSTRACTOS (El hijo DEBE implementarlos) ---
    public abstract void subscribe(String pair);
    public abstract void unsubscribe(String pair); // <--- NUEVO: Faltaba este
    public abstract void stop();
    public abstract boolean isActive(); // <--- NUEVO: Para saber si está vivo

    // --- MÉTODOS COMUNES (Ya implementados) ---

    public void addListener(PriceListener listener) {
        this.listeners.add(listener);
    }

    protected void notifyListeners(String exchange, String pair, double price, long timestamp) {
        for (PriceListener listener : listeners) {
            listener.onPriceUpdate(exchange, pair, price, timestamp);
        }
    }

    public interface PriceListener {
        void onPriceUpdate(String exchange, String pair, double price, long timestamp);
    }
}