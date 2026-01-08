package com.rafaeldiaz.orquestador_gold_rush_2025.core.telemetry;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public class DashboardService {

    // --- DATOS DEL RADAR (NUEVO) ---
    private final Map<String, RadarEntry> radarData = new ConcurrentHashMap<>();

    // Record interno para datos del radar
    private record RadarEntry(String symbol, double score, double estSpread, String status, long timestamp) {}

    // --- ESTADÍSTICAS DEL EMBUDO ---
    private final AtomicLong funnelDetected = new AtomicLong(0);
    private final AtomicLong funnelLowSpread = new AtomicLong(0);
    private final AtomicLong funnelOracleVeto = new AtomicLong(0);
    private final AtomicLong funnelNoLiquidity = new AtomicLong(0);
    private final AtomicLong funnelNetworkLag = new AtomicLong(0);
    private final AtomicLong funnelSystemError = new AtomicLong(0);
    private final AtomicLong funnelWinners = new AtomicLong(0);

    // --- ESTRUCTURAS DE DATOS ---
    private final Deque<String> recentLogs = new ConcurrentLinkedDeque<>();
    private final Map<String, Long> latencies = new HashMap<>();
    private final Map<String, Deque<Long>> latencyHistory = new ConcurrentHashMap<>();
    private final int MAX_POINTS = 30;

    private double currentPnL = 0.0;
    private long totalCycles = 0;
    private String inventoryHtml = "";

    // Auditoría
    private final Deque<ArbitrageTrace> auditTrail = new ConcurrentLinkedDeque<>();
    private final int MAX_AUDIT_ENTRIES = 50;

    // =================================================================================
    // 🔥 NUEVO MÉTODO PARA EL WATCHDOG
    // =================================================================================
    public void updateRadar(String symbol, double score, double estSpread, String status) {
        // Guardamos o actualizamos el par. El mapa mantiene la última info.
        radarData.put(symbol, new RadarEntry(symbol, score, estSpread, status, System.currentTimeMillis()));

        // Limpieza: Si hay pares viejos (> 5 min sin actualización), los borramos para no ensuciar
        long now = System.currentTimeMillis();
        radarData.entrySet().removeIf(e -> (now - e.getValue().timestamp) > 300000);
    }

    // --- MÉTODOS DE ACTUALIZACIÓN EXISTENTES ---
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

    public void addLog(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        recentLogs.offerFirst("[" + time + "] " + message);
        if (recentLogs.size() > 15) recentLogs.pollLast();
    }

    public void registrarTraza(ArbitrageTrace traza) {
        auditTrail.offerFirst(traza);
        if (auditTrail.size() > MAX_AUDIT_ENTRIES) auditTrail.pollLast();
        funnelDetected.incrementAndGet();
        switch (traza.stage) {
            case SCAN_IGNORED: case ADVISOR_REJECTED: funnelLowSpread.incrementAndGet(); break;
            case ORACLE_VETO: funnelOracleVeto.incrementAndGet(); break;
            case SLIPPAGE_EXCEEDED: funnelNoLiquidity.incrementAndGet(); break;
            case LATENCY_TIMEOUT: funnelNetworkLag.incrementAndGet(); break;
            case ORDER_FAILED: case RISK_PAUSED: case ORPHAN_DETECTED: case FORCED_CLOSE: funnelSystemError.incrementAndGet(); break;
            case ENTRY_FILLED: case EXIT_FILLED:
                if (traza.stage == ArbitrageTrace.AuditStage.EXIT_FILLED) funnelWinners.incrementAndGet();
                break;
            default: break;
        }
    }

    public void addDecisionLog(String symbol, String type, String details, double value, String exchange, String status) {
        ArbitrageTrace.AuditStage stage = ArbitrageTrace.AuditStage.SYSTEM_MSG;
        if (type.contains("SPREAD_LOW")) stage = ArbitrageTrace.AuditStage.SCAN_IGNORED;
        else if (type.contains("ORACLE_BLOCK")) stage = ArbitrageTrace.AuditStage.ORACLE_VETO;
        else if (type.contains("NO_LIQUIDITY")) stage = ArbitrageTrace.AuditStage.SLIPPAGE_EXCEEDED;
        else if (type.contains("NETWORK_LAG")) stage = ArbitrageTrace.AuditStage.LATENCY_TIMEOUT;
        else if (type.contains("WIN")) stage = ArbitrageTrace.AuditStage.EXIT_FILLED;
        else if (type.contains("ERROR")) stage = ArbitrageTrace.AuditStage.ORDER_FAILED;
        registrarTraza(new ArbitrageTrace(symbol, stage, details, value));
    }

    public void registrarEventoAuditoria(String type, String message, String value) {
        double val = 0;
        try { val = Double.parseDouble(value.replace("ms","").replace("$","")); } catch(Exception e){}
        auditTrail.offerFirst(new ArbitrageTrace("SYSTEM", ArbitrageTrace.AuditStage.SYSTEM_MSG, message, val));
    }


    public void updateInventory(Map<String, Map<String, Double>> balances, Map<String, Double> refPrices) {
        StringBuilder sb = new StringBuilder();
        // Agregamos colgroup para controlar el ancho de las columnas
        sb.append("<table style='table-layout: fixed; width: 100%;'>");
        sb.append("<colgroup><col style='width: 40%;'><col style='width: 20%;'><col style='width: 40%;'></colgroup>");
        sb.append("<tr style='color:var(--accent); font-size:10px;'><th>EXCHANGE</th><th>ASSET</th><th style='text-align:right'>QTY</th></tr>");

        balances.forEach((ex, assets) -> {
            assets.forEach((asset, qty) -> {
                // Calcular valor en USD para el filtro de 5 USDT
                double price = refPrices.getOrDefault(asset + "USDT", 1.0);
                double valueUsdt = qty * price;

                // FILTRO: Ignorar < 5 USDT excepto BNB/MX (para fees)
                if (valueUsdt >= 5.0 || asset.equals("BNB") || asset.equals("MX")) {
                    sb.append("<tr>");
                    // Usamos el nombre completo del exchange, no el substring
                    sb.append("<td style='color:#666; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;'>")
                            .append(ex.toUpperCase()).append("</td>");
                    sb.append("<td style='font-weight:bold'>").append(asset).append("</td>");

                    // Alerta de recarga si es muy bajo (0.1 unidades)
                    if (qty < 0.1 && !asset.equals("BNB") && !asset.equals("MX") && !asset.equals("USDT")) {
                        sb.append("<td style='text-align:right;' class='alert-fill'>⚠️ RECARGAR</td>");
                    } else {
                        sb.append("<td style='text-align:right; color:#fff; font-family: monospace;'>")
                                .append(String.format("%.4f", qty)).append("</td>");
                    }
                    sb.append("</tr>");
                }
            });
        });
        sb.append("</table>");
        this.inventoryHtml = sb.toString();
    }
    // =================================================================================
    // GENERACIÓN HTML
    // =================================================================================
    public void generate() {
        try {
            String funnelData = generateFunnelChartData();
            String configGridHtml = generateDynamicConfigGrid();
            String radarHtml = generateRadarHtml(); // <--- NUEVO CONTENIDO

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
            .header { display: flex; justify-content: space-between; border-bottom: 2px solid var(--accent); padding-bottom: 10px; margin-bottom: 15px; align-items: center; }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 15px; }
            .card { background: var(--card); border: 1px solid #222; padding: 12px; border-radius: 4px; }
            .kpi { font-size: 28px; font-weight: bold; color: #fff; }
            .kpi-label { font-size: 10px; text-transform: uppercase; color: var(--muted); margin-bottom: 5px; letter-spacing: 1px; }
            table { width: 100%%; border-collapse: collapse; font-size: 11px; }
            td, th { padding: 3px 5px; border-bottom: 1px solid #222; text-align: left; }
            
            /* Nuevos Estilos Radar */
            .radar-score-high { color: var(--danger); font-weight: bold; animation: pulse 2s infinite; }
            .radar-score-med { color: var(--warning); }
            .radar-spread { color: var(--accent); font-family: monospace; }
            @keyframes pulse { 0%% { opacity: 1; } 50%% { opacity: 0.7; } 100%% { opacity: 1; } }

            .cfg-section-title { color: var(--warning); font-size: 11px; border-bottom: 1px solid #333; margin-bottom: 8px; padding-bottom: 2px; text-transform: uppercase; }
            .cfg-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 8px; margin-top: 5px; }
            .cfg-card { background: #0a0a0a; border: 1px solid #333; padding: 8px; border-radius: 3px; }
            .cfg-row { display: flex; justify-content: space-between; margin-bottom: 2px; font-size: 10px; }
            .cfg-key { color: #777; }
            .cfg-val { color: #ddd; font-weight: bold; }
            .val-true { color: var(--accent); }
            .val-false { color: var(--danger); }
            .val-num { color: #58a6ff; }
            .alert-fill { color: var(--danger); font-weight: bold; animation: blink 1s infinite; }
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
            
            <div class="card" style="grid-row: span 2;">
                <div class="kpi-label">🔥 MERCADOS CALIENTES (RADAR)</div>
                <div style="max-height: 380px; overflow-y: auto;">
                    <table style="width:100%%">
                        <tr style="color:#666"><th>PAIR</th><th>SCORE</th><th>SPREAD</th><th>STATUS</th></tr>
                        %s
                    </table>
                </div>
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
                <div class="kpi-label">🌪️ EMBUDO DE EJECUCIÓN (LIFETIME)</div>
                <canvas id="funnelChart"></canvas>
            </div>

            <div class="card" style="grid-column: span 3; max-height: 300px; overflow-y: auto;">
                <div class="kpi-label">🛡️ AUDITORÍA DE OPERACIONES (TRACE - LAST 50)</div>
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
            <div class="kpi-label" style="color: var(--accent);">🛠️ PARÁMETROS DE VUELO (DYNAMIC CONFIG)</div>
            %s 
        </div>

        <script>
            // Gráficos (Latencia y Embudo)
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
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    inventoryHtml,
                    radarHtml, // <--- AQUI INSERTAMOS EL HTML DEL RADAR
                    currentPnL >= 0 ? "#00ff9d" : "#ff0055",
                    currentPnL,
                    totalCycles,
                    generateLatencyRows(),
                    generateAuditRows(),
                    String.join("<br>", recentLogs),
                    configGridHtml,
                    generateChartDatasets(),
                    funnelData
            );

            Files.writeString(Path.of("dashboard.html"), html);
        } catch (Exception e) {
            BotLogger.error("Error Dashboard: " + e.getMessage());
        }
    }

    // 🔥 GENERADOR DE HTML PARA RADAR
    private String generateRadarHtml() {
        if (radarData.isEmpty()) {
            return "<tr><td colspan='4' style='text-align:center; padding:20px; color:#444'>🔭 ESCANEANDO MERCADO...</td></tr>";
        }

        StringBuilder sb = new StringBuilder();
        // Ordenar por Score descendente
        radarData.values().stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .forEach(r -> {
                    String scoreClass = r.score > 0.8 ? "radar-score-high" : "radar-score-med";
                    sb.append("<tr>");
                    sb.append("<td style='font-weight:bold; color:#fff'>").append(r.symbol).append("</td>");
                    sb.append("<td class='").append(scoreClass).append("'>").append(String.format("%.2f", r.score)).append("</td>");
                    sb.append("<td class='radar-spread'>").append(String.format("%.2f%%", r.estSpread * 100)).append("</td>");
                    sb.append("<td style='color:#777; font-size:10px'>").append(r.status).append("</td>");
                    sb.append("</tr>");
                });
        return sb.toString();
    }

    // (Los métodos generateDynamicConfigGrid, generateFunnelChartData, etc. se mantienen igual)
    private String generateDynamicConfigGrid() {
        return """
        <div class="cfg-grid">
            <div class="cfg-card">
                <div class="cfg-section-title">🛑 CONTROL OPS</div>
                <div class="cfg-row"><span class="cfg-key">BOT_DRY_RUN</span><span class="cfg-val %s">%s</span></div>
                <div class="cfg-row"><span class="cfg-key">CAPITAL_SEMILLA</span><span class="cfg-val val-num">%.1f</span></div>
            </div>
            <div class="cfg-card">
                <div class="cfg-section-title">💰 UMBRALES</div>
                <div class="cfg-row"><span class="cfg-key">MIN_PROFIT</span><span class="cfg-val val-num">%.2f</span></div>
                <div class="cfg-row"><span class="cfg-key">NORMAL_PROFIT</span><span class="cfg-val val-num">%.2f</span></div>
            </div>
            <div class="cfg-card">
                <div class="cfg-section-title">⚡ TOLERANCIAS RED</div>
                <div class="cfg-row"><span class="cfg-key">MAX_LATENCY_MS</span><span class="cfg-val val-num">%.0f</span></div>
                <div class="cfg-row"><span class="cfg-key">MAX_SLIPPAGE</span><span class="cfg-val val-num">%.3f</span></div>
            </div>
            <div class="cfg-card">
                <div class="cfg-section-title">⏱️ VELOCIDAD</div>
                <div class="cfg-row"><span class="cfg-key">SCAN_INTERVAL</span><span class="cfg-val val-true">%dms</span></div>
            </div>
            <div class="cfg-card">
                <div class="cfg-section-title">🧠 CEREBRO</div>
                <div class="cfg-row"><span class="cfg-key">MIN_SPREAD</span><span class="cfg-val val-num">%.4f</span></div>
                <div class="cfg-row"><span class="cfg-key">ADVISOR</span><span class="cfg-val">%s</span></div>
            </div>
            <div class="cfg-card" style="border-color: var(--danger);">
                <div class="cfg-section-title" style="color:var(--danger)">🛡️ RIESGO</div>
                <div class="cfg-row"><span class="cfg-key">MAX_DAILY_LOSS</span><span class="cfg-val val-false">%.2f%%</span></div>
                <div class="cfg-row"><span class="cfg-key">MAX_DRAWDOWN</span><span class="cfg-val val-false">%.2f%%</span></div>
                <div class="cfg-row"><span class="cfg-key">MAX_STREAK</span><span class="cfg-val val-false">%d</span></div>
            </div>
            <div class="cfg-card">
                <div class="cfg-section-title">🔮 ORÁCULO</div>
                <div class="cfg-row"><span class="cfg-key">Z_SCORE_THR</span><span class="cfg-val val-num">%.1f</span></div>
                <div class="cfg-row"><span class="cfg-key">MIN_CONFIDENCE</span><span class="cfg-val val-num">%.2f</span></div>
                <div class="cfg-row"><span class="cfg-key">AGGR_SPREAD</span><span class="cfg-val val-num">%.4f</span></div>
            </div>
        </div>
        """.formatted(
                BotConfig.DRY_RUN ? "val-false" : "val-true", BotConfig.DRY_RUN, BotConfig.SEED_CAPITAL,
                BotConfig.MIN_PROFIT_USDT, BotConfig.NORMAL_MIN_PROFIT,
                (double) BotConfig.getMaxLatencyMs(), BotConfig.MAX_SLIPPAGE,
                BotConfig.SCAN_INTERVAL_MS,
                BotConfig.getMinScanSpread(), BotConfig.getAdvisorRefExchange().toUpperCase(),
                BotConfig.getRiskMaxDailyLoss() * 100, BotConfig.getRiskMaxDrawdown() * 100, BotConfig.getRiskMaxConsecutiveLosses(),
                BotConfig.getOracleZScoreThreshold(), BotConfig.getOracleMinConfidence(), BotConfig.getOracleAggressiveSpread()
        );
    }

    private String generateFunnelChartData() {
        return String.format("%d, %d, %d, %d, %d, %d, %d",
                funnelDetected.get(), funnelLowSpread.get(), funnelOracleVeto.get(),
                funnelNoLiquidity.get(), funnelNetworkLag.get(), funnelSystemError.get(), funnelWinners.get());
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
            sb.append("<td style='color:#555;'>").append(trace.timestamp.substring(6)).append("</td>");
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
    // En DashboardService.java
    public void registrarTrazaDecision(String asset, String stage, String status, String detail, double data) {
        // Creamos una traza compatible con su tabla de Auditoría
        ArbitrageTrace traza = new ArbitrageTrace(
                asset,
                mapStage(stage), // Mapeamos el String del Auditor al Enum del Dashboard
                detail,
                data
        );

        // Inyectamos en la lista que lee el método generateAuditRows()
        this.registrarTraza(traza);
    }

    private ArbitrageTrace.AuditStage mapStage(String stage) {
        return switch (stage.toUpperCase()) {
            case "ESTRATEGIA" -> ArbitrageTrace.AuditStage.SCAN_IGNORED;
            case "FINANCIERO" -> ArbitrageTrace.AuditStage.ORACLE_VETO;
            case "EJECUCION" -> ArbitrageTrace.AuditStage.ENTRY_FILLED;
            case "BATALLA" -> ArbitrageTrace.AuditStage.EXIT_FILLED;
            default -> ArbitrageTrace.AuditStage.SYSTEM_MSG;
        };
    }
}