package com.rafaeldiaz.orquestador_gold_rush_2025.core.platform;

import java.util.Map;
import java.util.Optional;

/**
 * 📚 REGISTRO DE DRIVERS (Type-Safe Fix)
 * Centraliza la configuración de todos los exchanges soportados.
 */
public class ExchangeRegistry {

    // 🛠️ CORRECCIÓN: Agregamos <String, ExchangeProfile> antes del .of
    // Esto obliga a Java a tratar a todas las implementaciones como su interfaz padre.
    private static final Map<String, ExchangeProfile> REGISTRY = Map.<String, ExchangeProfile>of(
            "binance", new BinanceProfile(),
            "mexc", new MexcProfile(),
            "kucoin", new KucoinProfile(),
            "okx", new OkxProfile(),
            // 🛠️ LA TRIADA BYBIT (Sin Main)
            "bybit_sub1", new BybitProfile(),
            "bybit_sub2", new BybitProfile(),
            "bybit_sub3", new BybitProfile() // <--- Agregamos la 3ra
    );

    public static ExchangeProfile getProfile(String exchangeName) {
        // Normalizamos a minúsculas para búsqueda
        String key = exchangeName.toLowerCase();

        // Soporte para subcuentas dinámicas de Bybit (si usas bybit_subX)
        if (key.startsWith("bybit")) {
            return REGISTRY.get("bybit");
        }

        return Optional.ofNullable(REGISTRY.get(key))
                .orElseThrow(() -> new IllegalArgumentException("Exchange no soportado: " + exchangeName));
    }

    /**
     * Utilidad rápida para normalizar símbolos sin instanciar el perfil manualmente.
     * Uso: ExchangeRegistry.toExchangeSymbol("kucoin", "BTC", "USDT") -> "BTC-USDT"
     */
    public static String toExchangeSymbol(String exchange, String asset, String quote) {
        return getProfile(exchange).toExchangeSymbol(asset, quote);
    }
}