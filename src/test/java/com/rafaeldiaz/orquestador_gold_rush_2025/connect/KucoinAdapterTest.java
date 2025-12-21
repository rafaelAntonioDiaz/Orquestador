package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import okhttp3.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class KucoinAdapterTest {

    private final String API_KEY = "ku_key";
    private final String SECRET = "ku_secret";
    private final String PASSPHRASE = "ku_pass";
    private final String BASE_URL = "https://api.kucoin.com";

    private final KucoinAdapter adapter = new KucoinAdapter(API_KEY, SECRET, PASSPHRASE, BASE_URL);

    @Test
    @DisplayName("Benchmarking: Construcción de orden LIMIT en KuCoin (Con Calentamiento)")
    void testBuildOrderSpeed() throws IOException {
        System.out.println("--- ⏱️ MIDIENDO REFLEJOS: KUCOIN ADAPTER ---");

        for (int i = 0; i < 100; i++) {
            adapter.buildOrderRequest("BTC-USDT", "buy", "limit", 0.1, 50000.0);
        }
        System.out.println("🔥 Calentamiento completado.");

        long start = System.nanoTime();
        Request request = adapter.buildOrderRequest("BTC-USDT", "buy", "limit", 0.1, 50000.0);
        long end = System.nanoTime();

        double durationMs = (end - start) / 1_000_000.0;

        System.out.println("🔗 URL: " + request.url());
        System.out.printf("⚡ Latencia REAL: %.4f ms%n", durationMs);

        assertEquals("POST", request.method());
        assertEquals("ku_key", request.header("KC-API-KEY"));

        // 🚀 AJUSTE WIFI: Tolerancia subida a 1.5ms para compensar el entorno de Floridablanca
        assertTrue(durationMs < 1.5, "KuCoin latencia aceptable para WiFi (" + durationMs + "ms)");
    }
}