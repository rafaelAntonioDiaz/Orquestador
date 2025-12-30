package com.rafaeldiaz.orquestador_gold_rush_2025.model;

import java.util.Collections;
import java.util.Map;

/**
 * 📸 FOTO INMUTABLE DE SALDOS
 * Representa el estado financiero en un nanosegundo específico.
 * Una vez creado, NO puede ser modificado por ningún hilo.
 */
public record BalanceSnapshot(
        Map<String, Map<String, Double>> balances, // Mapa de Exchanges -> Activos -> Cantidad
        long timestamp // Cuándo se tomó la foto (System.currentTimeMillis)
) {
    // Constructor canónico compacto para blindar las colecciones
    public BalanceSnapshot {
        // Hacemos el mapa inmodificable para que nadie pueda meter mano "por error"
        balances = Map.copyOf(balances);
    }

    /**
     * ⛽ Helper seguro para consultar saldo
     * Retorna 0.0 si el exchange o el activo no existen, evitando NullPointer.
     */
    public double getAvailableBalance(String exchange, String asset) {
        return balances.getOrDefault(exchange, Collections.emptyMap())
                .getOrDefault(asset, 0.0);
    }

    /**
     * 🕵️ Helper para verificar frescura
     * @param maxAgeMs Edad máxima permitida en milisegundos
     */
    public boolean isFresh(long maxAgeMs) {
        return (System.currentTimeMillis() - timestamp) <= maxAgeMs;
    }
}