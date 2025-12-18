package com.rafaeldiaz.orquestador_gold_rush_2025;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.BybitStreamer;

public class WSTest {
    public static void main(String[] args) throws InterruptedException {
        // Creamos el streamer con un listener que imprime en consola
        BybitStreamer streamer = new BybitStreamer((exchange, pair, price, ts) -> {
            System.out.println("⚡ PUMP! [" + exchange + "] " + pair + " : " + price + " @ " + ts);
        });

        streamer.connect();

        // Esperamos un segundo a que conecte y nos suscribimos
        Thread.sleep(1000);
        streamer.subscribe("BTCUSDT");
        streamer.subscribe("SOLUSDT");

        // Dejamos vivo el programa 30 segundos para ver los datos
        Thread.sleep(30000);

        streamer.disconnect();
        System.out.println("Test finalizado.");
    }
}