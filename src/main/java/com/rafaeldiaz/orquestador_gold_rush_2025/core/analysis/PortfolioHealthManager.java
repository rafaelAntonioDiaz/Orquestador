package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 🧠 CFO QUANTUM v3.1 (SAFE LOCK EDITION)
 * Gestiona el saldo virtual y evita condiciones de carrera "Double Spend".
 */
public class PortfolioHealthManager {

    private final ExchangeConnector connector;
    private final List<String> spatialAccounts;
    private volatile double totalEquityUsdt = 0.0;

    // 🧠 MEMORIA DE CORTO PLAZO (SNAPSHOT API)
    // Guardamos los balances crudos del último barrido
    private final Map<String, Map<String, Double>> balanceSnapshot = new ConcurrentHashMap<>();

    // 📒 LIBRO MAYOR VIRTUAL (LOCKS)
    // Registra los fondos comprometidos en operaciones en vuelo que la API aún no ve.
    // Estructura: Exchange -> Asset -> Cantidad Bloqueada
    private final Map<String, Map<String, DoubleAdder>> virtualLocks = new ConcurrentHashMap<>();
    // Timestamp de cuando se creó el lock para limpieza inteligente
    private final Map<String, Map<String, Long>> lockTimestamps = new ConcurrentHashMap<>();

    private final Map<String, HealthDirective> directiveCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> assetLocations = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService scatterExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public PortfolioHealthManager(ExchangeConnector connector) {
        this.connector = connector;
        this.spatialAccounts = BotConfig.SPATIAL_ACCOUNTS;
        BotLogger.info("🧠 CFO QUANTUM: Libro Mayor Virtual Activado.");
        startHealthMonitor();
    }

    // =========================================================================
    // 🔐 GESTIÓN DE SALDOS VIRTUALES (EL NÚCLEO ANTI-ERROR)
    // =========================================================================

    /**
     * Intenta reservar fondos virtualmente.
     * @return true si hay saldo virtual suficiente, false si no.
     */
    public boolean tryReserveFunds(String exchange, String asset, double amount) {
        // 1. Obtener saldo base del último snapshot
        double baseBalance = balanceSnapshot.getOrDefault(exchange, Collections.emptyMap())
                .getOrDefault(asset, 0.0);

        // 2. Obtener bloqueos actuales
        double currentLocks = getLockedAmount(exchange, asset);
        // 3. Calcular Disponible Virtual
        double virtualAvailable = baseBalance - currentLocks;

        // 4. Decisión
        if (virtualAvailable >= amount * 1.001) {
            // APROBADO: Agregamos el lock
            addLock(exchange, asset, amount);
            return true;
        } else {
            // RECHAZADO: No hay saldo virtual (El Auditor registrará esto fuera)
            return false;
        }
    }

    /**
     * Libera fondos si una operación falla o se cancela antes de ejecutarse.
     */
    public void releaseFunds(String exchange, String asset, double amount) {
        Map<String, DoubleAdder> exLocks = virtualLocks.get(exchange);
        if (exLocks != null) {
            DoubleAdder lock = exLocks.get(asset);
            if (lock != null) {
                // Restamos el lock (DoubleAdder permite suma negativa)
                lock.add(-amount);
                // Evitamos negativos por si acaso
                if (lock.sum() <= 0.000001) lock.reset();
            }
        }
    }

    /**
     * Método auxiliar para consultar saldo disponible sin reservar (para logs)
     */
    public double getVirtualAvailableBalance(String exchange, String asset) {
        double base = balanceSnapshot.getOrDefault(exchange, Collections.emptyMap()).getOrDefault(asset, 0.0);
        double locked = getLockedAmount(exchange, asset);
        return Math.max(0.0, base - locked);
    }

    private double getLockedAmount(String exchange, String asset) {
        Map<String, DoubleAdder> exLocks = virtualLocks.get(exchange);
        if (exLocks == null) return 0.0;
        DoubleAdder lock = exLocks.get(asset);
        return (lock == null) ? 0.0 : Math.max(0.0, lock.sum());
    }

