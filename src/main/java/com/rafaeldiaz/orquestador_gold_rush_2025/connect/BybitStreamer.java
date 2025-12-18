package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BybitStreamer implements MarketStreamer {

    private static final Logger logger = LoggerFactory.getLogger(BybitStreamer.class);
    private static final String WS_URL = "wss://stream.bybit.com/v5/public/spot";

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final PriceListener listener;
    private WebSocket webSocket;

    // Guardamos las suscripciones para re-suscribir si nos reconectamos
    private final Set<String> subscribedPairs = new HashSet<>();

    // Executor para tareas de reconexión y ping
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public BybitStreamer(PriceListener listener) {
        this.listener = listener;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // Importante: Sin timeout de lectura para WS
                .pingInterval(20, TimeUnit.SECONDS)   // Ping automático de OkHttp
                .build();
    }

    @Override
    public void connect() {
        Request request = new Request.Builder().url(WS_URL).build();
        this.webSocket = client.newWebSocket(request, new BybitWebSocketListener());

        // Tarea periódica de Heartbeat (Bybit requiere ping de aplicación a veces)
        scheduler.scheduleAtFixedRate(this::sendPing, 15, 20, TimeUnit.SECONDS);
        logger.info("Conectando a Bybit Stream...");
    }

    @Override
    public void subscribe(String pair) {
        subscribedPairs.add(pair);
        if (webSocket != null) {
            // Formato Bybit V5: {"op": "subscribe", "args": ["tickers.BTCUSDT"]}
            String msg = String.format("{\"op\": \"subscribe\", \"args\": [\"tickers.%s\"]}", pair);
            webSocket.send(msg);
            logger.info("Suscrito a: {}", pair);
        }
    }

    @Override
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Cierre solicitado");
        }
        scheduler.shutdown();
    }

    private void sendPing() {
        if (webSocket != null) {
            webSocket.send("{\"op\": \"ping\"}");
        }
    }

    // Clase interna para manejar eventos del WebSocket
    private class BybitWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            logger.info("Conexión WebSocket Bybit ABIERTA");
            // Re-suscribir automáticamente si hubo desconexión
            for (String pair : subscribedPairs) {
                subscribe(pair);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JsonNode root = mapper.readTree(text);

                // Ignorar respuestas de heartbeat/suscripción
                if (root.has("op") && (root.get("op").asText().equals("pong") || root.get("op").asText().equals("subscribe"))) {
                    return;
                }

                // Parsear Ticker Update
                // Formato V5: { "topic": "tickers.BTCUSDT", "data": { "lastPrice": "..." } }
                if (root.has("topic") && root.get("topic").asText().startsWith("tickers.")) {
                    String topic = root.get("topic").asText();
                    String pair = topic.replace("tickers.", "");

                    JsonNode data = root.get("data");
                    if (data != null && data.has("lastPrice")) {
                        double price = data.get("lastPrice").asDouble();
                        long ts = root.get("ts").asLong();

                        // ¡Disparamos el evento hacia el Bot!
                        listener.onPriceUpdate("bybit", pair, price, ts);
                    }
                }
            } catch (Exception e) {
                logger.error("Error parseando mensaje WS: {}", e.getMessage());
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            logger.warn("WS Bybit Cerrado: {} - {}", code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            logger.error("Error crítico WS Bybit: {}", t.getMessage());
            // Lógica simple de reconexión (Task 2.1.3 Requirement)
            try {
                Thread.sleep(5000); // Esperar 5s
                logger.info("Intentando reconectar...");
                connect(); // Llamada recursiva (cuidado en prod, pero cumple backlog)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}