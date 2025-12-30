package com.rafaeldiaz.orquestador_gold_rush_2025.core.util;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 🚦 CONTROLADOR DE TRÁFICO AÉREO (RATE LIMITER)
 * Convierte ráfagas de 2000 peticiones en un flujo constante seguro.
 * Implementa algoritmo "Token Bucket" usando Semáforos.
 */
public class TrafficController {

    // Mapa de semáforos por Exchange (Bucket de Tokens)
    private static final Map<String, Semaphore> buckets = new ConcurrentHashMap<>();

    // Scheduler para rellenar los buckets
    private static final ScheduledExecutorService refiller = Executors.newSingleThreadScheduledExecutor();

    static {
        // Inicializamos los límites (Requests por Segundo - RPS)
        // Valores conservadores para evitar BAN (Safety Margin 20%)
        initExchange("binance", 15); // Binance ~20/s
        initExchange("bybit", 10);   // Bybit ~10-20/s depende de endpoint
        initExchange("mexc", 20);    // MEXC es más permisivo

        // Tarea de recarga: Cada 100ms agregamos 1/10 de los tokens (Flujo suave)
        refiller.scheduleAtFixedRate(TrafficController::refillTokens, 0, 100, TimeUnit.MILLISECONDS);
    }

    private static void initExchange(String exchange, int rps) {
        // El semáforo inicia con capacidad máxima (burst permitido)
        buckets.put(exchange, new Semaphore(rps));
    }

    /**
     * 🛑 FRENO: Solicita permiso para llamar a la API.
     * Si no hay tokens, el Hilo Virtual se suspende aquí hasta que haya cupo.
     */
    public static void acquire(String exchange) {
        Semaphore sem = buckets.computeIfAbsent(exchange, k -> new Semaphore(5)); // Default seguro
        try {
            sem.acquire(); // Bloqueo eficiente (Virtual Thread Friendly)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void refillTokens() {
        // Rellenamos proporcionalmente cada 100ms
        replenish("binance", 15);
        replenish("bybit", 10);
        replenish("mexc", 20);
    }

    private static void replenish(String exchange, int maxRps) {
        Semaphore sem = buckets.get(exchange);
        if (sem != null) {
            int currentPermits = sem.availablePermits();
            // No exceder el límite máximo (Max Burst)
            if (currentPermits < maxRps) {
                // Agregamos tokens (RPS / 10 porque corremos cada 100ms)
                int tokensToAdd = Math.max(1, maxRps / 10);
                sem.release(Math.min(tokensToAdd, maxRps - currentPermits));
            }
        }
    }
}