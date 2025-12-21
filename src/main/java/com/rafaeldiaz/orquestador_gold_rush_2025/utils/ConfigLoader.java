package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import io.github.cdimascio.dotenv.Dotenv;

public class ConfigLoader {
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing() // Para que no explote si en producción usas variables reales
            .load();

    public static String get(String key) {
        // Primero busca en el sistema (IntelliJ), luego en el archivo .env
        String val = System.getenv(key);
        return (val != null) ? val : dotenv.get(key);
    }
}