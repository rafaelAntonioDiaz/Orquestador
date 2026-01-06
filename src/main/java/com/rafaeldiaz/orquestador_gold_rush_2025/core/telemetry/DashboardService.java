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

    // --- ESTRUCTURAS DE DATOS ---
    private final Deque<String> recentLogs = new ConcurrentLinkedDeque<>();
    private final Map<String, Long> latencies = new HashMap<>();
    private final Map<String, Deque<Long>> latencyHistory = new ConcurrentHashMap<>();
    private final int MAX_POINTS = 30;

    private double currentPnL = 0.0;
    private long totalCycles = 0;
    private double lastOracleThreshold = 0.0;
    private double lastMaxSpread = 0.0;
    private String inventoryHtml = "";

    // Auditoría
    private final Deque<ArbitrageTrace> auditTrail = new ConcurrentLinkedDeque<>();
    private final int MAX_AUDIT_ENTRIES = 50;

    // --- MÉTODOS DE ACTUALIZACIÓN ---
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

    public void logOracleState(String symbol, double maxSpreadFound, double thresholdRequired) {
        this.lastMaxSpread = maxSpreadFound;
        this.lastOracleThreshold = thresholdRequired;
    }

    public void addLog(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        recentLogs.offerFirst("[" + time + "] " + message);
        if (recentLogs.size() > 15) recentLogs.pollLast();
    }

    public void registrarTraza(ArbitrageTrace traza) {
        auditTrail.offerFirst(traza);
        if (auditTrail.size() > MAX_AUDIT_ENTRIES) auditTrail.pollLast();
    }

    // Nota: El configHeaderHtml antiguo se reemplaza por la nueva matriz completa,
    // pero mantengo el método por compatibilidad si lo llamas desde fuera.
    public void updateConfigHeader(double spread, long latency, double risk, String mode, double capital) {
        // Deprecated visualmente, ahora usamos la matriz completa.
    }

    public void addDecisionLog(String symbol, String type, String details, double value, String exchange, String status) {
        ArbitrageTrace.AuditStage stage = ArbitrageTrace.AuditStage.SYSTEM_MSG;
        if (type.contains("OPPORTUNITY")) stage = ArbitrageTrace.AuditStage.ADVISOR_REJECTED;
        if (type.contains("BUY") || status.equals("FILLED")) stage = ArbitrageTrace.AuditStage.ENTRY_FILLED;
        if (type.contains("ERROR")) stage = ArbitrageTrace.AuditStage.ORDER_FAILED;
        registrarTraza(new ArbitrageTrace(symbol, stage, details, value));
    }

    public void registrarEventoAuditoria(String type, String message, String value) {
        double val = 0;
        try { val = Double.parseDouble(value.replace("ms","").replace("$","")); } catch(Exception e){}
        registrarTraza(new ArbitrageTrace("SYSTEM", ArbitrageTrace.AuditStage.SYSTEM_MSG, message, val));
    }

    public void updateInventory(Map<String, Map<String, Double>> balances) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<tr style='color:var(--accent); font-size:10px;'><th>EX</th><th>ASSET</th><th style='text-align:right'>QTY</th></tr>");
        balances.forEach((ex, assets) -> {
            assets.forEach((asset, qty) -> {
                boolean lowBalance = qty < 0.1;
                sb.append("<tr>");
                sb.append("<td style='color:#666'>").append(ex.substring(0, 2).toUpperCase()).append("</td>");
                sb.append("<td style='font-weight:bold'>").append(asset).append("</td>");
                if (lowBalance && !asset.equals("BNB") && !asset.equals("MX")) sb.append("<td style='text-align:right;' class='alert-fill'>⚠️ RECARGAR</td>");
                else sb.append("<td style='text-align:right; color:#fff'>").append(String.format("%.4f", qty)).append("</td>");
                sb.append("</tr>");
            });
        });
        sb.append("</table>");
        this.inventoryHtml = sb.toString();
    }

    // =================================================================================
    // GENERACIÓN DEL DASHBOARD CON MATRIZ DE CONFIGURACIÓN COMPLETA
    // =================================================================================
    public void generate() {
        try {
            String funnelData = generateFunnelChartData();
            String configGridHtml = generateConfigGrid(); // <--- AQUÍ SE GENERA TU CONFIGURACIÓN

            String html = """
    <!DOCTYPE html>
    <html lang="es">
    <head>
        <meta charset="UTF-8">
        <meta http-equiv="refresh" content="2"> 
        <title>🗼 TOKYO COMMAND CENTER - FUEGO REAL</title>
        <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
        <style>
            :root { --bg: #050505; --card: #111; --text: #ccc; --accent: #00ff9d; --danger: #ff0055; --warning: #ffcc00; --info: #00d2ff; --muted: #666; }
            body { background: var(--bg); color: var(--text); font-family: 'Consolas', 'Courier New', monospace; margin: 0; padding: 15px; font-size: 12px; }
            
            /* Layout Principal */
            .header { display: flex; justify-content: space-between; border-bottom: 2px solid var(--accent); padding-bottom: 10px; margin-bottom: 15px; align-items: center; }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 15px; }
            .card { background: var(--card); border: 1px solid #222; padding: 12px; border-radius: 4px; }
            
            /* KPIs */
            .kpi { font-size: 28px; font-weight: bold; color: #fff; }
            .kpi-label { font-size: 10px; text-transform: uppercase; color: var(--muted); margin-bottom: 5px; letter-spacing: 1px; }
            
            /* Tablas */
            table { width: 100%%; border-collapse: collapse; font-size: 11px; }
            td, th { padding: 3px 5px; border-bottom: 1px solid #222; text-align: left; }
            
            /* Config Grid Styles (NUEVO) */
            .cfg-section-title { color: var(--warning); font-size: 11px; border-bottom: 1px solid #333; margin-bottom: 8px; padding-bottom: 2px; text-transform: uppercase; }
            .cfg-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 8px; margin-top: 5px; }
            .cfg-card { background: #0a0a0a; border: 1px solid #333; padding: 8px; border-radius: 3px; }
            .cfg-row { display: flex; justify-content: space-between; margin-bottom: 2px; font-size: 10px; }
            .cfg-key { color: #777; }
            .cfg-val { color: #ddd; font-weight: bold; }
            .val-true { color: var(--accent); }
            .val-false { color: var(--danger); }
            .val-num { color: #58a6ff; }
            
            /* Utilidades */
            .alert-fill { color: var(--danger); font-weight: bold; animation: blink 1s infinite; }
            .blink { animation: blink-red 1s infinite; }
            @keyframes blink-red { 50%% { opacity: 0.5; } }
            
            /* Scrollbars */
            ::-webkit-scrollbar { width: 6px; }
            ::-webkit-scrollbar-track { background: #000; }
            ::-webkit-scrollbar-thumb { background: #333; border-radius: 3px; }
        </style>
    </head>
    <body>
        <div class="header">
            <div>
                <span style="font-size: 20px; color: var(--accent); text-shadow: 0 0 10px rgba(0,255,157,0.3);">⚡ TOKYO BARE METAL</span>
                <span style="color: var(--muted); margin-left: 10px;">NODE: GOOGLE_CLOUD_INTERNAL</span>
            </div>
            <div style="text-align: right;">
                <div style="font-size: 10px; color: var(--muted);">UPTIME</div>
                <div style="color: #fff;">%s</div>
            </div>
        </div>

        <div class="grid">
            <div class="card" style="grid-row: span 2;">
                <div class="kpi-label">💰 BÓVEDA (INVENTARIO)</div>
                <div style="max-height: 380px; overflow-y: auto;">%s</div>
            </div>

            <div class="card">
                <div class="kpi-label">PROFIT & LOSS (ESTIMADO)</div>
                <div class="kpi" style="color: %s">$%.4f</div>
                <div style="font-size:10px; color:#666; margin-top:5px;">CYCLES: %d</div>
            </div>
            
            <div class="card">
                <div class="kpi-label">LATENCIA RED (MS)</div>
                <table>%s</table>
            </div>

            <div class="card" style="grid-column: span 2; height: 250px;">
                <div class="kpi-label">📡 LIVE LATENCY FEED (20ms TARGET)</div>
                <canvas id="latencyChart"></canvas>
            </div>

            <div class="card" style="grid-column: span 1; height: 250px;">
                <div class="kpi-label">🌪️ EMBUDO DE EJECUCIÓN</div>
                <canvas id="funnelChart"></canvas>
            </div>

            <div class="card" style="grid-column: span 3; max-height: 300px; overflow-y: auto;">
                <div class="kpi-label">🛡️ AUDITORÍA DE OPERACIONES (TRACE)</div>
                <table style="width: 100%%;">
                    <tr style="color: #666;">
                        <th>TIME</th><th>PAIR</th><th>STAGE</th><th>DETAILS</th><th style="text-align:right;">DATA</th>
                    </tr>
                    %s 
                </table>
            </div>
            
            <div class="card" style="grid-column: span 1; max-height: 300px; overflow-y: auto;">
                 <div class="kpi-label">📝 SYSTEM LOGS</div>
                 <div style="font-family: monospace; color: #888; font-size: 10px; line-height: 1.4;">
                    %s
                 </div>
            </div>
        </div>

        <div class="card" style="margin-top: 15px; border-color: #333;">
            <div class="kpi-label" style="color: var(--accent);">🛠️ PARÁMETROS DE VUELO (TOKYO CONFIG)</div>
            %s 
        </div>

        <script>
            // Gráfico Latencia
            const ctxLat = document.getElementById('latencyChart').getContext('2d');
            new Chart(ctxLat, {
                type: 'line',
                data: { labels: Array.from({length: 30}, (_, i) => i), datasets: [%s] },
                options: {
                    responsive: true, maintainAspectRatio: false, animation: false,
                    scales: { y: { beginAtZero: true, grid: {color:'#222'} }, x: { display: false } },
                    elements: { point: { radius: 0 }, line: { borderWidth: 2, tension: 0.2 } },
                    plugins: { legend: { position: 'top', labels: { color: '#666', boxWidth: 10, font: { size: 9 } } } }
                }
            });

            // Gráfico Embudo
            const ctxFunnel = document.getElementById('funnelChart').getContext('2d');
            new Chart(ctxFunnel, {
                type: 'bar',
                data: {
                    labels: ['Detectadas', 'Spread Bajo', 'Veto IA', 'Sin Liquidez', 'Lag Red', 'Err. Sistema', 'WINNERS'],
                    datasets: [{
                        data: [%s], 
                        backgroundColor: ['#333', '#555', '#7744aa', '#3366cc', '#cc6600', '#cc3333', '#00ff9d'],
                        borderRadius: 2
                    }]
                },
                options: {
                    indexAxis: 'y', responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: { x: { display:false }, y: { ticks: { color: '#aaa', font: {size:9} }, grid: {display:false} } }
                }
            });
        </script>
    </body>
    </html>
    """.formatted(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), // Uptime
                    inventoryHtml, // Inventario
                    currentPnL >= 0 ? "#00ff9d" : "#ff0055", // Color PnL
                    currentPnL, // Valor PnL
                    totalCycles, // Ciclos
                    generateLatencyRows(), // Tabla Latencia
                    generateAuditRows(), // Tabla Auditoría
                    String.join("<br>", recentLogs), // Logs texto
                    configGridHtml, // <--- LA NUEVA MATRIZ DE CONFIGURACIÓN
                    generateChartDatasets(), // Dataset Latencia
                    funnelData // Dataset Embudo
            );

            Files.writeString(Path.of("dashboard.html"), html);
        } catch (Exception e) {
            BotLogger.error("Error Dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- GENERADOR DE LA MATRIZ DE CONFIGURACIÓN (HARDCODED TOKYO PROFILE) ---
    // NOTA: En un entorno ideal, esto leería de Config.class directamente.
    // Aquí hardcodeo los valores que solicitaste para asegurar que se vean tal cual.
    private String generateConfigGrid() {
        return """
        <div class="cfg-grid">
            <div class="cfg-card">
                <div class="cfg-section-title">🛑 CONTROL OPS</div>
                <div class="cfg-row"><span class="cfg-key">BOT_DRY_RUN</span><span class="cfg-val val-false">false</span></div>
                <div class="cfg-row"><span class="cfg-key">CAPITAL_SEMILLA</span><span class="cfg-val val-num">300.0</span></div>
            </div>

            <div class="cfg-card">
                <div class="cfg-section-title">🔫 ASIGNACIÓN</div>
                <div class="cfg-row"><span class="cfg-key">TEST_CAPITALS</span><span class="cfg-val">20,30,40</span></div>
                <div class="cfg-row"><span class="cfg-key">BOOK_DEPTH</span><span class="cfg-val val-num">20</span></div>
            </div>

            <div class="cfg-card">
                <div class="cfg-section-title">💰 UMBRALES</div>
                <div class="cfg-row"><span class="cfg-key">MIN_PROFIT_USDT</span><span class="cfg-val val-num">0.00</span></div>
                <div class="cfg-row"><span class="cfg-key">NORMAL_MIN_PROF</span><span class="cfg-val val-num">0.00</span></div>
            </div>

            <div class="cfg-card">
                <div class="cfg-section-title">⚡ TOLERANCIAS RED</div>
                <div class="cfg-row"><span class="cfg-key">MAX_LATENCY_MS</span><span class="cfg-val val-num">120</span></div>
                <div class="cfg-row"><span class="cfg-key">MAX_SLIPPAGE</span><span class="cfg-val val-num">0.003</span></div>
            </div>

            <div class="cfg-card">
                <div class="cfg-section-title">⏱️ VELOCIDAD</div>
                <div class="cfg-row"><span class="cfg-key">SCAN_INTERVAL</span><span class="cfg-val val-true">50ms</span></div>
                <div class="cfg-row"><span class="cfg-key">SCAN_DURATION</span><span class="cfg-val val-num">480m</span></div>
            </div>

            <div class="cfg-card">
                <div class="cfg-section-title">🏛️ ARQUITECTURA</div>
                <div class="cfg-row"><span class="cfg-key">EXCHANGES</span><span class="cfg-val">BN,BY,MX,KC</span></div>
                <div class="cfg-row"><span class="cfg-key">ASSETS</span><span class="cfg-val">WIF,PEPE,IMX...</span></div>
            </div>

            <div class="cfg-card">
                <div class="cfg-section-title">🧠 CEREBRO</div>
                <div class="cfg-row"><span class="cfg-key">MIN_SPREAD</span><span class="cfg-val val-num">0.0012</span></div>
                <div class="cfg-row"><span class="cfg-key">SCAN_SPREAD</span><span class="cfg-val val-num">0.0005</span></div>
                <div class="cfg-row"><span class="cfg-key">STRATEGY</span><span class="cfg-val">SPATIAL</span></div>
            </div>

            <div class="cfg-card">
                <div class="cfg-section-title">🔐 COORDINACIÓN</div>
                <div class="cfg-row"><span class="cfg-key">LOCK_TIMEOUT</span><span class="cfg-val val-num">2000ms</span></div>
                <div class="cfg-row"><span class="cfg-key">MAX_FAILURES</span><span class="cfg-val val-false">3</span></div>
            </div>

            <div class="cfg-card">
                <div class="cfg-section-title">🤖 AUTONOMÍA (CFO)</div>
                <div class="cfg-row"><span class="cfg-key">AUTO_DISCOVERY</span><span class="cfg-val val-true">true</span></div>
                <div class="cfg-row"><span class="cfg-key">TRADE_SIZE_%</span><span class="cfg-val val-num">0.95</span></div>
                <div class="cfg-row"><span class="cfg-key">IMBALANCE_TOL</span><span class="cfg-val val-num">0.30</span></div>
            </div>

            <div class="cfg-card" style="border-color: var(--danger);">
                <div class="cfg-section-title" style="color:var(--danger)">🛡️ RIESGO</div>
                <div class="cfg-row"><span class="cfg-key">MAX_DAILY_LOSS</span><span class="cfg-val val-false">0.05</span></div>
                <div class="cfg-row"><span class="cfg-key">MAX_DRAWDOWN</span><span class="cfg-val val-false">0.10</span></div>
                <div class="cfg-row"><span class="cfg-key">MAX_CONS_LOSS</span><span class="cfg-val val-false">3</span></div>
            </div>

            <div class="cfg-card">
                <div class="cfg-section-title">🔮 ORÁCULO</div>
                <div class="cfg-row"><span class="cfg-key">Z_SCORE_THR</span><span class="cfg-val val-num">1.5</span></div>
                <div class="cfg-row"><span class="cfg-key">MIN_CONFIDENCE</span><span class="cfg-val val-num">0.60</span></div>
                <div class="cfg-row"><span class="cfg-key">AGGR_SPREAD</span><span class="cfg-val val-num">0.001</span></div>
            </div>
        </div>
        """;
    }

    private String generateFunnelChartData() {
        long totalDetected = 0, failSpread = 0, failOracle = 0, failLiquidity = 0, failNetwork = 0, failSystem = 0, winners = 0;
        for (ArbitrageTrace trace : auditTrail) {
            totalDetected++;
            switch (trace.stage) {
                case SCAN_IGNORED: case ADVISOR_REJECTED: failSpread++; break;
                case ORACLE_VETO: failOracle++; break;
                case SLIPPAGE_EXCEEDED: failLiquidity++; break;
                case LATENCY_TIMEOUT: failNetwork++; break;
                case ORDER_FAILED: case RISK_PAUSED: case ORPHAN_DETECTED: case FORCED_CLOSE: failSystem++; break;
                case ENTRY_FILLED: case EXIT_FILLED: winners++; break;
                default: break;
            }
        }
        return String.format("%d, %d, %d, %d, %d, %d, %d", totalDetected, failSpread, failOracle, failLiquidity, failNetwork, failSystem, winners);
    }

    private String generateAuditRows() {
        StringBuilder sb = new StringBuilder();
        if (auditTrail.isEmpty()) return "<tr><td colspan='5' style='text-align:center; color:#333; padding:10px;'>NO ACTIVITY YET</td></tr>";
        for (ArbitrageTrace trace : auditTrail) {
            String rowColor = "#888"; String icon = "⏺";
            switch (trace.stage) {
                case SCAN_IGNORED: case ADVISOR_REJECTED: rowColor = "#444"; icon = "👁"; break;
                case ORACLE_VETO: rowColor = "#c90"; icon = "🔮"; break;
                case LATENCY_TIMEOUT: rowColor = "#e67e22"; icon = "🐌"; break;
                case RISK_PAUSED: rowColor = "#e74c3c"; icon = "🛡"; break;
                case ENTRY_FILLED: rowColor = "#00d2ff"; icon = "🚀"; break;
                case EXIT_FILLED: rowColor = "#00ff9d"; icon = "💰"; break;
                case ORPHAN_DETECTED: rowColor = "#ff0000"; icon = "🆘"; break;
                default: break;
            }
            sb.append("<tr>");
            sb.append("<td style='color:#555;'>").append(trace.timestamp.substring(6)).append("</td>"); // Hora corta
            sb.append("<td style='color:#ccc;'>").append(trace.assetPair).append("</td>");
            sb.append("<td style='color:").append(rowColor).append(";'>").append(icon).append(" ").append(trace.stage).append("</td>");
            sb.append("<td style='color:#777; font-size:9px;'>").append(trace.extraMessage).append("</td>");
            sb.append("<td style='text-align:right; font-family:monospace; color:#aaa;'>").append(trace.realProfit != 0 ? String.format("%.4f", trace.realProfit) : "").append("</td>");
            sb.append("</tr>");
        }
        return sb.toString();
    }

    private String generateLatencyRows() {
        StringBuilder sb = new StringBuilder();
        latencies.forEach((ex, ms) -> {
            String color = ms < 50 ? "#00ff9d" : (ms < 150 ? "#ffcc00" : "#ff0055");
            sb.append("<tr><td>").append(ex.toUpperCase()).append("</td><td style='color:").append(color).append("'>").append(ms).append(" ms</td></tr>");
        });
        return sb.toString();
    }

    private String generateChartDatasets() {
        StringBuilder sb = new StringBuilder();
        String[] colors = {"#00ff9d", "#ff0055", "#58a6ff", "#ffcc00"};
        int i = 0;
        for (Map.Entry<String, java.util.Deque<Long>> entry : latencyHistory.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append("{label:'").append(entry.getKey().toUpperCase()).append("',data:").append(entry.getValue().toString())
                    .append(",borderColor:'").append(colors[i % colors.length]).append("',borderWidth:1,tension:0.1,pointRadius:0}");
            i++;
        }
        return sb.toString();
    }
}