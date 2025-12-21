package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RadioTest {

    @Test
    @DisplayName("📡 TEST DE SUPERVIVENCIA: Radio Telegram")
    void testRadioCorta() throws InterruptedException {
        System.out.println("--- 🛰️ INICIANDO PRUEBA DE CAMPO DE RADIO ---");

        // Este mensaje DEBE llegar a tu celular si el .env está bien
        BotLogger.sendTelegram("🏁 [RADIO CHECK]: Rafael, la nave ha recuperado comunicaciones.");

        System.out.println("⏳ Esperando 5 segundos para confirmar transmisión...");
        Thread.sleep(5000);
        System.out.println("--- 🛰️ FIN DE PRUEBA. Verifica tu celular. ---");
    }
}