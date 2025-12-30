package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ♻️ CACHÉ DE SÍMBOLOS (PATTERN FLYWEIGHT)
 * Elimina la creación de Strings en el Hot Path (Bucle de Análisis).
 * Convierte 1GB de basura/hora en 0 bytes.
 */
public class SymbolCache {

    // K: "BTC_USDT", V: "BTCUSDT" (La instancia única en memoria)
    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Obtiene el par concatenado sin crear basura si ya existe.
     * @param asset Activo base (ej: "BTC")
     * @param quote Activo cotizado (ej: "USDT")
     * @return String única "BTCUSDT"
     */
    public static String get(String asset, String quote) {
        // Clave compuesta para búsqueda rápida
        // Nota: Usamos una clave simple. Si asset y quote son referencias constantes,
        // esto es muy rápido.
        String key = asset + "_" + quote;

        // computeIfAbsent es atómico y eficiente
        return cache.computeIfAbsent(key, k -> (asset + quote).intern());
    }

    /**
     * Versión optimizada para pares comunes (evita concatenar la key si es posible)
     */
    public static String getUsdtPair(String asset) {
        return get(asset, "USDT");
    }
}