package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import java.util.List;

/**
 * 👂 OÍDO TÁCTICO
 * Permite que el Orquestador reaccione a cambios en el mercado
 * detectados por el Radar (DynamicPairSelector).
 */
public interface MarketListener {
    void updateTargets(List<String> newTargets);
}