package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 🧠 CFO QUANTUM (GERENTE FINANCIERO ASÍNCRONO)
 * Desacopla la lectura de datos (Red) de la toma de decisiones (CPU).
 * Mantiene un "Estado del Mundo" en RAM actualizado en segundo plano.
 */
public class PortfolioHealthManager {

    private final ExchangeConnector connector;
    private final List<String> spatialAccounts;
    private volatile double totalEquityUsdt = 0.0;

    // 🧠 MEMORIA DE CORTO PLAZO (RAM - Acceso Nanosegundos)
    private final Map<String, HealthDirective> directiveCache = new ConcurrentHashMap<>();

    // ⚙️ MOTOR DE FONDO
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService scatterExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public PortfolioHealthManager(ExchangeConnector connector) {
        this.connector = connector;
        this.spatialAccounts = BotConfig.SPATIAL_ACCOUNTS;
        BotLogger.info("🧠 CFO QUANTUM INICIADO: Gestionando cuentas " + spatialAccounts);

        // 🚀 AUTO-ARRANQUE: Inicia el monitoreo en segundo plano
        startHealthMonitor();
    }

    /**
     * 💓 LATIDO DEL CFO: Actualiza la salud de los activos proactivamente.
     * El Scanner nunca espera por la red, solo lee la última verdad conocida.
     */
    private void startHealthMonitor() {
        // Ejecutar cada 5 segundos (independiente del Scanner)
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Identificar qué activos nos importan (Intersección con lo que el Scanner busca)
                // Por defecto actualizamos los activos fijos o los descubiertos.
                // Para no complicar, actualizamos los "Hot Assets" definidos en Config o un set dinámico.
                List<String> assetsToMonitor = BotConfig.AUTO_DISCOVERY ?
                        new ArrayList<>(directiveCache.keySet()) : BotConfig.FIXED_ASSETS;

                if (assetsToMonitor.isEmpty()) return;

                // 2. Actualización Masiva Paralela
                updateDirectivesBatch(assetsToMonitor);

            } catch (Exception e) {
                BotLogger.error("💔 CFO Heartbeat Falló: " + e.getMessage());
            }
        }, 0, BotConfig.HEALTH_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * ⚡ LECTURA DE ALTA VELOCIDAD (Zero-Latency)
     * Este método es llamado por el Scanner en el bucle crítico (Hot Path).
     * Ya no hace I/O. Solo lee RAM.
     */
    public HealthDirective getAssetHealth(String asset) {
        // Si el dato no existe aún (arranque), devolvemos directiva neutra por defecto
        return directiveCache.computeIfAbsent(asset, k ->
                new HealthDirective(BotConfig.NORMAL_MIN_PROFIT, Set.of(), Set.of(), "UNKNOWN")
        );
    }

    /**
     * Motor de actualización en segundo plano (Scatter-Gather puro)
     */
    private void updateDirectivesBatch(List<String> assets) {
        // Para cada activo, lanzamos un cálculo de salud
        for (String asset : assets) {
            // Nota: Podríamos paralelizar esto aún más, pero el cuello de botella es la API
            // y ya tenemos paralelismo en el 'fetchBalances'.
            // Hacemos un cálculo rápido:
            computeSingleAssetHealth(asset);
        }
    }

    private void computeSingleAssetHealth(String asset) {
        Map<String, Double> assetBalances = new HashMap<>();
        Map<String, Double> usdtBalances = new HashMap<>();
        double totalAsset = 0;
        double totalUsdt = 0;

        // Recolección (I/O Síncrono pero en hilo de fondo, no duele)
        for (String ex : spatialAccounts) {
            double aBal = connector.fetchBalance(ex, asset);
            double uBal = connector.fetchBalance(ex, "USDT");

            assetBalances.put(ex, aBal);
            usdtBalances.put(ex, uBal);
            totalAsset += aBal;
            totalUsdt += uBal;
        }

        // Lógica de Negocio (CPU)
        double fairShareAsset = (totalAsset > 0) ? (totalAsset / spatialAccounts.size()) : 0;
        double fairShareUsdt = (totalUsdt > 0) ? (totalUsdt / spatialAccounts.size()) : 0;

        double criticalAssetThreshold = fairShareAsset * BotConfig.IMBALANCE_TOLERANCE;
        double criticalUsdtThreshold = fairShareUsdt * BotConfig.IMBALANCE_TOLERANCE;

        Set<String> needAsset = new HashSet<>();
        Set<String> needCash = new HashSet<>();

        for (String ex : spatialAccounts) {
            if (assetBalances.get(ex) < criticalAssetThreshold) needAsset.add(ex);
            if (usdtBalances.get(ex) < criticalUsdtThreshold) needCash.add(ex);
        }

        String state = (needAsset.isEmpty() && needCash.isEmpty()) ? "BALANCED" : "CRITICAL";
        double minProfit = state.equals("BALANCED") ? BotConfig.NORMAL_MIN_PROFIT : BotConfig.EMERGENCY_MIN_PROFIT;

        // Escritura Atómica en Caché
        directiveCache.put(asset, new HealthDirective(minProfit, needAsset, needCash, state));
    }

    // -------------------------------------------------------------------------
    // MÉTODOS DE APOYO (DISCOVERY Y AUDIT) - OPTIMIZADOS
    // -------------------------------------------------------------------------

    public List<String> discoverTradableAssets() {
        if (!BotConfig.AUTO_DISCOVERY) return BotConfig.FIXED_ASSETS;

        BotLogger.info("🕵️ CFO: Escaneando Universo (Paralelo)...");
        try {
            List<Callable<Set<String>>> tasks = new ArrayList<>();
            for (String ex : spatialAccounts) {
                tasks.add(() -> filterDust(ex, connector.fetchBalances(ex)));
            }

            List<Future<Set<String>>> futures = scatterExecutor.invokeAll(tasks, 5000, TimeUnit.MILLISECONDS);
            List<Set<String>> results = new ArrayList<>();
            for (Future<Set<String>> f : futures) {
                if (f.state() == Future.State.SUCCESS) results.add(f.get());
            }

            if (results.size() < 2) return BotConfig.FIXED_ASSETS;

            Set<String> commonAssets = new HashSet<>(results.get(0));
            for (int i = 1; i < results.size(); i++) commonAssets.retainAll(results.get(i));

            commonAssets.remove("USDT");
            List<String> finalAssets = new ArrayList<>(commonAssets);

            // 🔥 TRUCO: Pre-calentamos el cache con los nuevos activos encontrados
            // Para que la próxima vez que el scanner pregunte, ya tengan datos.
            BotLogger.info("✅ CFO: Activos comunes detectados: " + finalAssets);

            // Forzamos una actualización inmediata en segundo plano
            scatterExecutor.submit(() -> updateDirectivesBatch(finalAssets));

            return finalAssets;

        } catch (Exception e) {
            return BotConfig.FIXED_ASSETS;
        }
    }

    private Set<String> filterDust(String exchange, Map<String, Double> balances) {
        Set<String> real = new HashSet<>();
        Map<String, Double> allPrices = connector.fetchAllPrices(exchange);

        for (Map.Entry<String, Double> e : balances.entrySet()) {
            String asset = e.getKey();
            Double qty = e.getValue();
            if (asset.equals("USDT")) {
                if (qty > BotConfig.MIN_ASSET_VALUE_USDT) real.add(asset);
                continue;
            }
            Double price = allPrices.getOrDefault(asset + "USDT", allPrices.get(asset + "-USDT"));
            if (price != null && (qty * price) > BotConfig.MIN_ASSET_VALUE_USDT) {
                real.add(asset);
            }
        }
        return real;
    }

    public void performAudit() {
        DoubleAdder grandTotal = new DoubleAdder();
        List<Callable<Void>> auditTasks = new ArrayList<>();

        for (String exchange : spatialAccounts) {
            auditTasks.add(() -> {
                try {
                    Map<String, Double> balances = connector.fetchBalances(exchange);
                    Map<String, Double> prices = connector.fetchAllPrices(exchange);
                    double exchangeTotal = 0.0;
                    for (Map.Entry<String, Double> entry : balances.entrySet()) {
                        String asset = entry.getKey();
                        if (asset.equals("USDT")) exchangeTotal += entry.getValue();
                        else {
                            Double p = prices.getOrDefault(asset + "USDT", prices.get(asset + "-USDT"));
                            if (p != null) exchangeTotal += entry.getValue() * p;
                        }
                    }
                    grandTotal.add(exchangeTotal);
                } catch (Exception e) {}
                return null;
            });
        }
        try {
            scatterExecutor.invokeAll(auditTasks, 3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        this.totalEquityUsdt = grandTotal.sum();
    }

    public double getTotalEquityUsdt() { return totalEquityUsdt; }

    public void shutdown() {
        heartbeatScheduler.shutdownNow();
        scatterExecutor.shutdownNow();
    }

    public record HealthDirective(
            double minProfitPercent,
            Set<String> preferredBuyers,
            Set<String> preferredSellers,
            String statusLabel
    ) {}
}