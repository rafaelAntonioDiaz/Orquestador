package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Task 2.4.3: Contabilidad y Cálculo de Ganancias Diarias.
 * Mantiene un historial persistente de los saldos totales.
 */
public class ProfitAccountant {

    private static final String HISTORY_FILE = "logs/balance_history.csv";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public ProfitAccountant() {
        ensureFileExists();
    }

    /**
     * Registra el saldo total actual y calcula la ganancia respecto al último registro.
     * @param totalBalance Saldo total sumado de todas las cuentas.
     */
    public void recordDailySnapshot(double totalBalance) {
        double lastBalance = getLastRecordedBalance();
        double profit = 0.0;

        // Si es la primera vez que corre (lastBalance = -1), no hay profit calculado aún
        if (lastBalance >= 0) {
            profit = totalBalance - lastBalance;
        }

        String today = LocalDate.now().format(DATE_FMT);

        // Logueamos la contabilidad
        if (profit != 0) {
            String emoji = profit > 0 ? "📈" : "📉";
            String msg = String.format("%s PnL Diario: %s$%.2f (Anterior: $%.2f -> Actual: $%.2f)",
                    emoji, profit > 0 ? "+" : "", profit, lastBalance, totalBalance);
            BotLogger.info(msg);
            BotLogger.sendTelegram(msg);
        } else {
            BotLogger.info(String.format("📊 Snapshot Diario: $%.2f (Sin cambios o primer registro)", totalBalance));
        }

        // Guardar en CSV
        appendToFile(today, totalBalance, profit);
    }

    private void ensureFileExists() {
        File f = new File(HISTORY_FILE);
        if (!f.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("Date,TotalBalance,DailyProfit"); // Header
            } catch (IOException e) {
                BotLogger.error("No se pudo crear historial de balances: " + e.getMessage());
            }
        }
    }

    private double getLastRecordedBalance() {
        double lastBal = -1.0;
        File f = new File(HISTORY_FILE);
        if (!f.exists()) return -1.0;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            String lastLine = null;
            // Leer hasta el final
            while ((line = br.readLine()) != null) {
                lastLine = line;
            }

            if (lastLine != null && !lastLine.startsWith("Date")) {
                // Formato: Date,TotalBalance,DailyProfit
                String[] parts = lastLine.split(",");
                if (parts.length >= 2) {
                    lastBal = Double.parseDouble(parts[1]);
                }
            }
        } catch (Exception e) {
            BotLogger.warn("Error leyendo historial previo: " + e.getMessage());
        }
        return lastBal;
    }

    private void appendToFile(String date, double balance, double profit) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(HISTORY_FILE, true))) {
            // FIX: Forzamos Locale.US para que use PUNTO (.) como decimal
            // Así evitamos romper el formato CSV
            pw.printf(Locale.US, "%s,%.2f,%.2f%n", date, balance, profit);
        } catch (IOException e) {
            BotLogger.error("Error guardando snapshot: " + e.getMessage());
        }
    }
}