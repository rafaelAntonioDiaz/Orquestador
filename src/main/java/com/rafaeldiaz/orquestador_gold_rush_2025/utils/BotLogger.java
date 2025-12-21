package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;
import java.util.Locale;

public class BotLogger {

    private static final Logger logger = Logger.getLogger("GoldRushBot");
    private static final String LOG_DIR = "logs";
    private static final String CSV_FILE = "logs/trades.csv";
    private static final OkHttpClient httpClient = new OkHttpClient();

    // 🚀 MEJORA: Ruta absoluta garantizada para encontrar el .env
    private static final Dotenv dotenv = Dotenv.configure()
            .directory(System.getProperty("user.dir"))
            .ignoreIfMissing()
            .load();

    private static final String TOKEN = dotenv.get("TELEGRAM_BOT_TOKEN");
    private static final String CHAT_ID = dotenv.get("TELEGRAM_CHAT_ID");

    static {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) dir.mkdirs();
            FileHandler fh = new FileHandler(LOG_DIR + "/bot.log", 10 * 1024 * 1024, 5, true);
            fh.setFormatter(new SimpleFormatter() {
                private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";
                @Override
                public synchronized String format(LogRecord lr) {
                    return String.format(format, new java.util.Date(lr.getMillis()), lr.getLevel().getLocalizedName(), lr.getMessage());
                }
            });
            logger.addHandler(fh);
            logger.setLevel(Level.INFO);
            logger.setUseParentHandlers(true);
            initCSV();
        } catch (IOException e) {
            System.err.println("FATAL: No se pudo iniciar el sistema de logs: " + e.getMessage());
        }
    }

    public static void info(String msg) { logger.info(msg); }
    public static void warn(String msg) { logger.warning(msg); }
    public static void error(String msg) {
        logger.severe(msg);
        sendTelegram("🚨 ERROR CRÍTICO: " + msg);
    }

    public static void logTrade(String pair, String type, double profitPercent, double amountUSDT) {
        initCSV();
        String date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            pw.printf(Locale.US, "%s,%s,%s,%.4f,%.2f%n", date, pair, type, profitPercent, amountUSDT);
        } catch (IOException e) {
            logger.severe("Error escribiendo CSV: " + e.getMessage());
        }
        String msg = String.format(Locale.US, "💰 TRADE EXITOSO: %s | %s | Profit: %.4f%% | Vol: $%.2f", pair, type, profitPercent, amountUSDT);
        logger.info(msg);
        sendTelegram(msg);
    }

    public static void sendTelegram(String message) {
        // 1. Doble chequeo de existencia de llaves
        if (TOKEN == null || TOKEN.isBlank() || CHAT_ID == null || CHAT_ID.isBlank()) {
            System.err.println("❌ RADIO FUERA DE SERVICIO: Verifica el archivo .env en " + System.getProperty("user.dir"));
            return;
        }

        // 🏎️ Motor Java 25: Hilo Virtual para comunicaciones asíncronas
        Thread.ofVirtual().start(() -> {
            try {
                // 🛡️ LIMPIEZA QUIRÚRGICA: Eliminamos cualquier basura del token
                String cleanToken = TOKEN.trim().replaceAll("[\"']", "");
                String cleanChatId = CHAT_ID.trim().replaceAll("[\"']", "");

                String url = "https://api.telegram.org/bot" + cleanToken + "/sendMessage";
                String jsonBody = String.format("{\"chat_id\": \"%s\", \"text\": \"%s\"}", cleanChatId, message);

                RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder().url(url).post(body).build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        System.out.println("📡 [RADIO]: Mensaje transmitido con éxito.");
                    } else {
                        // Si falla, queremos ver el JSON de Telegram para saber por qué
                        String errorBody = response.body() != null ? response.body().string() : "Sin respuesta";
                        System.err.println("❌ Radio Error (HTTP " + response.code() + "): " + errorBody);
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Fallo físico de antena: " + e.getMessage());
            }
        });
    }

    private static void initCSV() {
        File f = new File(CSV_FILE);
        if (!f.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("Fecha,Par,Tipo,Profit_Percent,Volumen_USDT");
            } catch (IOException e) {
                logger.severe("No se pudo crear cabecera CSV");
            }
        }
    }
}