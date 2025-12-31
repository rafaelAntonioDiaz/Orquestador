package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 🧠 CFO QUANTUM v2.1 (QUORUM EDITION)
 * - Lógica de descubrimiento basada en Democracia (Min 2 exchanges), no Unanimidad.
 * - Resiliente a caídas parciales de exchanges (ej. MEXC Timeout).
 */
public class PortfolioHealthManager {

    private final ExchangeConnector connector;
    private final List<String> spatialAccounts;
    private volatile double totalEquityUsdt = 0.0;

    // 🧠 MEMORIA DE CORTO PLAZO
    private final Map<String, HealthDirective> directiveCache = new ConcurrentHashMap<>();

    // ⚙️ MOTORES
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService scatterExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public PortfolioHealthManager(ExchangeConnector connector) {
        this.connector = connector;
        this.spatialAccounts = BotConfig.SPATIAL_ACCOUNTS;
        BotLogger.info("🧠 CFO QUANTUM INICIADO: Gestionando cuentas " + spatialAccounts);
        startHealthMonitor();
    }

    private void startHealthMonitor() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Identificar activos calientes (Config o Descubiertos)
                List<String> assetsToMonitor = BotConfig.AUTO_DISCOVERY ?
                        new ArrayList<>(directiveCache.keySet()) : BotConfig.FIXED_ASSETS;

                // Si la lista está vacía al inicio, intentamos descubrir una vez
                if (assetsToMonitor.isEmpty() && BotConfig.AUTO_DISCOVERY) {
                    discoverTradableAssets();
                    return;
                }

                if (assetsToMonitor.isEmpty()) return;

