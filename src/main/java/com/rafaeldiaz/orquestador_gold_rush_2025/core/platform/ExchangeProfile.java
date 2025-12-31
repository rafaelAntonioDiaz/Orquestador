package com.rafaeldiaz.orquestador_gold_rush_2025.core.platform;

/**
 * 🔌 EXCHANGE PROFILE (The Driver Interface)
 * Define el comportamiento estandarizado para cualquier exchange que se conecte al sistema.
 * Elimina la necesidad de 'if-else' masivos en el conector.
 */
public sealed interface ExchangeProfile permits BinanceProfile, BybitProfile,
        MexcProfile, KucoinProfile, OkxProfile {

    /**
     * @return El nombre canónico del exchange (ej: "binance").
     */
    String getName();

    /**
     * Convierte el formato interno (BTCUSDT) al formato de la API del exchange.
     * Ej: OKX requiere "BTC-USDT", Binance requiere "BTCUSDT".
     */
    String toExchangeSymbol(String asset, String quote);

    /**
     * Normaliza el símbolo recibido de la API al formato interno del bot.
     * Ej: "BTC-USDT" -> "BTCUSDT".
     */
    String toInternalSymbol(String exchangeSymbol);

    /**
     * Obtiene el Fee de Taker base para este exchange.
     * Útil para cálculos en frío (Cold-Path) o fallbacks.
     */
    double getBaseTakerFee();

    /**
     * Valida si una orden cumple con los requisitos mínimos del exchange.
     * (Notional mínimo, precisión, etc).
     */
    boolean validateOrderRequirements(double price, double quantity);
}