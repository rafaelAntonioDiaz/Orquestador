package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TelegramTest {

    @Test
    @DisplayName("DEBUG: Ver respuesta cruda de Telegram")
    void testRawTelegramConnection() throws IOException {
        System.out.println("--- 🕵️ DIAGNÓSTICO DE TELEGRAM ---");

        // 1. Verificar Variables de Entorno
        String token = System.getenv("TELEGRAM_BOT_TOKEN");
        String chatId = System.getenv("TELEGRAM_CHAT_ID");

        System.out.print("1. Token: ");
        if (token == null || token.isEmpty()) {
            System.out.println("❌ NULL o VACÍO (Configura las Env Vars en IntelliJ)");
            return; // Abortar
        } else {
            System.out.println("✅ Cargado (Termina en ..." + token.substring(Math.max(0, token.length() - 5)) + ")");
        }

        System.out.print("2. Chat ID: ");
        if (chatId == null || chatId.isEmpty()) {
            System.out.println("❌ NULL o VACÍO");
            return;
        } else {
            System.out.println("✅ Cargado: " + chatId);
        }

        // 2. Construir la URL manual
        String message = "📢 Test de Diagnóstico: Si lees esto, funciona.";
        String url = "https://api.telegram.org/bot" + token + "/sendMessage?chat_id=" + chatId + "&text=" + message;

        // 3. Ejecutar Petición Cruda
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        System.out.println("3. Enviando petición a Telegram...");

        try (Response response = client.newCall(request).execute()) {
            System.out.println("--- RESPUESTA DEL SERVIDOR ---");
            System.out.println("Código HTTP: " + response.code());

            String body = response.body() != null ? response.body().string() : "Sin cuerpo";
            System.out.println("Cuerpo JSON: " + body);

            if (response.isSuccessful()) {
                System.out.println("✅ ÉXITO TOTAL: El mensaje debería haber llegado.");
            } else {
                System.out.println("❌ ERROR: Telegram rechazó el mensaje.");
                analizarError(response.code(), body);
            }
        }
    }

    private void analizarError(int code, String body) {
        if (code == 401) System.out.println("👉 PISTA: El Token es incorrecto o fue revocado.");
        if (code == 400 && body.contains("chat not found")) System.out.println("👉 PISTA: El Chat ID es incorrecto.");
        if (code == 400 && body.contains("bot was blocked")) System.out.println("👉 PISTA: Debes abrir el chat con el bot y darle a /start.");
        if (code == 404) System.out.println("👉 PISTA: La URL está mal formada (revisa el token, a veces se cuelan espacios).");
    }
}