package com.rafaeldiaz.orquestador_gold_rush_2025.core.platform;

/**
 * 🔌 OKX DRIVER
 * Gestiona las particularidades de OKX:
 * 1. Símbolos con guión (BTC-USDT).
 * 2. Validación de notional mínimo (~5 USDT).
 */
public final class OkxProfile implements ExchangeProfile {

    @Override
    public String getName() {
        return "okx";
    }

    @Override
    public String toExchangeSymbol(String asset, String quote) {
        // OKX requiere formato con guión: "BTC-USDT"
        return (asset + "-" + quote).toUpperCase();
    }

    @Override
    public String toInternalSymbol(String exchangeSymbol) {
        // Normalizamos a formato interno plano: "BTC-USDT" -> "BTCUSDT"
        return exchangeSymbol.replace("-", "").toUpperCase();
    }

    @Override
    public double getBaseTakerFee() {
        // OKX suele tener fees base de 0.08% o 0.1%.
        // Usamos 0.1% (0.001) para ser conservadores en las estimaciones.
        return 0.001;
    }

    @Override
    public boolean validateOrderRequirements(double price, double quantity) {
        // OKX tiene reglas de tamaño de lote específicas, pero 5 USDT
        // es un filtro de seguridad global razonable para evitar errores de API.
        return (price * quantity) >= 5.0;
    }
}