package com.rafaeldiaz.orquestador_gold_rush_2025.core.platform;

public final class BybitProfile implements ExchangeProfile {

    @Override
    public String getName() {
        return "bybit";
    }

    @Override
    public String toExchangeSymbol(String asset, String quote) {
        // Bybit V5 Spot: BTCUSDT (Sin guiones)
        return (asset + quote).toUpperCase();
    }

    @Override
    public String toInternalSymbol(String exchangeSymbol) {
        return exchangeSymbol.toUpperCase();
    }

    @Override
    public double getBaseTakerFee() {
        return 0.001; // 0.1% Taker Base
    }

    @Override
    public boolean validateOrderRequirements(double price, double quantity) {
        // Bybit suele pedir notional > 1-5 USDT dependiendo del par.
        // Usamos 5.0 como estándar seguro.
        return (price * quantity) >= 5.0;
    }
}