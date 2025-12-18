package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BybitAdapterTest {

    @Test
    @DisplayName("Debe construir un JSON válido para órdenes LIMIT")
    void testBuildOrderRequestLimit() throws IOException {
        BybitAdapter adapter = new BybitAdapter("key", "secret", "https://api.bybit.com");

        // Construimos una orden Limit de compra
        Request request = adapter.buildOrderRequest("BTCUSDT", "Buy", "Limit", 0.5, 50000.0);

        // 1. Verificamos Headers Críticos
        assertEquals("application/json", request.header("Content-Type"));
        assertNotNull(request.header("X-BAPI-SIGN"));
        assertNotNull(request.header("X-BAPI-TIMESTAMP"));

        // 2. Inspeccionamos el JSON Body (Aquí está la magia)
        String body = bodyToString(request);
        System.out.println("JSON Generado (Limit): " + body);

        // Validaciones estrictas de formato
        assertTrue(body.contains("\"category\":\"spot\""));
        assertTrue(body.contains("\"symbol\":\"BTCUSDT\""));
        assertTrue(body.contains("\"side\":\"Buy\""));
        assertTrue(body.contains("\"orderType\":\"Limit\""));
        assertTrue(body.contains("\"qty\":\"0.5\""));
        assertTrue(body.contains("\"price\":\"50000.0\""));
        assertTrue(body.contains("\"timeInForce\":\"FOK\""));

        // Verificamos que termine correctamente sin comas colgantes
        assertTrue(body.trim().endsWith("}"));
    }

    @Test
    @DisplayName("Debe construir un JSON válido para órdenes MARKET")
    void testBuildOrderRequestMarket() throws IOException {
        BybitAdapter adapter = new BybitAdapter("key", "secret", "https://api.bybit.com");

        Request request = adapter.buildOrderRequest("SOLUSDT", "Sell", "Market", 10.0, 0.0);
        String body = bodyToString(request);
        System.out.println("JSON Generado (Market): " + body);

        assertTrue(body.contains("\"orderType\":\"Market\""));
        assertTrue(body.contains("\"side\":\"Sell\""));
        // Market NO debe llevar precio
        assertFalse(body.contains("\"price\""));
        // Market debe ser IOC (Immediate or Cancel)
        assertTrue(body.contains("\"timeInForce\":\"IOC\""));
    }

    // Helper para leer el cuerpo del request
    private String bodyToString(Request request) throws IOException {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }
}