package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🧠 CFO AUTÓNOMO (GERENTE DE SALUD FINANCIERA)
 * Controla el Auto-Descubrimiento de activos y el Rebalanceo de Inventario.
 */
public class PortfolioHealthManager {

    private final ExchangeConnector connector;
    private final List<String> spatialAccounts;
    private double totalEquityUsdt = 0.0;
    // Caché de Directivas (Para no saturar la API)
    private final Map<String, HealthDirective> directiveCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateMap = new ConcurrentHashMap<>();

    public PortfolioHealthManager(ExchangeConnector connector) {
        this.connector = connector;
        this.spatialAccounts = BotConfig.SPATIAL_ACCOUNTS;
        BotLogger.info("🧠 CFO INICIADO: Gestionando cuentas " + spatialAccounts);
    }

    /**
     * 🕵️ AUTO-DESCUBRIMIENTO: Cruza los inventarios para ver qué operar.
     */
    public List<String> discoverTradableAssets() {
        if (!BotConfig.AUTO_DISCOVERY) return BotConfig.FIXED_ASSETS;

        BotLogger.info("🕵️ CFO: Realizando Auditoría de Inventario...");
        try {
            String exA = spatialAccounts.get(0);
            String exB = spatialAccounts.get(1);

            Map<String, Double> balA = connector.fetchBalances(exA);
            Map<String, Double> balB = connector.fetchBalances(exB);

            Set<String> assetsA = filterDust(exA, balA);
            Set<String> assetsB = filterDust(exB, balB);

            // La Intersección Mágica (Solo lo que existe en ambos lados)
            List<String> commonAssets = new ArrayList<>();
            for (String asset : assetsA) {
                if (assetsB.contains(asset) && !asset.equals("USDT")) {
                    commonAssets.add(asset);
                }
            }

            if(commonAssets.isEmpty()) {
                BotLogger.warn("⚠️ CFO: No hay activos comunes entre " + exA + " y " + exB + ". (Solo USDT?)");
            } else {
                BotLogger.info("✅ CFO: Portafolio Activo Detectado: " + commonAssets);
            }
            return commonAssets;
        } catch (Exception e) {
            BotLogger.error("❌ CFO Error en Discovery: " + e.getMessage());
            return BotConfig.FIXED_ASSETS; // Fallback seguro
        }
    }

