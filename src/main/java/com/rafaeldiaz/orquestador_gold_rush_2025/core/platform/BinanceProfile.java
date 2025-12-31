package com.rafaeldiaz.orquestador_gold_rush_2025.core.platform;

/**
 * Driver para Binance.
 * Reglas: Símbolos sin guiones, Fee base 0.1% (o 0.075% con BNB).
 */
public final class BinanceProfile implements ExchangeProfile {

    @Override
    public String getName() {
        return "binance";
    }

    @Override
    public String toExchangeSymbol(String asset, String quote) {
        // Binance usa concatenación simple: BTC + USDT = BTCUSDT
        return (asset + quote).toUpperCase();
    }

    @Override
    public String toInternalSymbol(String exchangeSymbol) {
        // En Binance el símbolo ya viene limpio, solo aseguramos mayúsculas
        return exchangeSymbol.toUpperCase();
    }

    @Override
    public double getBaseTakerFee() {
        return 0.001; // 0.1% estándar
    }

    @Override
    public boolean validateOrderRequirements(double price, double quantity) {
        // Regla básica de Binance: Notional > 5 USDT
        return (price * quantity) >= 5.0;
    }
}