                // 2. Actualizar Directivas
                updateDirectivesBatch(assetsToMonitor);

            } catch (Exception e) {
                BotLogger.error("💔 CFO Heartbeat Falló: " + e.getMessage());
            }
        }, 0, BotConfig.HEALTH_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    public HealthDirective getAssetHealth(String asset) {
        return directiveCache.computeIfAbsent(asset, k ->
                new HealthDirective(BotConfig.NORMAL_MIN_PROFIT, Set.of(), Set.of(), "UNKNOWN")
        );
    }

    private void updateDirectivesBatch(List<String> assets) {
        for (String asset : assets) {
            computeSingleAssetHealth(asset);
        }
    }

    private void computeSingleAssetHealth(String asset) {
        Map<String, Double> assetBalances = new HashMap<>();
        Map<String, Double> usdtBalances = new HashMap<>();
        double totalAsset = 0;
        double totalUsdt = 0;
        int activeExchanges = 0;

        // Recolección
        for (String ex : spatialAccounts) {
            try {
                double aBal = connector.fetchBalance(ex, asset);
                double uBal = connector.fetchBalance(ex, "USDT");

                // Si la API falla devolviendo -1 o similar, lo tratamos como 0 pero no contamos el exchange como activo si todo es 0
                // Asumimos que el conector devuelve 0.0 en caso de error.

                assetBalances.put(ex, aBal);
                usdtBalances.put(ex, uBal);
                totalAsset += aBal;
                totalUsdt += uBal;
                activeExchanges++;
            } catch (Exception ignored) {
                // Si falla un exchange puntual, no rompemos el cálculo global
            }
        }

        if (activeExchanges < 2) return; // No hay suficientes participantes para balancear

        // Lógica de Negocio (Rebalanceo)
        double fairShareAsset = (totalAsset > 0) ? (totalAsset / activeExchanges) : 0;
        double fairShareUsdt = (totalUsdt > 0) ? (totalUsdt / activeExchanges) : 0;

        double criticalAssetThreshold = fairShareAsset * BotConfig.IMBALANCE_TOLERANCE;
        double criticalUsdtThreshold = fairShareUsdt * BotConfig.IMBALANCE_TOLERANCE;

        Set<String> needAsset = new HashSet<>();
        Set<String> needCash = new HashSet<>();

        for (String ex : spatialAccounts) {
            // Solo evaluamos si tenemos datos del exchange
            if (assetBalances.containsKey(ex)) {
                if (assetBalances.get(ex) < criticalAssetThreshold) needAsset.add(ex);
                if (usdtBalances.get(ex) < criticalUsdtThreshold) needCash.add(ex);
            }
        }

        String state = (needAsset.isEmpty() && needCash.isEmpty()) ? "BALANCED" : "CRITICAL";
        double minProfit = state.equals("BALANCED") ? BotConfig.NORMAL_MIN_PROFIT : BotConfig.EMERGENCY_MIN_PROFIT;

        directiveCache.put(asset, new HealthDirective(minProfit, needAsset, needCash, state));
    }

    // -------------------------------------------------------------------------
    // 🔥 MÉTODOS OPTIMIZADOS (FIX: QUORUM VS UNANIMITY)
    // -------------------------------------------------------------------------

    public List<String> discoverTradableAssets() {
        if (!BotConfig.AUTO_DISCOVERY) return BotConfig.FIXED_ASSETS;

        // BotLogger.info("🕵️ CFO: Escaneando Universo (Paralelo)..."); // Verbose reducido
        try {
            List<Callable<Set<String>>> tasks = new ArrayList<>();
            for (String ex : spatialAccounts) {
                tasks.add(() -> filterDust(ex, connector.fetchBalances(ex)));
            }

            List<Future<Set<String>>> futures = scatterExecutor.invokeAll(tasks, 5000, TimeUnit.MILLISECONDS);

            // Mapa de Frecuencia: Activo -> Cuántos exchanges lo tienen
            Map<String, Integer> frequencyMap = new HashMap<>();
            int successfulResponses = 0;

            for (Future<Set<String>> f : futures) {
                if (f.state() == Future.State.SUCCESS) {
                    try {
                        Set<String> assets = f.get();
                        if (assets != null && !assets.isEmpty()) {
                            successfulResponses++;
                            for (String asset : assets) {
                                frequencyMap.merge(asset, 1, Integer::sum);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Si no hay al menos 2 exchanges respondiendo, no podemos arbitrar
            if (successfulResponses < 2) return BotConfig.FIXED_ASSETS;

            // FILTRO DE QUÓRUM: El activo debe existir en al menos 2 exchanges
            List<String> commonAssets = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
                if (entry.getValue() >= 2 && !entry.getKey().equals("USDT")) {
                    commonAssets.add(entry.getKey());
                }
            }

            // Intersección con HUNTING_GROUNDS (Configuración .env)
            // Esto asegura que solo operemos lo que el usuario quiere Y tenemos en inventario.
            if (!BotConfig.HUNTING_GROUNDS_SEED.isEmpty()) {
                commonAssets.retainAll(BotConfig.HUNTING_GROUNDS_SEED);
            }

            if (!commonAssets.isEmpty()) {
                BotLogger.info("✅ CFO: Activos operables (Quórum >= 2): " + commonAssets);
                // Pre-calentamiento
                scatterExecutor.submit(() -> updateDirectivesBatch(commonAssets));
            }

            return commonAssets;

        } catch (Exception e) {
            BotLogger.error("❌ Error Discovery: " + e.getMessage());
            return BotConfig.FIXED_ASSETS;
        }
    }

    private Set<String> filterDust(String exchange, Map<String, Double> balances) {
        Set<String> real = new HashSet<>();
        // Si el balance es nulo (error de conexión), retornamos vacío para no romper el flujo
        if (balances == null || balances.isEmpty()) return real;

        Map<String, Double> allPrices = connector.fetchAllPrices(exchange);

        for (Map.Entry<String, Double> e : balances.entrySet()) {
            String asset = e.getKey();
            Double qty = e.getValue();

            // Filtro USDT
            if (asset.equals("USDT")) {
                if (qty > BotConfig.MIN_ASSET_VALUE_USDT) real.add(asset);
                continue;
            }

            // Filtro Crypto (Valor en USD > Mínimo)
            // Intentamos obtener precio. Si no hay precio, no podemos valorizarlo.
            Double price = null;
            if (allPrices != null) {
                price = allPrices.getOrDefault(asset + "USDT", allPrices.get(asset + "-USDT"));
            }

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
                    if (balances == null) return null;

                    Map<String, Double> prices = connector.fetchAllPrices(exchange);
                    double exchangeTotal = 0.0;

                    for (Map.Entry<String, Double> entry : balances.entrySet()) {
                        String asset = entry.getKey();
                        if (asset.equals("USDT")) exchangeTotal += entry.getValue();
                        else if (prices != null) {
                            Double p = prices.getOrDefault(asset + "USDT", prices.get(asset + "-USDT"));
                            if (p != null) exchangeTotal += entry.getValue() * p;
                        }
                    }
                    grandTotal.add(exchangeTotal);
                } catch (Exception ignored) {}
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