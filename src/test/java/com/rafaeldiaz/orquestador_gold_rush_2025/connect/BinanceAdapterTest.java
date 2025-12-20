package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import okhttp3.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinanceAdapterTest {

    private final String API_KEY = "binance_key";
    private final String SECRET = "binance_secret";
    private final String BASE_URL = "https://api.binance.com";
    private final BinanceAdapter adapter = new BinanceAdapter(API_KEY, SECRET, BASE_URL);

    @Test
    @DisplayName("Benchmarking: Construcción de orden LIMIT en Binance (Con Calentamiento)")
    void testBuildOrderSpeed() {
        System.out.println("--- ⏱️ MIDIENDO REFLEJOS: BINANCE ADAPTER ---");

        // 1. Calentamiento
        for (int i = 0; i < 100; i++) {
            adapter.buildOrderRequest("ETHUSDT", "SELL", "LIMIT", 1.5, 3000.0);
        }
        System.out.println("🔥 Calentamiento completado.");

        // 2. Medición Real
        long start = System.nanoTime();
        Request request = adapter.buildOrderRequest("ETHUSDT", "SELL", "LIMIT", 1.5, 3000.0);
        long end = System.nanoTime();

        double durationMs = (end - start) / 1_000_000.0;

        System.out.println("🔗 URL Generada: " + request.url());
        System.out.printf("⚡ Latencia REAL: %.4f ms%n", durationMs);

        // Validaciones
        assertEquals("POST", request.method());
        assertEquals("binance_key", request.header("X-MBX-APIKEY"));
        assertTrue(request.url().toString().contains("signature="));

        // Debería ser igual de rápido que MEXC
        // Calibración de excelencia para trading institucional
        assertTrue(durationMs < 5.0,
                "⚠️ ALARMA DE LATENCIA: La construcción de orden en Binance tardó " + durationMs + "ms. " +
                        "El objetivo es < 5ms para evitar arbitraje fantasma.");

        System.out.println("✅ Benchmarking completado: Binance está en zona de alta velocidad.");
    }
}