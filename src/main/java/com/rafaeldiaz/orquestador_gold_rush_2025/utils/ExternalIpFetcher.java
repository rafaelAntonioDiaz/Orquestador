package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 📡 SENSOR DE IDENTIDAD EXTERNA
 * Detecta la IP pública real del bot para compararla con Bybit.
 */
public class ExternalIpFetcher {
    private static final OkHttpClient client = new OkHttpClient();

    public static String getMyPublicIp() {
        try {
            Request request = new Request.Builder()
                    .url("https://api.ipify.org")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.body().string();
            }
        } catch (Exception e) {
            return "DESCONOCIDA";
        }
    }
}