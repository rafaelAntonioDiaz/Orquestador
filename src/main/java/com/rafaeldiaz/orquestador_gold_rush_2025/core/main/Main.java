package com.rafaeldiaz.orquestador_gold_rush_2025.core.main;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner.DeepMarketScanner;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

public class Main {

    public static void main(String[] args) {

        BotLogger.info("======================================================");
        BotLogger.info("🔥 INICIANDO GOLD RUSH 2025 (PRODUCCIÓN)...");
        BotLogger.info("💰 CAPITAL SEMILLA: $" + BotConfig.SEED_CAPITAL);
        BotLogger.info("🛡️ MODO DRY-RUN: " + BotConfig.DRY_RUN);
        BotLogger.info("   Agente: ChasquiTokio | Modo: Corriente LOCAL");
        BotLogger.info("======================================================");

        // 1. CONECTOR BASE
        ExchangeConnector connector = new ExchangeConnector();
        BotLogger.info("✅ [1/4] Conector Central: ONLINE");

        // 2. SISTEMA SENSORIAL (OJOS Y OÍDOS)
        DeepMarketScanner scanner = new DeepMarketScanner(connector);

        // 3. Configurar Seguridad (Sincronizar con .env)
        scanner.setDryRun(BotConfig.DRY_RUN);

        // 4. ¡FUEGO! (Escaneo Infinito: 24 horas = 1440 minutos)
        // ✅ CORRECCIÓN: Línea descomentada
        scanner.startOmniScan(1440);

        BotLogger.info("✅ [SISTEMA INTEGRADO]: Agente corrigiendo el mercado ya !!!");

        // 5. MANTENER VIVO EL PROCESO
        // Esto evita que el programa termine inmediatamente y mantiene la consola abierta
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}