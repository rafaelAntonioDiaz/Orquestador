package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@DisplayName("📏 Integración: Precisión Decimal (Bybit)")
class BybitPrecisionTest {

    @Test
    @DisplayName("🎯 BYBIT FIX: Debe truncar decimales según StepSize")
    void testBybitDecimalFormatting() throws Exception {
        // 1. Instanciar Conector (Spy para simular stepSize)
        ExchangeConnector connector = new ExchangeConnector();
        ExchangeConnector spyConnector = spy(connector);

        // 2. SIMULAR REGLAS DEL MERCADO
        // WIFUSDT en Bybit suele tener stepSize 0.1 o 1.
        // Simularemos un stepSize de 0.1 (1 decimal permitido)
        doReturn(0.1).when(spyConnector).getStepSize(anyString(), anyString());

        // Simulamos claves para que no falle la firma (Valores dummy)
        // Nota: Esto requiere que tu conector maneje nulos o tengas un EnvProvider mockeado.
        // Si falla por NullPointer en claves, usa el constructor con EnvProvider mockeado.

        // 3. INTENTO DE ORDEN CON MUCHOS DECIMALES
        // Queremos vender 10.56789 unidades.
        // Si el step es 0.1, debe enviar "10.5" o "10.6" (dependiendo del rounding, usualmente floor -> 10.5)
        double qtyRaw = 10.56789;

        Request request = spyConnector.buildOrderRequest(
                "bybit", "WIF-USDT", "SELL", "MARKET", qtyRaw, 0.0
        );

        // 4. INSPECCIONAR EL JSON GENERADO
        String body = bodyToString(request);
        System.out.println("📦 JSON Generado: " + body);

        // 5. VALIDACIÓN
        // Debe contener "10.5" o "10.6". NO "10.56789"
        // Tu lógica usa String.format, que redondea. 10.56 -> 10.6
        boolean isCorrect = body.contains("\"qty\": \"10.6\"") || body.contains("\"qty\": \"10.5\"");

        assertTrue(isCorrect, "❌ FALLO: El JSON contiene demasiados decimales o formato incorrecto.");
        assertTrue(!body.contains("10.56789"), "❌ FALLO: Se filtró la cantidad cruda.");
    }

    private String bodyToString(Request request) {
        try {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (IOException e) {
            return "";
        }
    }
}