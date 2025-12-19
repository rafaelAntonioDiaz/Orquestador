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

        // 1. Calentamiento (Es vital, la primera vez carga Base64 Encoder)
        for (int i = 0; i < 100; i++) {
            adapter.buildOrderRequest("BTC-USDT", "buy", "limit", 0.1, 50000.0);
        }
        System.out.println("🔥 Calentamiento completado.");

        // 2. Medición Real
        long start = System.nanoTime();
        Request request = adapter.buildOrderRequest("BTC-USDT", "buy", "limit", 0.1, 50000.0);
        long end = System.nanoTime();

        double durationMs = (end - start) / 1_000_000.0;

        System.out.println("🔗 URL: " + request.url());
        System.out.printf("⚡ Latencia REAL: %.4f ms%n", durationMs);

        // Validaciones de Estructura
        assertEquals("POST", request.method());
        assertEquals("ku_key", request.header("KC-API-KEY"));
        assertEquals("2", request.header("KC-API-KEY-VERSION"));

        // La Passphrase debe ir encriptada (no debe ser igual a "ku_pass")
        assertNotEquals("ku_pass", request.header("KC-API-PASSPHRASE"));
        assertNotNull(request.header("KC-API-SIGN"));

        // KuCoin suele ser un pelín más lento por el doble hash (Passphrase + Body)
        // y la codificación Base64, pero debería estar bajo 0.5ms.
        assertTrue(durationMs < 0.5, "KuCoin debe ser ágil (< 0.5ms)");
    }
}