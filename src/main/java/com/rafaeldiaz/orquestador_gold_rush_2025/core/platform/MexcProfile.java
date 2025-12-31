package com.rafaeldiaz.orquestador_gold_rush_2025.core.platform;

public final class MexcProfile implements ExchangeProfile {

    @Override
    public String getName() {
        return "mexc";
    }

    @Override
    public String toExchangeSymbol(String asset, String quote) {
        // MEXC: BTCUSDT
        return (asset + quote).toUpperCase();
    }

    @Override
    public String toInternalSymbol(String exchangeSymbol) {
        return exchangeSymbol.toUpperCase();
    }

    @Override
    public double getBaseTakerFee() {
        // MEXC a veces tiene 0% Maker, pero asumimos 0.1% Taker para cálculos conservadores
        return 0.001;
    }

    @Override
    public boolean validateOrderRequirements(double price, double quantity) {
        return (price * quantity) >= 5.0;
    }
}