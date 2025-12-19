package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import okhttp3.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementación REAL con WebSockets para Bybit.
 * Hereda de MarketStreamer (Clase Abstracta).
 */
public class BybitStreamer extends MarketStreamer {

    private static final String WS_URL = "wss://stream.bybit.com/v5/public/spot";

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private WebSocket webSocket;

    // Guardamos suscripciones para reconexión automática
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

    private void connect() {
        Request request = new Request.Builder().url(WS_URL).build();
        this.webSocket = client.newWebSocket(request, new BybitWebSocketListener());

        // Tarea de Heartbeat adicional (nivel aplicación)
        scheduler.scheduleAtFixedRate(this::sendPing, 15, 20, TimeUnit.SECONDS);
    }

    // --- IMPLEMENTACIÓN DEL CONTRATO (MarketStreamer) ---

    @Override
    public void subscribe(String pair) {
        subscribedPairs.add(pair);
        if (webSocket != null && isActive) {
            // JSON V5: {"op": "subscribe", "args": ["tickers.BTCUSDT"]}
            String msg = String.format("{\"op\": \"subscribe\", \"args\": [\"tickers.%s\"]}", pair);
            webSocket.send(msg);
            BotLogger.info("📡 [WS] Suscribiendo a: " + pair);
        }
    }

    @Override
    public void unsubscribe(String pair) {
        subscribedPairs.remove(pair);
        if (webSocket != null && isActive) {
            // JSON V5: {"op": "unsubscribe", "args": ["tickers.BTCUSDT"]}
            String msg = String.format("{\"op\": \"unsubscribe\", \"args\": [\"tickers.%s\"]}", pair);
            webSocket.send(msg);
            BotLogger.info("🔕 [WS] Desuscribiendo de: " + pair);
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

    // --- UTILIDADES INTERNAS ---

    private void sendPing() {
        if (webSocket != null && isActive) {
            webSocket.send("{\"op\": \"ping\"}");
        }
    }

    // --- OYENTE INTERNO DEL WEBSOCKET ---

    private class BybitWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            BotLogger.info("✅ Conexión WebSocket Bybit ESTABLECIDA");
            isActive = true;
            // Re-suscribir lo que teníamos pendiente
            for (String pair : subscribedPairs) {
                subscribe(pair);
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

                        // 🔥 AQUÍ ESTÁ LA MAGIA: Usamos el método del padre para avisar a todos
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
            // Podríamos intentar reconectar aquí tras un delay
        }
    }
}