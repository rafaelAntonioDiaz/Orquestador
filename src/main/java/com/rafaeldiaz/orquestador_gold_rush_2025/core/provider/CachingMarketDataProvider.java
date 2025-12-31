package com.rafaeldiaz.orquestador_gold_rush_2025.core.provider;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector.OrderBook;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.MarketDataProvider;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.util.TrafficController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 🛡️ PROVIDER BLINDADO (Paridad v3.1)
 * - Request Coalescing (Anti-Stampede).
 * - Traffic Controller en Prefetch.
 * - Métricas de Caché (Hits/Misses).
 */
public class CachingMarketDataProvider implements MarketDataProvider {

    private final ExchangeConnector connector;

    // Almacenamiento
    private final Map<String, CachedBook> bookCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<OrderBook>> inflightRequests = new ConcurrentHashMap<>();

    // Métricas (Recuperadas de v3.1)
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    // Executor I/O
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private static final long CACHE_TTL_MS = 2000;

    private record CachedBook(OrderBook book, long timestamp) {
        boolean isValid() { return (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS; }
    }

    public CachingMarketDataProvider(ExchangeConnector connector) {
        this.connector = connector;
    }

    @Override
    public OrderBook getOrderBook(String exchange, String symbol, int depth) {
        String key = exchange + ":" + symbol;

        // 1. Fast Path (Caché)
        CachedBook cached = bookCache.get(key);
        if (cached != null && cached.isValid()) {
            cacheHits.incrementAndGet();
            return cached.book;
        }

        cacheMisses.incrementAndGet();

        // 2. Anti-Stampede (Request Coalescing)
        // Si 10 hilos piden el mismo libro, solo sale 1 petición HTTP.
        CompletableFuture<OrderBook> future = inflightRequests.computeIfAbsent(key, k ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return connector.fetchOrderBook(exchange, symbol, depth);
                    } catch (Exception e) {
                        return null;
                    }
                }, ioExecutor)
        );

        try {
            OrderBook freshBook = future.join();
            if (freshBook != null) {
                bookCache.put(key, new CachedBook(freshBook, System.currentTimeMillis()));
            }
            return freshBook;
        } catch (Exception e) {
            return null;
        } finally {
            // Limpieza inmediata para permitir refresco en el siguiente ciclo
            inflightRequests.remove(key, future);
        }
    }

    @Override
    public Map<String, Map<String, Double>> fetchGlobalPrices(List<String> exchanges) {
        Map<String, Map<String, Double>> globalData = new ConcurrentHashMap<>();
        List<Callable<Void>> tasks = exchanges.stream().map(ex -> (Callable<Void>) () -> {
            try {
                Map<String, Double> prices = connector.fetchAllPrices(ex);
                if (prices != null) globalData.put(ex, prices);
            } catch (Exception ignored) {}
            return null;
        }).toList();

        try { ioExecutor.invokeAll(tasks, 4500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
        return globalData;
    }

    @Override
    public CompletableFuture<Void> prefetchOrderBooks(List<String> assets, List<String> exchanges) {
        return CompletableFuture.runAsync(() -> {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (String asset : assets) {
                for (String ex : exchanges) {
                    tasks.add(() -> {
                        // 🚦 INTEGRACIÓN DE TRAFFIC CONTROLLER (VITAL v3.1)
                        try {
                            TrafficController.acquire(ex);
                            String pair = asset + "USDT";
                            getOrderBook(ex, pair, BotConfig.BOOK_DEPTH); // Poblar caché
                        } catch (Exception ignored) {}
                        return null;
                    });
                }
            }
            try { ioExecutor.invokeAll(tasks, 8000, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
        }, ioExecutor);
    }

    @Override
    public Map<String, Map<String, Double>> fetchAllBalances(List<String> exchanges) {
        Map<String, Map<String, Double>> allBalances = new ConcurrentHashMap<>();
        List<Callable<Void>> tasks = exchanges.stream().map(ex -> (Callable<Void>) () -> {
            try {
                Map<String, Double> b = connector.fetchBalances(ex);
                if (b != null) allBalances.put(ex, b);
            } catch (Exception ignored) {}
            return null;
        }).toList();

        try { ioExecutor.invokeAll(tasks, 2000, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
        return allBalances;
    }

    @Override
    public void invalidateCache() { bookCache.clear(); }

    public long getCacheHits() { return cacheHits.get(); }
    public long getCacheMisses() { return cacheMisses.get(); }
}