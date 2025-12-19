package com.rafaeldiaz.orquestador_gold_rush_2025;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.MarketListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

public class RealMarketTest {

    @Test
    @DisplayName("EN VIVO: Escuchar mercado real por 20 segundos")
    void testLiveMarketFeed() throws InterruptedException {
        System.out.println("=================================================");
        System.out.println("   🚀 INICIANDO SISTEMA EN MODO LECTURA REAL 🚀  ");
        System.out.println("   Objetivo: Medir latencia de agregación total  ");
        System.out.println("=================================================");

        MarketListener listener = new MarketListener();
        listener.startScanning();

        // Dejar correr el hilo principal para ver los logs
        // Escucharemos durante 20 segundos (aprox 6-7 escaneos)
        TimeUnit.SECONDS.sleep(20);

        listener.stop();
        System.out.println("🛑 Test finalizado.");
    }
}