    private void addLock(String exchange, String asset, double amount) {
        virtualLocks.computeIfAbsent(exchange, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(asset, k -> new DoubleAdder())
                .add(amount);
    }

    // =========================================================================
    // 💓 CICLO DE VIDA (SNAPSHOT & RECONCILIACIÓN)
    // =========================================================================

    private void startHealthMonitor() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Descubrimiento y Actualización de Saldos Reales (Snapshot)
                if (BotConfig.AUTO_DISCOVERY) {
                    discoverTradableAssets();
                } else {
                    // Si no hay autodiscovery, al menos actualizamos saldos de los fijos
                    refreshBalancesForFixedAssets();
                }

                // 2. Actualizar Directivas (Opcional en modo guerra, pero mantiene el cache vivo)
                updateDirectivesBatch(BotConfig.AUTO_DISCOVERY ? new ArrayList<>(directiveCache.keySet()) : BotConfig.FIXED_ASSETS);

            } catch (Exception e) {
                BotLogger.error("💔 CFO Heartbeat Falló: " + e.getMessage());
            }
        }, 0, BotConfig.HEALTH_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Descarga saldos reales y RECONCILIA el libro virtual.
     */
    public List<String> discoverTradableAssets() {
        if (!BotConfig.AUTO_DISCOVERY) return BotConfig.FIXED_ASSETS;

        try {
            List<Callable<Map.Entry<String, Map<String, Double>>>> tasks = new ArrayList<>();
            for (String ex : spatialAccounts) {
                tasks.add(() -> {
                    // Fetch saldo real API
                    Map<String, Double> bals = connector.fetchBalances(ex);
                    return Map.entry(ex, bals);
                });
            }

            List<Future<Map.Entry<String, Map<String, Double>>>> futures = scatterExecutor.invokeAll(tasks, 5000, TimeUnit.MILLISECONDS);

            int successfulResponses = 0;
            Map<String, Integer> frequencyMap = new HashMap<>();

            for (Future<Map.Entry<String, Map<String, Double>>> f : futures) {
                if (f.state() == Future.State.SUCCESS) {
                    try {
                        Map.Entry<String, Map<String, Double>> result = f.get();
                        String exchange = result.getKey();
                        Map<String, Double> balances = result.getValue();

                        if (balances != null) {
                            successfulResponses++;

                            // 🔥 RECONCILIACIÓN CRÍTICA 🔥
                            // 1. Actualizamos el Snapshot Base
                            balanceSnapshot.put(exchange, balances);

                            // 2. LIMPIEZA DE LOCKS
                            // Asumimos que el Snapshot ya incluye las órdenes que se ejecutaron hace >2 segundos.
                            // Para HFT, es seguro reiniciar los locks al recibir la verdad absoluta de la API.
                            if (virtualLocks.containsKey(exchange)) {
                                virtualLocks.get(exchange).clear();
                            }

                            // Lógica de descubrimiento (filtro de polvo, etc.)
                            Set<String> cleanAssets = filterDust(exchange, balances);
                            for (String asset : cleanAssets) {
                                frequencyMap.merge(asset, 1, Integer::sum);
                                assetLocations.computeIfAbsent(asset, k -> ConcurrentHashMap.newKeySet()).add(exchange);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            List<String> commonAssets = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
                if (entry.getValue() >= 2 && !entry.getKey().equals("USDT")) {
                    commonAssets.add(entry.getKey());
                }
            }
            if (!BotConfig.HUNTING_GROUNDS_SEED.isEmpty()) {
                commonAssets.retainAll(BotConfig.HUNTING_GROUNDS_SEED);
            }
            return commonAssets;

        } catch (Exception e) {
            BotLogger.error("❌ Error Discovery: " + e.getMessage());
            return BotConfig.FIXED_ASSETS;
        }
    }

    // Método auxiliar para modo sin auto-discovery
    private void refreshBalancesForFixedAssets() {
        for (String ex : spatialAccounts) {
            try {
                Map<String, Double> bals = connector.fetchBalances(ex);
                if (bals != null) {
                    balanceSnapshot.put(ex, bals);
                    if (virtualLocks.containsKey(ex)) virtualLocks.get(ex).clear();
                }
            } catch (Exception e) {/* Silent */}
        }
    }


    private Set<String> filterDust(String exchange, Map<String, Double> balances) {
        Set<String> real = new HashSet<>();
        if (balances == null || balances.isEmpty()) return real;
        // Optimizacion: no pedimos precios si solo hay USDT
        if (balances.size() == 1 && balances.containsKey("USDT")) {
            if (balances.get("USDT") > BotConfig.MIN_ASSET_VALUE_USDT) real.add("USDT");
            return real;
        }

        Map<String, Double> allPrices = connector.fetchAllPrices(exchange);
        for (Map.Entry<String, Double> e : balances.entrySet()) {
            String asset = e.getKey();
            Double qty = e.getValue();
            if (asset.equals("USDT")) {
                if (qty > BotConfig.MIN_ASSET_VALUE_USDT) real.add(asset);
                continue;
            }
            Double price = null;
            if (allPrices != null) price = allPrices.getOrDefault(asset + "USDT", allPrices.get(asset + "-USDT"));
            if (price != null && (qty * price) > BotConfig.MIN_ASSET_VALUE_USDT) real.add(asset);
        }
        return real;
    }

    private void updateDirectivesBatch(List<String> assets) {
        for (String asset : assets) {
            directiveCache.put(asset, new HealthDirective(BotConfig.NORMAL_MIN_PROFIT, Set.of(), Set.of(), "BALANCED"));
        }
    }

    public HealthDirective getAssetHealth(String asset) {
        return directiveCache.computeIfAbsent(asset, k -> new HealthDirective(BotConfig.NORMAL_MIN_PROFIT, Set.of(), Set.of(), "UNKNOWN"));
    }

    public Set<String> getValidExchangesForAsset(String asset) {
        return assetLocations.getOrDefault(asset, Collections.emptySet());
    }
// =========================================================================
    // 📊 AUDITORÍA Y DIAGNÓSTICO (NUEVO BLOQUE REQUERIDO)
    // =========================================================================

    /**
     * Fuerza una actualización inmediata de saldos y calcula el patrimonio total.
     * Usado por SystemDiagnostics durante el arranque.
     */
    public void performAudit() {
        try {
            // 1. Forzar actualización de saldos (Síncrono/Bloqueante para el diagnóstico)
            if (BotConfig.AUTO_DISCOVERY) {
                discoverTradableAssets();
            } else {
                refreshBalancesForFixedAssets();
            }

            // 2. Calcular Patrimonio
            calculateGlobalEquity();
        } catch (Exception e) {
            BotLogger.error("Error en Auditoría CFO: " + e.getMessage());
        }
    }

    /**
     * Retorna el valor total estimado de la cuenta en USDT.
     */
    public double getTotalEquityUsdt() {
        return totalEquityUsdt;
    }

    /**
     * Recorre todos los balances, cotiza las altcoins a precio de mercado y suma todo.
     */
    private void calculateGlobalEquity() {
        double total = 0.0;
        for (String exchange : balanceSnapshot.keySet()) {
            Map<String, Double> balances = balanceSnapshot.get(exchange);
            if (balances == null) continue;

            // Sumar USDT puro
            total += balances.getOrDefault("USDT", 0.0);

            // Sumar Altcoins (Estimación rápida para diagnóstico)
            try {
                // Solo pedimos precios si hay altcoins que valga la pena medir
                boolean hasAltcoins = balances.size() > 1 || !balances.containsKey("USDT");

                if (hasAltcoins) {
                    Map<String, Double> prices = connector.fetchAllPrices(exchange);
                    for (Map.Entry<String, Double> entry : balances.entrySet()) {
                        String asset = entry.getKey();
                        if (asset.equals("USDT")) continue;

                        double qty = entry.getValue();
                        // Intentamos par "ASSETUSDT" o "ASSET-USDT"
                        double price = prices.getOrDefault(asset + "USDT",
                                prices.getOrDefault(asset + "-USDT", 0.0));

                        if (price > 0) {
                            total += qty * price;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignoramos error de precios en auditoría para no detener el boot
                // El total reflejará al menos el USDT seguro.
            }
        }
        this.totalEquityUsdt = total;
    }
    public void shutdown() {
        heartbeatScheduler.shutdownNow();
        scatterExecutor.shutdownNow();
    }

    public record HealthDirective(double minProfitPercent, Set<String> preferredBuyers, Set<String> preferredSellers, String statusLabel) {}
}