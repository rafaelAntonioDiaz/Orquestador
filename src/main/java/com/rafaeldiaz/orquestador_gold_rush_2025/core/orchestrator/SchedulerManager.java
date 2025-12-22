package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.ProfitAccountant;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchedulerManager {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExchangeConnector connector;
    private final ProfitAccountant accountant = new ProfitAccountant();
    // Umbral de pobreza (700 USD)
    private static final double MIN_BALANCE_THRESHOLD = 700.0;

    // Mapa de UIDs para rebalanceo (Cargado desde ENV)
    private final Map<String, String> accountUids = new HashMap<>();

    public SchedulerManager(ExchangeConnector connector) {
        this.connector = connector;
        loadUids();
    }

    private void loadUids() {
        // Cargamos los UIDs reales configurados en el entorno
        addUid("bybit_sub1", "BYBIT_SUB1_UID");
        addUid("bybit_sub2", "BYBIT_SUB2_UID");
        addUid("bybit_sub3", "BYBIT_SUB3_UID");
    }

    private void addUid(String exchangeId, String envKey) {
        String uid = System.getenv(envKey);
        if (uid != null && !uid.isBlank()) {
            accountUids.put(exchangeId, uid);
        } else {
            BotLogger.warn("⚠️ UID no configurado para " + exchangeId + " (Check " + envKey + ")");
        }
    }

    public void startHealthCheck() {
        BotLogger.info("⏰ Iniciando Scheduler de Salud Financiera...");

        // 1. Chequeo de Salud / Rebalanceo (Cada 6 min)
        scheduler.scheduleAtFixedRate(this::checkBalances, 0, 6, TimeUnit.MINUTES);

        // 2. Cierre Contable Diario (Cada 24 horas)
        // Task 2.4.3
        scheduler.scheduleAtFixedRate(this::performDailyAccounting, 0, 24, TimeUnit.HOURS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void checkBalances() {
        try {
            // Revisamos todas las cuentas registradas en Bybit
            double bal1 = getAndLogBalance("bybit_sub1");
            double bal2 = getAndLogBalance("bybit_sub2");
            double bal3 = getAndLogBalance("bybit_sub3");

            // Lógica de Rebalanceo Simplificada (Solo entre 1 y 2 por ahora para MVP)
            if (bal1 > 0 && bal2 > 0) {
                checkRebalanceNeed("bybit_sub1", bal1, "bybit_sub2", bal2);
            }

        } catch (Exception e) {
            BotLogger.error("Error en Health Check: " + e.getMessage());
        }
    }

    private double getAndLogBalance(String exchangeId) {
        try {
            // ¡YA NO HAY DUMMIES! Llamada real a la API
            double balance = connector.fetchBalance(exchangeId, "USDT");            BotLogger.info(String.format("💰 Balance Check [%s]: $%.2f USDT", exchangeId, balance));

            if (balance < MIN_BALANCE_THRESHOLD) {
                BotLogger.warn("⚠️ ALERTA: Saldo BAJO en " + exchangeId);
                BotLogger.sendTelegram("⚠️ Low Balance: " + exchangeId); // Descomentar en prod
            }
            return balance;
        } catch (IllegalArgumentException e) {
            // Si la cuenta no está configurada (ej. sub3), la ignoramos silenciosamente
            return -1.0;
        } catch (Exception e) {
            BotLogger.error("Fallo al leer balance de " + exchangeId + ": " + e.getMessage());
            return -1.0;
        }
    }

    private void checkRebalanceNeed(String idA, double balA, String idB, double balB) {
        double avg = (balA + balB) / 2.0;
        double threshold = avg * 0.30; // 30% desviación

        // A es Rico, B es Pobre
        if (balA > avg + threshold && balB < avg - threshold) {
            double amount = balA - avg;
            String targetUid = accountUids.get(idB);
            if (targetUid != null) performRebalance(idA, amount, targetUid);
        }
        // B es Rico, A es Pobre
        else if (balB > avg + threshold && balA < avg - threshold) {
            double amount = balB - avg;
            String targetUid = accountUids.get(idA);
            if (targetUid != null) performRebalance(idB, amount, targetUid);
        }
    }

    private void performDailyAccounting() {
        try {
            double total = 0.0;
            // Sumamos Bybit (Subs 1, 2, 3)
            total += Math.max(0, getAndLogBalance("bybit_sub1"));
            total += Math.max(0, getAndLogBalance("bybit_sub2"));
            total += Math.max(0, getAndLogBalance("bybit_sub3"));

            // Sumamos Cross Exchanges
            // Nota: getAndLogBalance retorna -1 si falla, por eso usamos Math.max(0, ...)
            total += Math.max(0, getAndLogBalance("mexc"));
            total += Math.max(0, getAndLogBalance("binance"));
            total += Math.max(0, getAndLogBalance("kucoin"));

            BotLogger.info("💰💰 CAPITAL TOTAL DEL SISTEMA: $" + total);

            // Delegamos al contador
            accountant.recordDailySnapshot(total);

        } catch (Exception e) {
            BotLogger.error("Error en Cierre Contable: " + e.getMessage());
        }
    }
    private void performRebalance(String fromId, double amount, String toUid) {
        BotLogger.info(String.format("⚖️ REBALANCEO REAL NECESARIO: Mover $%.2f de %s a UID %s", amount, fromId, toUid));
        // Aquí se descomentará la llamada real a transferFunds cuando estés listo para mover dinero
        // connector.transferFunds(fromId, amount, toUid);
    }

}