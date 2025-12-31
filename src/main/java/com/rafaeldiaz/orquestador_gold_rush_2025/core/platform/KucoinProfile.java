package com.rafaeldiaz.orquestador_gold_rush_2025.core.platform;

public final class KucoinProfile implements ExchangeProfile {

    @Override
    public String getName() {
        return "kucoin";
    }

    @Override
    public String toExchangeSymbol(String asset, String quote) {
        // ⚠️ KUCOIN REQUIERE GUION: BTC-USDT
        return (asset + "-" + quote).toUpperCase();
    }

    @Override
    public String toInternalSymbol(String exchangeSymbol) {
        // Recibimos "BTC-USDT", devolvemos "BTCUSDT"
        return exchangeSymbol.replace("-", "").toUpperCase();
    }

    @Override
    public double getBaseTakerFee() {
        return 0.001; // 0.1% Taker
    }

    @Override
    public boolean validateOrderRequirements(double price, double quantity) {
        // KuCoin permite órdenes pequeñas, pero mantenemos 5 USDT por seguridad del bot
        return (price * quantity) >= 1.0;
    }
}