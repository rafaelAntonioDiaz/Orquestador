package com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector.OrderBook;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface MarketDataProvider {

    /**
     * Obtiene un libro de órdenes. Si está en caché y es válido, lo devuelve.
     * Si no, va a la API.
     */
    OrderBook getOrderBook(String exchange, String symbol, int depth);

    /**
     * FASE 1: Obtiene precios globales (Tickers) de múltiples exchanges en paralelo.
     */
    Map<String, Map<String, Double>> fetchGlobalPrices(List<String> exchanges);

    /**
     * FASE 1.5: Pre-calentamiento masivo de caché (Prefetch).
     * Crítico para reducir latencia antes de entrar al bucle de estrategias.
     * Replicamos tu lógica de "TrafficController" aquí dentro.
     */
    CompletableFuture<Void> prefetchOrderBooks(List<String> assets, List<String> exchanges);

    /**
     * Gestión de Balances: Abstrae la lógica de llamadas a la API de saldos.
     */
    Map<String, Map<String, Double>> fetchAllBalances(List<String> exchanges);

    /**
     * Limpieza de recursos al final del ciclo.
     */
    void invalidateCache();
}