package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DashboardService {

    // Almacenamos historial reciente para las gráficas
    private final Deque<String> recentLogs = new ConcurrentLinkedDeque<>();
    private final Map<String, Long> latencies = new HashMap<>();
    private final Map<String, Deque<Long>> latencyHistory = new ConcurrentHashMap<>();
    private final int MAX_POINTS = 30;
    private double currentPnL = 0.0;
    private long totalCycles = 0;

    // Datos del Oráculo (Para graficar la "línea de disparo")
    private double lastOracleThreshold = 0.0;
    private double lastMaxSpread = 0.0;
    private String inventoryHtml = "";
    public void updateNetwork(Map<String, Long> latencies) {
        this.latencies.putAll(latencies);
        latencies.forEach((ex, ms) -> {
            latencyHistory.computeIfAbsent(ex, k -> new ConcurrentLinkedDeque<>()).offerLast(ms);
            if (latencyHistory.get(ex).size() > MAX_POINTS) latencyHistory.get(ex).pollFirst();
        });
    }

    public void updateStats(long cycles, double pnl) {
        this.totalCycles = cycles;
        this.currentPnL = pnl;
    }

    public void logOracleState(double maxSpreadFound, double thresholdRequired) {
        this.lastMaxSpread = maxSpreadFound;
        this.lastOracleThreshold = thresholdRequired;
    }

    public void addLog(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        recentLogs.offerFirst("[" + time + "] " + message);
        if (recentLogs.size() > 15) recentLogs.pollLast(); // Mantener solo últimos 15
    }

    public void generate() {
        try {
            String html = """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <meta http-equiv="refresh" content="2"> 
            <title>🗼 TOKYO COMMAND CENTER</title>
            <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
            <style>
                :root { --bg: #050505; --card: #111; --text: #0f0; --accent: #00ff9d; --danger: #ff0055; --warning: #ffcc00; }
                body { background: var(--bg); color: #ccc; font-family: 'Courier New', monospace; margin: 0; padding: 20px; }
                .header { display: flex; justify-content: space-between; border-bottom: 2px solid var(--accent); padding-bottom: 10px; margin-bottom: 20px; }
                .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px; }
                .card { background: var(--card); border: 1px solid #333; padding: 15px; border-radius: 5px; }
                .kpi { font-size: 32px; font-weight: bold; color: #fff; }
                .kpi-label { font-size: 11px; text-transform: uppercase; color: #777; margin-bottom: 8px; }
                table { width: 100%%; border-collapse: collapse; font-size: 11px; }
                td { padding: 4px; border-bottom: 1px solid #222; }
                .alert-fill { color: var(--danger); font-weight: bold; animation: blink 1s infinite; }
                @keyframes blink { 50%% { opacity: 0.5; } }
            </style>
        </head>
        <body>
            <div class="header">
                <div style="font-size: 24px; color: var(--accent); text-shadow: 0 0 10px var(--accent);">⚡ GOLD RUSH 2025: TOKYO NODE</div>
                <div>UPTIME: %s | CYCLES: %d</div>
            </div>

            <div class="grid">
                <div class="card" style="grid-row: span 2;">
                    <div class="kpi-label">💰 BÓVEDA DEL TESORO (INVENTARIO)</div>
                    <div style="max-height: 400px; overflow-y: auto;">
                        %s
                    </div>
                </div>

                <div class="card">
                    <div class="kpi-label">ESTIMATED PnL</div>
                    <div class="kpi" style="color: %s">$%.4f</div>
                </div>

                <div class="card">
                    <div class="kpi-label">LATENCIA DE RED (MS)</div>
                    <table>%s</table>
                </div>

                <div class="card" style="grid-column: span 2; height: 250px;">
                    <div class="kpi-label">📡 MONITOR DE FIBRA EN TIEMPO REAL</div>
                    <canvas id="latencyChart"></canvas>
                </div>

                <div class="card">
                    <div class="kpi-label">ÚLTIMO SPREAD VS REQUERIMIENTO</div>
                    <div style="display: flex; justify-content: space-between;">
                        <div><span style="font-size:20px; color:#fff;">%.4f%%</span></div>
                        <div style="text-align:right;"><span style="font-size:20px; color:var(--warning);">%.4f%%</span></div>
                    </div>
                    <div style="font-size:10px; color:#555; margin-top:5px;">Si Spread > Umbral = DISPARO 🔫</div>
                </div>
            </div>

            <div class="card" style="margin-top: 20px;">
                <div class="kpi-label">LOGS TÁCTICOS (CEMENTERIO DE OPORTUNIDADES)</div>
                <div style="font-family: monospace; color: #aaa; font-size: 11px; line-height: 1.4;">%s</div>
            </div>

            <script>
                const ctx = document.getElementById('latencyChart').getContext('2d');
                new Chart(ctx, {
                    type: 'line',
                    data: {
                        labels: Array.from({length: 30}, (_, i) => i),
                        datasets: [%s] 
                    },
                    options: {
                        responsive: true, maintainAspectRatio: false, animation: false,
                        scales: { y: { beginAtZero: true, grid: {color:'#222'} }, x: { display: false } },
                        elements: { point: { radius: 0 }, line: { borderWidth: 2, tension: 0.3 } },
                        plugins: { legend: { position: 'right', labels: { color: '#888', font: { size: 9 } } } }
                    }
                });
            </script>
        </body>
        </html>
        """.formatted(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    totalCycles,
                    inventoryHtml,                   // 1. Inyecta el inventario
                    currentPnL >= 0 ? "#00ff9d" : "#ff0055",
                    currentPnL,
                    generateLatencyRows(),
                    lastMaxSpread * 100,
                    lastOracleThreshold * 100,
                    String.join("<br>", recentLogs),
                    generateChartDatasets()         // 2. Inyecta los datos de la gráfica
            );

            Files.writeString(Path.of("dashboard.html"), html);
        } catch (Exception e) {
            BotLogger.error("Error Dashboard: " + e.getMessage());
        }
    }
    private String generateLatencyRows() {
        StringBuilder sb = new StringBuilder();
        latencies.forEach((ex, ms) -> {
            String color = ms < 50 ? "lat-good" : (ms < 150 ? "#ffcc00" : "lat-bad");
            sb.append("<tr><td>").append(ex.toUpperCase())
                    .append("</td><td class='").append(color).append("'>")
                    .append(ms).append(" ms</td></tr>");
        });
        return sb.toString();
    }
    private String generateChartDatasets() {
        StringBuilder sb = new StringBuilder();
        // Paleta de colores Neón: Verde, Rojo, Azul, Amarillo, Púrpura, Naranja
        String[] colors = {"#00ff9d", "#ff0055", "#58a6ff", "#ffcc00", "#9b59b6", "#e67e22"};
        int i = 0;

        for (Map.Entry<String, java.util.Deque<Long>> entry : latencyHistory.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("label: '").append(entry.getKey().toUpperCase()).append("',");
            // Deque.toString() devuelve [1, 2, 3], que es exactamente lo que JS espera
            sb.append("data: ").append(entry.getValue().toString()).append(",");
            sb.append("borderColor: '").append(colors[i % colors.length]).append("',");
            sb.append("backgroundColor: '").append(colors[i % colors.length]).append("22',"); // 22 = transparencia
            sb.append("borderWidth: 2,");
            sb.append("fill: false,");
            sb.append("tension: 0.4");
            sb.append("}");
            i++;
        }
        return sb.toString();
    }
    public void updateInventory(Map<String, Map<String, Double>> balances) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<tr style='color:var(--accent); font-size:10px;'><th>EX</th><th>ASSET</th><th style='text-align:right'>QTY</th></tr>");

        balances.forEach((ex, assets) -> {
            assets.forEach((asset, qty) -> {
                // Umbral de sugerencia: Si tiene menos de 0.001 o un valor muy bajo
                boolean lowBalance = qty < 0.1; // Ajuste este valor según el activo

                sb.append("<tr>");
                sb.append("<td style='color:#666'>").append(ex.substring(0, 2).toUpperCase()).append("</td>");
                sb.append("<td style='font-weight:bold'>").append(asset).append("</td>");

                if (lowBalance && !asset.equals("BNB") && !asset.equals("MX")) {
                    sb.append("<td style='text-align:right;' class='alert-fill'>⚠️ RECARGAR</td>");
                } else {
                    sb.append("<td style='text-align:right; color:#fff'>").append(String.format("%.4f", qty)).append("</td>");
                }
                sb.append("</tr>");
            });
        });
        sb.append("</table>");
        this.inventoryHtml = sb.toString();
    }}