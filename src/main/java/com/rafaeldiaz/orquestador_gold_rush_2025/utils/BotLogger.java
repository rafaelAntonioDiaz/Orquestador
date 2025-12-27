package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BotLogger {

    // 🎨 PALETA DE COLORES ANSI (Cyberpunk Theme)
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE_BOLD = "\u001B[1;37m";

    private static final Logger logger = Logger.getLogger("GoldRushBot");
    private static final String LOG_DIR = "logs";
    private static final String CSV_FILE = "logs/trades.csv";
    private static final String OPPORTUNITY_FILE = "logs/opportunities.csv";
    private static final OkHttpClient httpClient = new OkHttpClient();

    private static final Dotenv dotenv = Dotenv.configure().directory(System.getProperty("user.dir")).ignoreIfMissing().load();
    private static final String TOKEN = dotenv.get("TELEGRAM_BOT_TOKEN");
    private static final String CHAT_ID = dotenv.get("TELEGRAM_CHAT_ID");

    private static final BlockingQueue<Runnable> logTasks = new LinkedBlockingQueue<>();

    static {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) dir.mkdirs();

            // --- 1. FILE HANDLER (Sin colores, solo texto limpio) ---
            FileHandler fh = new FileHandler(LOG_DIR + "/bot.log", 10 * 1024 * 1024, 5, true);
            fh.setFormatter(new Formatter() {
                private static final String STANDARD_FORMAT = "[%1$tT] [%2$-7s] %3$s %n";
                @Override
                public synchronized String format(LogRecord lr) {
                    String msg = lr.getMessage();
                    // Limpieza simple para el archivo
                    if (isTableBorder(msg)) {
                        return msg + "\n";
                    }
                    return String.format(STANDARD_FORMAT, new java.util.Date(lr.getMillis()), lr.getLevel().getLocalizedName(), msg);
                }
            });
            logger.addHandler(fh);
            logger.setLevel(Level.INFO);
            logger.setUseParentHandlers(false);

            // --- 2. CONSOLE HANDLER (Con Colores y UI) ---
            ConsoleHandler ch = new ConsoleHandler();
            ch.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord lr) {
                    String msg = lr.getMessage();

                    // Si es parte de una tabla, imprimir CRUDO (sin fecha ni nivel)
                    // Usamos .contains para ignorar si empieza con códigos de color
                    if (isTableBorder(msg)) {
                        return msg + "\n";
                    }

                    // Coloreamos el nivel (INFO=Verde, WARN=Amarillo, ERROR=Rojo)
                    String color = GREEN;
                    if (lr.getLevel() == Level.WARNING) color = YELLOW;
                    if (lr.getLevel() == Level.SEVERE) color = RED;

                    // CORRECCIÓN CRÍTICA DE ÍNDICES:
                    // %1$s = Color
                    // %2$tT = Fecha (Time)
                    // %3$s = Reset
                    // %4$s = Mensaje
                    return String.format("%1$s[%2$tT]%3$s %4$s %n",
                            color, new java.util.Date(lr.getMillis()), RESET, msg);
                }
            });
            logger.addHandler(ch);

            initCSV();
            initOpportunityCSV();

            // --- 3. HILO ASÍNCRONO ---
            Thread consumerThread = new Thread(() -> {
                while (true) {
                    try {
                        Runnable task = logTasks.take();
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) { /* Silent */ }
                }
            });
            consumerThread.setName("Async-Log-Worker");
            consumerThread.setDaemon(true);
            consumerThread.start();

        } catch (IOException e) {
            System.err.println("FATAL LOG ERROR: " + e.getMessage());
        }
    }

    // Método auxiliar para detectar arte ASCII ignorando colores ANSI
    private static boolean isTableBorder(String msg) {
        return msg.contains("╔") || msg.contains("╚") || msg.contains("╠") || msg.contains("║") || msg.startsWith("\n");
    }

    // --- MÉTODOS PÚBLICOS ---

    public static void info(String msg) { logTasks.offer(() -> logger.info(msg)); }
    public static void warn(String msg) { logTasks.offer(() -> logger.warning(msg)); }
    public static void error(String msg) { logTasks.offer(() -> { logger.severe(msg); sendTelegram("🚨 ERROR: " + msg); }); }

    public static void logTrade(String pair, String type, double profitPercent, double amountUSDT) {
        logTasks.offer(() -> {
            initCSV();
            String date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE, true))) {
                pw.printf(Locale.US, "%s,%s,%s,%.4f,%.2f%n", date, pair, type, profitPercent, amountUSDT);
            } catch (IOException e) { logger.severe("CSV Error: " + e.getMessage()); }
            String msg = String.format(Locale.US, "💰 TRADE: %s | %s | P: %.2f%% | Vol: $%.2f", pair, type, profitPercent, amountUSDT);
            logger.info(msg);
            sendTelegram(msg);
        });
    }

    public static void logOpportunity(String type, String asset, String route, double grossGap, double netProfit, String status, String reason) {
        logTasks.offer(() -> {
            String date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            try (PrintWriter pw = new PrintWriter(new FileWriter(OPPORTUNITY_FILE, true))) {
                pw.printf(Locale.US, "%s,%s,%s,%s,%.4f,%.4f,%s,%s%n", date, type, asset, route, grossGap, netProfit, status, reason);
            } catch (IOException e) { logger.severe("Opp CSV Error: " + e.getMessage()); }
        });
    }

    public static void sendTelegram(String message) {
        if (TOKEN == null || TOKEN.isBlank() || CHAT_ID == null || CHAT_ID.isBlank()) return;
        Thread.ofVirtual().start(() -> {
            try {
                String url = "https://api.telegram.org/bot" + TOKEN.replace("\"", "").trim() + "/sendMessage";
                String jsonBody = String.format("{\"chat_id\": \"%s\", \"text\": \"%s\"}", CHAT_ID.replace("\"", "").trim(), message);
                RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
                Request request = new Request.Builder().url(url).post(body).build();
                httpClient.newCall(request).execute().close();
            } catch (Exception e) { /* Silent */ }
        });
    }

    private static void initCSV() {
        File f = new File(CSV_FILE);
        if (!f.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("Fecha,Par,Tipo,Profit_Percent,Volumen_USDT");
            } catch (IOException e) { logger.severe("No se pudo crear cabecera CSV"); }
        }
    }

    private static void initOpportunityCSV() {
        File f = new File(OPPORTUNITY_FILE);
        if (!f.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("Timestamp,Strategy,Asset,Route,Gross_Spread_Pct,Net_Profit_Usd,Status,Reason");
            } catch (IOException e) { logger.severe("No se pudo crear cabecera Opportunity CSV"); }
        }
    }
}