    /**
     * Filtra monedas "basura" (< $5 USD) usando Batch Price Fetch.
     * OPTIMIZADO: 1 llamada API en lugar de N llamadas.
     */
    private Set<String> filterDust(String exchange, Map<String, Double> balances) {
        Set<String> real = new HashSet<>();

        // 🚀 OPTIMIZACIÓN: Descargamos todo el mercado en 1 sola llamada
        Map<String, Double> allPrices = connector.fetchAllPrices(exchange);

        for (Map.Entry<String, Double> e : balances.entrySet()) {
            String asset = e.getKey();
            Double qty = e.getValue();

            // Caso base: USDT
            if (asset.equals("USDT")) {
                if (qty > BotConfig.MIN_ASSET_VALUE_USDT) real.add(asset);
                continue;
            }

            // Búsqueda en memoria (HashMap O(1))
            // Probamos formatos comunes (SOLUSDT o SOL-USDT)
            Double price = allPrices.get(asset + "USDT");
            if (price == null) {
                price = allPrices.get(asset + "-USDT"); // Soporte Kucoin/Legacy
            }

            // Si no encontramos precio (moneda deslistada o rara), asumimos 0
            double finalPrice = (price != null) ? price : 0.0;

            if ((qty * finalPrice) > BotConfig.MIN_ASSET_VALUE_USDT) {
                real.add(asset);
            }
        }
        return real;
    }
    /**
     * 🩺 DIAGNÓSTICO DE SALUD (PID Controller Simplificado)
     */
    /**
     * 🩺 DIAGNÓSTICO DE SALUD DE ENJAMBRE (Cluster Health Check)
     * Compatible con DeepMarketScanner N-Way.
     */
    public HealthDirective getAssetHealth(String asset) {
        long now = System.currentTimeMillis();
        long lastTime = lastUpdateMap.getOrDefault(asset, 0L);

        // 1. Check Caché
        if ((now - lastTime) < (BotConfig.HEALTH_CHECK_INTERVAL * 1000L)) {
            if (directiveCache.containsKey(asset)) return directiveCache.get(asset);
        }

        // 2. Recopilar datos de todo el enjambre
        Map<String, Double> assetBalances = new HashMap<>();
        Map<String, Double> usdtBalances = new HashMap<>();

        double totalAsset = 0;
        double totalUsdt = 0;

        for (String ex : spatialAccounts) {
            double aBal = connector.fetchBalance(ex, asset);
            double uBal = connector.fetchBalance(ex, "USDT");

            assetBalances.put(ex, aBal);
            usdtBalances.put(ex, uBal);

            totalAsset += aBal;
            totalUsdt += uBal;
        }

        // 3. Definir "Cuota Justa" (Fair Share)
        double fairShareAsset = (totalAsset > 0) ? (totalAsset / spatialAccounts.size()) : 0;
        double fairShareUsdt = (totalUsdt > 0) ? (totalUsdt / spatialAccounts.size()) : 0;

        // 4. Umbrales Críticos
        double criticalAssetThreshold = fairShareAsset * BotConfig.IMBALANCE_TOLERANCE;
        double criticalUsdtThreshold = fairShareUsdt * BotConfig.IMBALANCE_TOLERANCE;

        // 5. Identificar cuentas "Hambrientas"
        // preferredBuyers = Cuentas que necesitan STOCK (Asset) -> Prioridad: Comprar aquí
        Set<String> needAsset = new HashSet<>();

        // preferredSellers = Cuentas que necesitan CASH (USDT) -> Prioridad: Vender aquí
        Set<String> needCash = new HashSet<>();

        for (String ex : spatialAccounts) {
            if (assetBalances.get(ex) < criticalAssetThreshold) needAsset.add(ex);
            if (usdtBalances.get(ex) < criticalUsdtThreshold) needCash.add(ex);
        }

        // 6. Determinar Estado Global
        String state = (needAsset.isEmpty() && needCash.isEmpty()) ? "BALANCED" : "CRITICAL";
        double minProfit = state.equals("BALANCED") ? BotConfig.NORMAL_MIN_PROFIT : BotConfig.EMERGENCY_MIN_PROFIT;

        // ✅ CREAMOS EL RECORD COMPATIBLE CON EL ESCÁNER
        HealthDirective directive = new HealthDirective(minProfit, needAsset, needCash, state);

        directiveCache.put(asset, directive);
        lastUpdateMap.put(asset, now);

        return directive;
    }

    // ==========================================
    // 📦 EL RECORD QUE FALTABA (Versión N-Way)
    // ==========================================
    public record HealthDirective(
            double minProfitPercent,
            Set<String> preferredBuyers,  // ✅ Ahora sí existe: Exchanges que necesitan STOCK
            Set<String> preferredSellers, // ✅ Ahora sí existe: Exchanges que necesitan USDT
            String statusLabel
    ) {}
    /**
     * Calcula el valor total del portafolio en USDT sumando todas las cuentas.
     */
    public void performAudit() {
        BotLogger.info("💰 CFO: Iniciando Auditoría Global de Patrimonio...");
        double grandTotal = 0.0;

        for (String exchange : spatialAccounts) {
            try {
                // 1. Traer saldos
                Map<String, Double> balances = connector.fetchBalances(exchange);
                // 2. Traer precios (Batch) para no saturar
                Map<String, Double> prices = connector.fetchAllPrices(exchange);

                double exchangeTotal = 0.0;

                for (Map.Entry<String, Double> entry : balances.entrySet()) {
                    String asset = entry.getKey();
                    double qty = entry.getValue();

                    if (asset.equals("USDT")) {
                        exchangeTotal += qty;
                    } else {
                        // Intentamos buscar el precio
                        Double price = prices.get(asset + "USDT");
                        if (price == null) price = prices.get(asset + "-USDT"); // Kucoin fallback

                        if (price != null) {
                            exchangeTotal += qty * price;
                        }
                    }
                }
                grandTotal += exchangeTotal;
            } catch (Exception e) {
                BotLogger.warn("⚠️ Error auditando " + exchange + ": " + e.getMessage());
            }
        }

        this.totalEquityUsdt = grandTotal;
        // BotLogger.info("💰 Resultado Auditoría: $" + String.format("%.2f", grandTotal));
    }

    public double getTotalEquityUsdt() {
        return totalEquityUsdt;
    }
}