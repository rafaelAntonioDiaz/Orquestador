package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.AdaptiveSpatialStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 🧠 TEST DE ESTRÉS: CEREBRO PROBABILÍSTICO (v3.1 - ALINEACIÓN DEFENSIVA)
 */
@DisplayName("🧠 Probabilistic Brain - Heavy Duty Test")
class ProbabilisticBrainTest {

    private static MockedStatic<BotConfig> mockedConfig;
    private MarketCortex cortex;
    private ProbabilisticOracle oracle;

    @BeforeAll
    static void initStaticMocks() {
        mockedConfig = mockStatic(BotConfig.class);

        // CONFIGURACIÓN ROBUSTA:
        // Usamos 5 ticks (valor default real) para evitar desajustes si el Oracle usa la constante directa.
        mockedConfig.when(BotConfig::getOracleHistorySize).thenReturn(20); // Suficiente memoria
        mockedConfig.when(BotConfig::getOracleLeadLagTicks).thenReturn(5); // Alineado con Default
        mockedConfig.when(BotConfig::getOracleZScoreThreshold).thenReturn(2.0);
        mockedConfig.when(BotConfig::getMinScanSpread).thenReturn(0.003);
        mockedConfig.when(BotConfig::getOracleAggressiveSpread).thenReturn(0.001);
        mockedConfig.when(BotConfig::getOracleMinConfidence).thenReturn(0.8);
        mockedConfig.when(BotConfig::getAdvisorRefExchange).thenReturn("binance");
    }

    @AfterAll
    static void closeStaticMocks() {
        mockedConfig.close();
    }

    @BeforeEach
    void setUp() {
        cortex = new MarketCortex();
        oracle = new ProbabilisticOracle(cortex);
    }

    // =========================================================================
    // 🧠 1. TEST DE MARKET CORTEX
    // =========================================================================

    @Test
    @DisplayName("Cortex: Buffer Circular y Velocidad")
    void testCortexMemoryAndVelocity() {
        String asset = "BTC";
        String exchange = "binance";
        int ticks = 5; // Usamos 5 explícitamente

        // 1. Ingesta Inicial
        cortex.ingest(Map.of(exchange, Map.of(asset + "USDT", 100.0)));

        // 2. Llenamos datos suficientes para cubrir (ticks + 1)
        // Necesitamos al menos 6 puntos de datos para comparar T vs T-5
        for (int i = 0; i < ticks; i++) {
            cortex.ingest(Map.of(exchange, Map.of(asset + "USDT", 100.0)));
        }

        // 3. Salto Final
        cortex.ingest(Map.of(exchange, Map.of(asset + "USDT", 101.0)));

        // Velocity = (101 - 100) / 100 = 0.01
        double velocity = cortex.getPriceVelocity(asset, exchange, ticks);

        assertEquals(0.01, velocity, 0.0001);
    }

    @Test
    @DisplayName("Cortex: Matemáticas Welford (Z-Score con Ruido)")
    void testWelfordAlgorithm() {
        String asset = "ETH";

        // Inyectamos varianza real (Ruido)
        for (int i=0; i<10; i++) {
            cortex.recordSpread(asset, 2.9);
            cortex.recordSpread(asset, 3.1);
        }
        // Media ~3.0

        // Evento Extremo: Spread 10.0
        double zScore = cortex.getSpreadZScore(asset, 10.0);

        assertTrue(zScore > 2.0, "Z-Score debe ser alto (>2.0)");
    }

    // =========================================================================
    // 🔮 2. TEST DEL ORÁCULO
    // =========================================================================

    @Test
    @DisplayName("Oracle: Detección de Lead-Lag (Full Depth)")
    void testOracleLeadLag() {
        String asset = "SOL";
        String leader = "binance";
        String follower = "bybit";
        int ticks = 5; // Alineado con la configuración Mock y Default

        // FASE 1: PRE-CALENTAMIENTO (Llenar Buffer)
        // Insertamos suficientes datos planos (ticks + 2) para asegurar que el buffer no esté vacío
        // T=0 a T=6 (Todo 100.0)
        for (int i = 0; i <= ticks + 2; i++) {
            cortex.ingest(Map.of(
                    leader, Map.of(asset+"USDT", 100.0),
                    follower, Map.of(asset+"USDT", 100.0)
            ));
        }

        // FASE 2: EXPLOSIÓN (El evento Lead-Lag)
        // Binance sube a 102 (2%), Bybit se queda en 100 (0%)
        cortex.ingest(Map.of(
                leader, Map.of(asset+"USDT", 102.0),
                follower, Map.of(asset+"USDT", 100.0)
        ));

        // Consultamos al Oráculo
        var verdict = oracle.getVerdict(asset, 0.02, follower);

        // DEBUG: Si falla, imprime qué pasó
        if (!"LEAD_LAG".equals(verdict.signalSource())) {
            System.out.println("❌ FALLO ORÁCULO. Confianza: " + verdict.confidenceScore() + " | Fuente: " + verdict.signalSource());
        }

        assertEquals("LEAD_LAG", verdict.signalSource());
        assertTrue(verdict.confidenceScore() >= 0.85);
        assertEquals(0.001, verdict.suggestedThreshold(), 0.00001);
    }

    // =========================================================================
    // 🎯 3. TEST DE ESTRATEGIA
    // =========================================================================

    @Test
    @DisplayName("Strategy: Robustez con CFO Null")
    void testStrategyWithoutCFO() {
        AdaptiveSpatialStrategy strategy = new AdaptiveSpatialStrategy(null);
        Map<String, Map<String, Double>> prices = new HashMap<>();
        prices.put("binance", Map.of("BTCUSDT", 50000.0));
        prices.put("kucoin", Map.of("BTCUSDT", 51000.0));

        List<ArbitrageOpportunity> opps = strategy.findOpportunities("BTC", prices);
        assertFalse(opps.isEmpty());
    }

    @Test
    @DisplayName("Strategy: Filtrado de Umbral Mínimo")
    void testStrategyThreshold() {
        AdaptiveSpatialStrategy strategy = new AdaptiveSpatialStrategy(null);
        Map<String, Map<String, Double>> prices = new HashMap<>();
        prices.put("binance", Map.of("BTCUSDT", 100.000));
        prices.put("kucoin", Map.of("BTCUSDT", 100.001));

        List<ArbitrageOpportunity> opps = strategy.findOpportunities("BTC", prices);
        assertTrue(opps.isEmpty());
    }
}