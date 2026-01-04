package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@DisplayName("🛡️ Risk Manager - Mathematical & Logic Validation")
class RiskManagerTest {

    private RiskManager riskManager;
    private StubCoordinator stubCoordinator;
    private static MockedStatic<BotConfig> mockedConfig; // Para métodos estáticos si los hubiera

    // 🔥 ARCHIVO AISLADO
    private static final String TEST_STATE_FILE = "test_financial_state_isolated.json";

    // 🕵️ STUB COORDINATOR
    static class StubCoordinator extends ExecutionCoordinator {
        boolean lockdownTriggered = false;
        String lockdownReason = "";

        @Override
        public void forceGlobalLockdown(String reason) {
            this.lockdownTriggered = true;
            this.lockdownReason = reason;
        }
    }

    @BeforeAll
    static void initGlobalMocks() {
        // Mockeamos la clase para interceptar métodos si fuera necesario
        mockedConfig = mockStatic(BotConfig.class);
    }

    @AfterAll
    static void closeGlobalMocks() {
        mockedConfig.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        new File(TEST_STATE_FILE).delete();
        stubCoordinator = new StubCoordinator();

        // 💉 CONFIGURACIÓN MOCKEADA (Esto funciona perfecto en Java 25)
        mockedConfig.when(BotConfig::getRiskMaxDailyLoss).thenReturn(0.02);        // 2%
        mockedConfig.when(BotConfig::getRiskMaxDrawdown).thenReturn(0.08);         // 8%
        mockedConfig.when(BotConfig::getRiskMaxConsecutiveLosses).thenReturn(5);   // 5 Rachas
        mockedConfig.when(BotConfig::getRiskStreakPauseMs).thenReturn(3600000L);   // 1 Hora
        mockedConfig.when(BotConfig::getRiskMcRuinThreshold).thenReturn(0.05);

        riskManager = new RiskManager(stubCoordinator, 1000.0, TEST_STATE_FILE);
    }
    @AfterEach
    void tearDown() {
        new File(TEST_STATE_FILE).delete();
    }

    // ==========================================
    // 🛑 TESTS DE LÍMITES MATEMÁTICOS
    // ==========================================

    @Test
    @DisplayName("🛑 DISYUNTOR DIARIO: Bloqueo exacto al perder >2%")
    void shouldTriggerLockdown_WhenDailyLossExceedsLimit() {
        // Capital $1000. Límite 2% = $20.

        // 1. Pérdida de $19.9 (1.99%) -> Debe seguir vivo
        riskManager.reportTradeResult(-19.9);
        assertThat(riskManager.canExecuteTrade()).isTrue();

        // 2. Pérdida adicional de $0.2 -> Total $20.1 (2.01%) -> MUERTE
        riskManager.reportTradeResult(-0.2);

        assertThat(riskManager.canExecuteTrade())
                .as("Debe bloquearse al superar el 2% diario")
                .isFalse();

        assertThat(stubCoordinator.lockdownTriggered)
                .as("Debe llamar al Coordinador Global")
                .isTrue();
    }

    @Test
    @DisplayName("📈 DRAWDOWN: Bloqueo al caer 8% desde el pico histórico")
    void shouldBlock_WhenDrawdownFromPeak_IsViolated() {
        // 1. Subida: Ganamos $1000 -> Capital $2000 (Nuevo Pico)
        riskManager.reportTradeResult(1000.0);

        // 2. Caída: Límite 8% de $2000 = $160.
        // Perdemos $150 (7.5%) -> Vivo
        riskManager.reportTradeResult(-150.0);
        assertThat(riskManager.canExecuteTrade()).isTrue();

        // Perdemos $11 más -> Total caída $161 (8.05%) -> MUERTE
        riskManager.reportTradeResult(-11.0);

        assertThat(riskManager.canExecuteTrade())
                .as("Debe bloquearse por Max Drawdown del 8%")
                .isFalse();
    }

