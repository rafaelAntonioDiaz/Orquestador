package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import okhttp3.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * Sistema centralizado de Logs, Reportes CSV y Alertas Telegram.
 * Cubre User Story 2.3 del Backlog.
 */
public class BotLogger {

    private static final Logger logger = Logger.getLogger("GoldRushBot");
    private static final String LOG_DIR = "logs";
    private static final String CSV_FILE = "logs/trades.csv";

    // Configuración Telegram (Task 2.3.3)
    private static final String TG_API_URL = "https://api.telegram.org/bot%s/sendMessage";
    private static final OkHttpClient httpClient = new OkHttpClient();

    static {
        try {
            // 1. Crear directorio de logs si no existe
            File dir = new File(LOG_DIR);
            if (!dir.exists()) dir.mkdirs();

            // 2. Configurar FileHandler (Task 2.3.1)
            // Rotación: 10MB, máx 5 archivos. Append mode = true.
            FileHandler fh = new FileHandler(LOG_DIR + "/bot.log", 10 * 1024 * 1024, 5, true);
            fh.setFormatter(new SimpleFormatter() {
                private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";
                @Override
                public synchronized String format(LogRecord lr) {
                    return String.format(format,
                            new java.util.Date(lr.getMillis()),
                            lr.getLevel().getLocalizedName(),
                            lr.getMessage()
                    );
                }
            });

            logger.addHandler(fh);
            logger.setLevel(Level.INFO);
            logger.setUseParentHandlers(true); // También mostrar en consola

            // 3. Inicializar CSV con cabeceras si es nuevo (Task 2.3.2)
            initCSV();

        } catch (IOException e) {
            System.err.println("FATAL: No se pudo iniciar el sistema de logs: " + e.getMessage());
        }
    }

    // --- MÉTODOS PÚBLICOS DE LOGGING ---

    public static void info(String msg) {
        logger.info(msg);
    }

    public static void warn(String msg) {
        logger.warning(msg);
    }

    public static void error(String msg) {
        logger.severe(msg);
        // Los errores críticos también van a Telegram
        sendTelegram("🚨 ERROR CRÍTICO: " + msg);
    }

    /**
     * Registra un trade completado en el CSV y avisa por Telegram.
     * Task 2.3.2 y 2.3.3
     */
    public static void logTrade(String pair, String type, double profitPercent, double amountUSDT) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 1. Escribir en CSV
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            // Formato: Fecha, Par, Tipo, Profit%, Volumen
            pw.printf("%s,%s,%s,%.4f,%.2f%n", date, pair, type, profitPercent, amountUSDT);
        } catch (IOException e) {
            logger.severe("Error escribiendo CSV: " + e.getMessage());
        }

        // 2. Log normal
        String msg = String.format("💰 TRADE EXITOSO: %s | %s | Profit: %.4f%% | Vol: $%.2f",
                pair, type, profitPercent, amountUSDT);
        logger.info(msg);

        // 3. Alerta Telegram
        sendTelegram(msg);
    }

    // --- TELEGRAM (Task 2.3.3) ---

    public static void sendTelegram(String message) {
        String token = System.getenv("TELEGRAM_BOT_TOKEN");
        String chatId = System.getenv("TELEGRAM_CHAT_ID");

        if (token == null || chatId == null) {
            logger.warning("Telegram no configurado (Faltan env vars). Mensaje no enviado.");
            return;
        }

        // Ejecutar en hilo separado para no bloquear el trading
        new Thread(() -> {
            try {
                String url = String.format(TG_API_URL, token);
                String jsonBody = String.format("{\"chat_id\": \"%s\", \"text\": \"%s\"}", chatId, message);

                RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
                Request request = new Request.Builder().url(url).post(body).build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        System.err.println("Error enviando Telegram: " + response.code());
                    }
                }
            } catch (Exception e) {
                System.err.println("Fallo al enviar Telegram: " + e.getMessage());
            }
        }).start();
    }

    private static void initCSV() {
        File f = new File(CSV_FILE);
        if (!f.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("Fecha,Par,Tipo,Profit_Percent,Volumen_USDT"); // Cabecera
            } catch (IOException e) {
                logger.severe("No se pudo crear cabecera CSV");
            }
        }
    }
}