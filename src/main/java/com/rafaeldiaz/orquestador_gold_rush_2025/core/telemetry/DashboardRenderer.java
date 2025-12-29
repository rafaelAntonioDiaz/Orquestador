package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger; // Para constantes de color
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 🎨 RENDERIZADOR DE DASHBOARD (ASCII UI)
 * Transforma los datos crudos del MetricsService en un reporte visual de alto impacto.
 */
public class DashboardRenderer {

    private static final int WIDTH = 60;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static String render(MetricsService metrics) {
        StringBuilder sb = new StringBuilder();

        // Datos Temporales
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.ofInstant(metrics.getSessionStart(), ZoneId.systemDefault());
        Duration uptime = metrics.getUptime();
        String uptimeStr = String.format("%02dh %02dm %02ds", uptime.toHours(), uptime.toMinutesPart(), uptime.toSecondsPart());

        // 1. ENCABEZADO CON CONTEXTO TEMPORAL 🕒
        sb.append("\n");
        sb.append(BotLogger.CYAN).append("┌─────────────────────────────────────────────────────────┐").append(BotLogger.RESET).append("\n");
        sb.append(formatLine("📊 DASHBOARD OPERATIVO", ""));
        sb.append(BotLogger.CYAN).append("├─────────────────────────────────────────────────────────┤").append(BotLogger.RESET).append("\n");

        // Aquí está lo que pediste: INICIO vs AHORA
        sb.append(formatLine("🚀 Inicio: " + start.format(TIME_FMT), "⏱️ Run: " + uptimeStr));
        sb.append(formatLine("📅 Reporte: " + now.format(TIME_FMT), ""));

        sb.append(BotLogger.CYAN).append("├─────────────────────────────────────────────────────────┤").append(BotLogger.RESET).append("\n");

        // 2. LATENCIA Y RED ⚡
        Map<String, Double> latencies = metrics.getAvgLatencySnapshot();
        Map<String, Long> errors = metrics.getErrorsSnapshot();

        latencies.forEach((exch, ms) -> {
            String bar = drawBar(ms, 300); // 300ms es el "tope" de la barra roja
            String status = (ms < 150) ? "✅" : (ms < 500 ? "⚠️" : "🐢");
            long errCount = errors.getOrDefault(exch, 0L);
            String errStr = errCount > 0 ? " (Err: " + errCount + ")" : "";

            String label = String.format("⚡ %-7s %3.0fms %s %s", exch, ms, bar, status);
            sb.append(formatLineContent(label + errStr)).append("\n");
        });

        sb.append(BotLogger.CYAN).append("├─────────────────────────────────────────────────────────┤").append(BotLogger.RESET).append("\n");

        // 3. NEGOCIO Y RENDIMIENTO 💰
        long ops = metrics.getThroughputTotal();
        double opsPerSec = ops / (uptime.toSeconds() < 1 ? 1 : uptime.toSeconds());

        sb.append(formatLine(String.format("🔄 Throughput: %d ops (%.1f/s)", ops, opsPerSec), ""));

        long trades = metrics.getTradeCount();
        if (trades > 0) {
            double winRate = (double) metrics.getWinCount() / trades * 100.0;
            String pnlColor = metrics.getPnL() >= 0 ? BotLogger.GREEN : BotLogger.RED;
            String pnlStr = String.format("%s$%.2f%s", pnlColor, metrics.getPnL(), BotLogger.RESET);

            sb.append(formatLine("💰 PnL Sesión: " + pnlStr, "🎯 Win: " + String.format("%.0f%%", winRate)));
        } else {
            sb.append(formatLine("💰 PnL Sesión: $0.00", "💤 (Sin Trades)"));
        }

        // Memoria
        long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
        long maxMem = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        sb.append(formatLine(String.format("🧠 Memoria: %dMB / %dMB", usedMem, maxMem), ""));

        sb.append(BotLogger.CYAN).append("└─────────────────────────────────────────────────────────┘").append(BotLogger.RESET).append("\n");

        return sb.toString();
    }

    // --- HELPERS DE DIBUJO ---

    private static String formatLine(String left, String right) {
        // Cálculo manual para padding ASCII perfecto
        int padding = WIDTH - 4 - cleanLen(left) - cleanLen(right); // -4 por los bordes y espacios
        if (padding < 0) padding = 0;
        return BotLogger.CYAN + "│ " + BotLogger.RESET + left + " ".repeat(padding) + right + BotLogger.CYAN + " │" + BotLogger.RESET + "\n";
    }

    private static String formatLineContent(String content) {
        int padding = WIDTH - 3 - cleanLen(content);
        if (padding < 0) padding = 0;
        return BotLogger.CYAN + "│ " + BotLogger.RESET + content + " ".repeat(padding) + BotLogger.CYAN + "│" + BotLogger.RESET;
    }

    private static int cleanLen(String s) {
        // Elimina códigos ANSI para calcular longitud visual real
        return s.replaceAll("\u001B\\[[;\\d]*m", "").length();
    }

    private static String drawBar(double val, double max) {
        int barLen = 10;
        int filled = (int) ((val / max) * barLen);
        if (filled > barLen) filled = barLen;

        String color = BotLogger.GREEN;
        if (filled > 3) color = BotLogger.YELLOW;
        if (filled > 7) color = BotLogger.RED;

        return color + "[" + "█".repeat(filled) + "░".repeat(barLen - filled) + "]" + BotLogger.RESET;
    }
}