package com.rafaeldiaz.orquestador_gold_rush_2025.core.main;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class TestDashboard {

    public static void main(String[] args) {
        // 1. Simulamos datos de la red (Como si vinieran de Tokyo)
        Map<String, Long> latenciasSimuladas = new HashMap<>();
        latenciasSimuladas.put("binance", 12L);    // ⚡ Hyper (Verde)
        latenciasSimuladas.put("kucoin", 45L);     // 🔵 Sync (Cian)
        latenciasSimuladas.put("bybit", 140L);     // 🟡 Stable (Amarillo)
        latenciasSimuladas.put("mexc", 320L);      // 🔴 Lag (Rojo)
        latenciasSimuladas.put("okx", 9999L);      // 💀 Error (Rojo Muerto)

        // 2. Simulamos métricas de negocio
        long uptimeSec = 3600 + 1540; // 1 hora y pico
        long cycles = 14502;
        double pnl = 12.50;

        // 3. EJECUTAMOS EL RENDERIZADOR
        printCyberpunkDashboard(latenciasSimuladas, uptimeSec, cycles, pnl);
    }

    private static void printCyberpunkDashboard(Map<String, Long> networkLatencies, long uptimeSec, long cycles, double pnl) {
        // PALETA DE COLORES NEÓN ANSI
        final String RST = "\u001B[0m";   // Reset
        final String CYA = "\u001B[36m";  // Cyan (Tokyo Blue)
        final String MAG = "\u001B[35m";  // Magenta (Neon Purple)
        final String GRN = "\u001B[32m";  // Green (Matrix)
        final String RED = "\u001B[31m";  // Red (Danger)
        final String YEL = "\u001B[33m";  // Yellow (Warning)
        final String WHT = "\u001B[37m";  // White (Bright)

        StringBuilder sb = new StringBuilder();

        // --- HEADER ---
        sb.append("\n\n");
        sb.append(CYA).append("╔═").append(MAG).append(" SYSTEM: GOLD_RUSH_2025 ").append(CYA).append("════════════════════════════════════════════════╗").append(RST).append("\n");
        sb.append(CYA).append("║").append(WHT).append(String.format(" ⏱️ UPTIME: %02d:%02d:%02d ", uptimeSec/3600, (uptimeSec%3600)/60, uptimeSec%60))
                .append(CYA).append("│").append(WHT).append(String.format(" ⚡ CYCLES: %-8d ", cycles))
                .append(CYA).append("│").append(pnl >= 0 ? GRN : RED).append(String.format(" 💰 PnL: $%-8.2f ", pnl))
                .append(CYA).append("║").append(RST).append("\n");
        sb.append(CYA).append("╠══════════════════════════════════════════════════════════════════════╣").append(RST).append("\n");
        sb.append(CYA).append("║ 🗼 NETWORK UPLINK STATUS (TOKYO NODE)                                ║").append(RST).append("\n");

        // --- NETWORK BARS ---
        sb.append(CYA).append("║ ").append(MAG).append("EXCHANGE       ").append(CYA).append("│ ").append(MAG).append("LATENCY ").append(CYA).append("│ ").append(MAG).append("SIGNAL INTEGRITY BAR        ").append(CYA).append("│ ").append(MAG).append("STATUS   ").append(CYA).append("║").append(RST).append("\n");
        sb.append(CYA).append("╟────────────────┼─────────┼─────────────────────────────┼──────────╢").append(RST).append("\n");

        // Ordenamos keys para que se vea ordenado en la prueba
        String[] exchanges = {"binance", "kucoin", "bybit", "mexc", "okx"};

        for (String ex : exchanges) {
            long lat = networkLatencies.getOrDefault(ex, 0L);

            // Lógica de Semáforo Cyberpunk
            String color;
            String statusTxt;
            int blocks;

            if (lat == 0) { color = WHT; statusTxt = "INIT..."; blocks = 0; }
            else if (lat == 9999) { color = RED; statusTxt = "SEVERED"; blocks = 0; }
            else if (lat <= 20) { color = GRN; statusTxt = "HYPER"; blocks = 20; }     // < 20ms
            else if (lat <= 50) { color = CYA; statusTxt = "SYNC"; blocks = 16; }      // < 50ms
            else if (lat <= 150) { color = YEL; statusTxt = "STABLE"; blocks = 10; }   // < 150ms
            else { color = RED; statusTxt = "LAG"; blocks = 4; }                       // > 150ms

            // Construcción de la Barra [████········]
            StringBuilder bar = new StringBuilder();
            for(int i=0; i<20; i++) {
                if (i < blocks) bar.append("█");
                else bar.append(CYA + "·");
            }

            sb.append(CYA).append("║ ").append(WHT).append(String.format("%-14s", ex.toUpperCase()))
                    .append(CYA).append(" │ ").append(color).append(String.format("%3d ms", lat))
                    .append(CYA).append("  │ ").append(color).append(bar.toString())
                    .append(CYA).append(" │ ").append(color).append(String.format("%-8s", statusTxt))
                    .append(CYA).append(" ║").append(RST).append("\n");
        }
        sb.append(CYA).append("╚════════════════╧═════════╧═════════════════════════════╧══════════╝").append(RST);

        System.out.println(sb.toString());
    }
}