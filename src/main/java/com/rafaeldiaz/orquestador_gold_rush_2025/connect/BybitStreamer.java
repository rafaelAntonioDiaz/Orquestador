package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner.MarketListener; // <--- IMPORTACIÓN CRÍTICA
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import okhttp3.*;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementación REAL con WebSockets para Bybit.
 * Hereda de MarketStreamer y ahora escucha al Cerebro Táctico (MarketListener).
 */
public class BybitStreamer extends MarketStreamer implements MarketListener { // <--- CONTRATO FIRMADO

    private static final String WS_URL = "wss://stream.bybit.com/v5/public/spot";

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private WebSocket webSocket;

    // Guardamos suscripciones para reconexión automática y gestión de targets
    private final Set<String> subscribedPairs = Collections.synchronizedSet(new HashSet<>());

    // Executor para Heartbeat y Reconexión
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isActive = false;

    public BybitStreamer() {
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // Vital para WS
                .pingInterval(20, TimeUnit.SECONDS)   // Ping nativo
                .build();

        BotLogger.info("🔌 Iniciando Motor WebSocket Bybit...");
        connect();
    }
    public void start() {
        if (!isActive) {
            BotLogger.info("🔌 Reiniciando conexión manual...");
            connect();
        }
    }

    private void connect() {
        Request request = new Request.Builder().url(WS_URL).build();
        this.webSocket = client.newWebSocket(request, new BybitWebSocketListener());

        // Tarea de Heartbeat adicional (nivel aplicación)
        scheduler.scheduleAtFixedRate(this::sendPing, 15, 20, TimeUnit.SECONDS);
    }

    // =========================================================================
    // 🧠 NUEVO: IMPLEMENTACIÓN DE MARKET LISTENER (SMART SWAPPING)
    // =========================================================================
    @Override
    public synchronized void updateTargets(List<String> newTargets) {
        if (newTargets == null) newTargets = new ArrayList<>();
        // BotLogger.info("🔄 STREAMER: Optimizando sensores hacia -> " + newTargets);
        // 🛡️ BLINDAJE: Siempre agregar el PIVOTE (BTCUSDT)
        // Sin esto, la triangulación falla porque nos falta el tercer lado del triángulo.
        List<String> tacticalTargets = new ArrayList<>(newTargets);
        if (!tacticalTargets.contains("BTCUSDT")) {
            tacticalTargets.add("BTCUSDT");
        }
        BotLogger.info("🔄 STREAMER: Recalibrando sensores hacia -> " + tacticalTargets);
        // 1. Convertir targets a Set para operaciones matemáticas
        Set<String> desiredSet = new HashSet<>(newTargets);

        // 2. Snapshot seguro de lo que tenemos actualmente
        Set<String> currentSet;
        synchronized (subscribedPairs) {
            currentSet = new HashSet<>(subscribedPairs);
        }

        // 3. CALCULAR DELTA (Diferencial)

        // A. Qué sobra (Están en Current pero NO en Desired)
        Set<String> toUnsubscribe = new HashSet<>(currentSet);
        toUnsubscribe.removeAll(desiredSet);

        // B. Qué falta (Están en Desired pero NO en Current)
        Set<String> toSubscribe = new HashSet<>(desiredSet);
        toSubscribe.removeAll(currentSet);

        // 4. EJECUCIÓN (Solo tocamos lo necesario, no reiniciamos nada)
        if (!toUnsubscribe.isEmpty()) {
            for (String pair : toUnsubscribe) {
                unsubscribe(pair);
                BotLogger.info("🔇 [AUTO] Dejando de escuchar: " + pair);
            }
        }

        if (!toSubscribe.isEmpty()) {
            for (String pair : toSubscribe) {
                subscribe(pair);
                BotLogger.info("🔈 [AUTO] Escuchando nuevo objetivo: " + pair);
            }
        }

        // Si no hubo cambios, no hacemos nada (Eficiencia Zen)
    }

    @Override
    public void reportRadarDetection(String symbol, double score, double spreadPct, double volatility) {

    }

    // =========================================================================
    // ⚡ MÉTODOS DEL CONTRATO ORIGINAL (INTACTOS)
    // =========================================================================

    @Override
    public void subscribe(String pair) {
        if (subscribedPairs.add(pair)) { // Solo enviar si es nuevo en el set
            if (webSocket != null && isActive) {
                // JSON V5: {"op": "subscribe", "args": ["tickers.BTCUSDT"]}
                String msg = String.format("{\"op\": \"subscribe\", \"args\": [\"tickers.%s\"]}", pair);
                webSocket.send(msg);
                BotLogger.info("📡 [WS] Suscribiendo a: " + pair);
            }
        }
    }

    @Override
    public void unsubscribe(String pair) {
        if (subscribedPairs.remove(pair)) { // Solo enviar si existía
            if (webSocket != null && isActive) {
                // JSON V5: {"op": "unsubscribe", "args": ["tickers.BTCUSDT"]}
                String msg = String.format("{\"op\": \"unsubscribe\", \"args\": [\"tickers.%s\"]}", pair);
                webSocket.send(msg);
                BotLogger.info("🔕 [WS] Desuscribiendo de: " + pair);
            }
        }
    }

    @Override
    public void stop() {
        isActive = false;
        if (webSocket != null) {
            webSocket.close(1000, "Cierre ordenado por ChasquiTokio");
        }
        scheduler.shutdownNow();
        BotLogger.info("🔌 BybitStreamer APAGADO.");
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    private void sendPing() {
        if (webSocket != null && isActive) {
            webSocket.send("{\"op\": \"ping\"}");
        }
    }

    // =========================================================================
    // 👂 OYENTE INTERNO (Lógica de Recepción)
    // =========================================================================

    private class BybitWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            BotLogger.info("✅ Conexión WebSocket Bybit ESTABLECIDA");
            isActive = true;
            // Re-suscribir lo que teníamos pendiente (Resilience)
            synchronized (subscribedPairs) {
                for (String pair : subscribedPairs) {
                    // Reenviar comando manual para recuperar estado tras desconexión
                    String msg = String.format("{\"op\": \"subscribe\", \"args\": [\"tickers.%s\"]}", pair);
                    webSocket.send(msg);
                }
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JsonNode root = mapper.readTree(text);

                // Ignorar respuestas de control (pong, subscribe confirm)
                if (root.has("op")) {
                    String op = root.get("op").asText();
                    if (op.equals("pong") || op.equals("subscribe") || op.equals("unsubscribe")) return;
                }

                // Parsear Ticker Update
                // { "topic": "tickers.BTCUSDT", "data": { "lastPrice": "..." } }
                if (root.has("topic") && root.get("topic").asText().startsWith("tickers.")) {
                    String topic = root.get("topic").asText();
                    String pair = topic.replace("tickers.", "");

                    JsonNode data = root.get("data");
                    if (data != null && data.has("lastPrice")) {
                        double price = data.get("lastPrice").asDouble();
                        long ts = root.has("ts") ? root.get("ts").asLong() : System.currentTimeMillis();

                        // 🔥 MAGIA PURA: Usamos el método del padre para notificar a todo el sistema
                        notifyListeners("bybit", pair, price, ts);
                    }
                }
            } catch (Exception e) {
                BotLogger.error("Error procesando frame WS: " + e.getMessage());
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            BotLogger.warn("WS Bybit Cerrado: " + reason);
            isActive = false;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            BotLogger.error("🔥 Error WS Bybit: " + t.getMessage());
            isActive = false;
            // Nota: El Scheduler podría intentar reconectar en versiones futuras
        }
    }
}