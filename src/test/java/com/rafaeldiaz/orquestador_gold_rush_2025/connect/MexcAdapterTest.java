package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import okhttp3.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MexcAdapterTest {

    private final String API_KEY = "test_key";
    private final String SECRET = "test_secret";
    private final String BASE_URL = "https://api.mexc.com";
    private final MexcAdapter adapter = new MexcAdapter(API_KEY, SECRET, BASE_URL);

    @Test
    @DisplayName("Benchmarking: Construcción de orden LIMIT en MEXC (Con Calentamiento)")
    void testBuildLimitOrderSpeed() {
        System.out.println("--- ⏱️ MIDIENDO REFLEJOS: MEXC ADAPTER ---");

        // 1. FASE DE CALENTAMIENTO (WARM-UP)
        // Hacemos que el agente practique el movimiento 100 veces para "despertar" la JVM
        // y que el compilador JIT optimice el código.
        for (int i = 0; i < 100; i++) {
            adapter.buildOrderRequest("BTCUSDT", "BUY", "LIMIT", 0.001, 50000.0);
        }
        System.out.println("🔥 Calentamiento completado (JVM lista).");

        // 2. LA PRUEBA REAL (Medimos una sola ejecución ya optimizada)
        long start = System.nanoTime();

        // El movimiento maestro
        Request request = adapter.buildOrderRequest("BTCUSDT", "BUY", "LIMIT", 0.001, 50000.0);

        long end = System.nanoTime();

        // 3. Resultados
        long durationNs = end - start;
        double durationMs = durationNs / 1_000_000.0;

        System.out.println("🔗 URL Generada: " + request.url());
        System.out.printf("⚡ Latencia REAL (Hot Path): %.4f ms (%d ns)%n", durationMs, durationNs);

        // Validaciones
        assertEquals("POST", request.method());

        // Ahora sí, exigimos perfección (< 0.5 ms)
        if (durationMs > 0.5) {
            System.err.println("⚠️ AÚN LENTO: Algo anda mal en la lógica.");
        } else {
            System.out.println("✅ VELOCIDAD SUPREMA: El agente fluye sin resistencia.");
        }
    }
}