package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

public interface ExchangeStrategy {
    String getName();

    // Obtenemos los precios reales del libro de órdenes
    double fetchBid(String pair); // Precio al que venderíamos
    double fetchAsk(String pair); // Precio al que compraríamos

    // Peajes dinámicos según tu cuenta
    double getTradingFee(String pair);
    double getWithdrawalFee(String coin);
}