    // ==========================================
    // ❄️ TESTS DE RACHAS (STREAK)
    // ==========================================

    @Test
    @DisplayName("❄️ RACHA: Pausa temporal tras 5 pérdidas consecutivas")
    void shouldPause_WhenStreakExceedsLimit() {
        // Mock dice límite 5.

        for (int i = 0; i < 4; i++) {
            riskManager.reportTradeResult(-1.0);
        }
        assertThat(riskManager.canExecuteTrade()).as("Con 4 sigue vivo").isTrue();

        riskManager.reportTradeResult(-1.0); // La 5ta

        assertThat(riskManager.canExecuteTrade()).as("Con 5 se pausa").isFalse();
    }

    @Test
    @DisplayName("✨ RACHA: Ganancia resetea contador")
    void shouldResetStreak_OnWin() {
        // 4 pérdidas
        for (int i = 0; i < 4; i++) riskManager.reportTradeResult(-1.0);

        // Ganancia
        riskManager.reportTradeResult(10.0);

        // Nueva pérdida (sería la 5ta sin reset)
        riskManager.reportTradeResult(-1.0);

        assertThat(riskManager.canExecuteTrade()).as("Debió resetearse").isTrue();
    }

    // ==========================================
    // 🔓 TESTS DE ESTADO Y RECUPERACIÓN
    // ==========================================

    @Test
    @DisplayName("🔓 OVERRIDE: Desbloqueo manual")
    void shouldResetStats_OnManualOverride() {
        riskManager.reportTradeResult(-50.0); // Trigger Daily Loss
        assertThat(riskManager.canExecuteTrade()).isFalse();

        riskManager.overrideLockdown();

        assertThat(riskManager.canExecuteTrade()).isTrue();
    }

    @Test
    @DisplayName("🧵 CONCURRENCIA: Integridad de datos")
    void shouldCalculateCorrectly_UnderConcurrency() throws InterruptedException {
        int numTrades = 1000;
        double profitPerTrade = 1.0;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, numTrades).forEach(i -> {
                executor.submit(() -> riskManager.reportTradeResult(profitPerTrade));
            });
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        // 1000 trades de $1 + $1000 inicial = $2000
        // Usamos reflexión para verificar el saldo interno sin getters públicos
        assertThat(getPrivateField(riskManager, "currentCapital"))
                .isEqualTo(2000.0);
    }

    @Test
    @DisplayName("🎲 MONTE CARLO: Validación probabilística")
    void monteCarlo_Validation() {
        // Estrategia suicida (WinRate 10%, Risk/Reward malo)
        assertThat(riskManager.runMonteCarloSimulation(0.1, 10.0, 100.0)).isFalse();

        // Estrategia ganadora (WinRate 60%, R/R 2:1)
        assertThat(riskManager.runMonteCarloSimulation(0.6, 100.0, 50.0)).isTrue();
    }

    // ==========================================
    // 🛠️ HERRAMIENTAS DE INYECCIÓN (DARK MAGIC)
    // ==========================================

    /**
     * Rompe la seguridad de Java para modificar constantes 'static final' en tiempo de test.
     * Esto nos permite probar con valores fijos (2%, 5 rachas) sin depender del .env
     */
    private void setRiskParameter(String fieldName, Object value) throws Exception {
        try {
            Field field = BotConfig.class.getDeclaredField(fieldName);
            field.setAccessible(true);

            // Truco para quitar 'final' (Funciona en Java < 17, y en algunos setups de test en Java 21+)
            // Si falla en Java 25 estricto, el test usará los valores del .env y imprimirá advertencia.
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            field.set(null, value);
        } catch (Exception e) {
            // Fallback silencioso: Si no podemos inyectar, usamos lo que haya en el .env
            System.err.println("⚠️ WARN: No se pudo inyectar " + fieldName + ". Usando valor real del .env.");
        }
    }

    private double getPrivateField(Object target, String fieldName) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getDouble(target);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}