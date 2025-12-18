package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gestor de tareas periódicas (Cron Jobs).
 * Implementa User Story 2.4 (Rebalanceo y Monitoreo).
 */
public class SchedulerManager {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExchangeConnector connector;

    // Umbral de pobreza definido en Backlog Task 2.4.1 (700 USD)
    private static final double MIN_BALANCE_THRESHOLD = 700.0;

    public SchedulerManager(ExchangeConnector connector) {
        this.connector = connector;
    }

    /**
     * Inicia el monitoreo de saldos cada 6 minutos.
     */
    public void startHealthCheck() {
        BotLogger.info("⏰ Iniciando Scheduler de Salud Financiera...");

        // Ejecutar cada 6 minutos (Task 2.4.1)
        scheduler.scheduleAtFixedRate(this::checkBalances, 0, 6, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void checkBalances() {
        try {
            // Revisamos Bybit Sub 1 (Core)
            checkSubaccount("bybit_sub1");

            // Aquí agregaríamos sub2 y sub3 en el futuro
            // checkSubaccount("bybit_sub2");

        } catch (Exception e) {
            BotLogger.error("Error en Health Check: " + e.getMessage());
        }
    }

    private void checkSubaccount(String exchangeId) {
        try {
            double balance = connector.fetchBalance(exchangeId);

            // Log de rutina
            BotLogger.info(String.format("💰 Balance Check [%s]: $%.2f USDT", exchangeId, balance));

            // Verificación de Umbral (Task 2.4.1)
            if (balance < MIN_BALANCE_THRESHOLD) {
                String msg = String.format("⚠️ ALERTA: Saldo CRÍTICO en %s ($%.2f < $%.2f). Pausando operaciones.",
                        exchangeId, balance, MIN_BALANCE_THRESHOLD);

                BotLogger.warn(msg);
                BotLogger.sendTelegram(msg); // Disparamos alerta al móvil

                // TODO: Aquí llamaremos a un método para pausar el TradeExecutor en esa subcuenta
            }

        } catch (IOException e) {
            BotLogger.error("Fallo al leer balance de " + exchangeId + ": " + e.getMessage());
        }
    }
}