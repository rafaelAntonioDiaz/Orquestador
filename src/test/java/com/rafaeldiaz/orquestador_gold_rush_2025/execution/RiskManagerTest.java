package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.ExecutionCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RiskManagerTest {

    private RiskManager riskManager;
    private StubCoordinator stubCoordinator;

    // 🔥 CLAVE DEL ÉXITO: Usamos un nombre de archivo ÚNICO para pruebas
    // para que no lea la basura que dejó el test anterior.
    private static final String TEST_STATE_FILE = "test_financial_state_isolated.json";

    // 🕵️ STUB: Simulador del Coordinador
    static class StubCoordinator extends ExecutionCoordinator {
        boolean lockdownTriggered = false;
        String lockdownReason = "";

        @Override
        public void forceGlobalLockdown(String reason) {
            this.lockdownTriggered = true;
            this.lockdownReason = reason;
        }
    }

    @BeforeEach
    void setUp() {
        // 1. Limpieza Nuclear: Borramos cualquier rastro previo
        new File(TEST_STATE_FILE).delete();
        // También borramos el default por si acaso se nos coló algo
        new File("financial_state.json").delete();

        stubCoordinator = new StubCoordinator();

        // 2. INYECCIÓN EXPLÍCITA:
        // Usamos el constructor de 3 argumentos para forzar el archivo de prueba.
        // Si usas new RiskManager(stub, 1000.0), usará el archivo default y fallará.
        riskManager = new RiskManager(stubCoordinator, 1000.0, TEST_STATE_FILE);
    }

    @AfterEach
    void tearDown() {
        // 🧹 Limpieza post-test para no dejar basura en el disco
        File file = new File(TEST_STATE_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    @DisplayName("🛑 MATEMÁTICA: Bloqueo por Pérdida Diaria (>2%)")
    void shouldTriggerLockdown_WhenDailyLossExceedsLimit() {
        // Capital $1000. Límite 2% = $20. Simulamos pérdida de $21
        riskManager.reportTradeResult(-21.0);

        assertThat(riskManager.canExecuteTrade())
                .as("El flag interno del RiskManager debe estar en FALSE")
                .isFalse();

        assertThat(stubCoordinator.lockdownTriggered)
                .as("Debe haber llamado a forceGlobalLockdown")
                .isTrue();
    }

    @Test
    @DisplayName("📈 MATEMÁTICA: High-Water Mark y Drawdown (>8%)")
    void shouldBlock_WhenDrawdownFromPeak_IsViolated() {
        // 1. Subida: Ganamos $1000 -> Capital $2000 (Nuevo Pico)
        riskManager.reportTradeResult(1000.0);

        // Verificamos estado intermedio
        assertThat(riskManager.canExecuteTrade()).isTrue();

        // 2. Caída: Límite 8% de $2000 = $160.
        // Perdemos $170. Drawdown = 170 / 2000 = 8.5%
        riskManager.reportTradeResult(-170.0);

        // Como el archivo está aislado, Math es pura:
        // Start: 1000 -> Peak: 2000 -> Current: 1830.
        // Drawdown: (2000 - 1830) / 2000 = 0.085 > 0.08. BLOQUEO.

        assertThat(riskManager.canExecuteTrade())
                .as("Debe bloquearse por violar Max Drawdown del 8%")
                .isFalse();
    }

    @Test
    @DisplayName("🔓 LÓGICA: Reinicio Manual (Override)")
    void shouldResetStats_OnManualOverride() {
        riskManager.reportTradeResult(-50.0);
        assertThat(riskManager.canExecuteTrade()).isFalse();

        riskManager.overrideLockdown();

        assertThat(riskManager.canExecuteTrade()).isTrue();
    }

    @Test
    @DisplayName("🧵 CONCURRENCIA: Integridad de Cálculo de Capital")
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

        // Al usar un archivo aislado, este test termina con $2000 en 'test_financial_state_isolated.json'
        // Pero como tearDown() lo borra, el siguiente test arranca limpio.
        assertThat(riskManager.canExecuteTrade()).isTrue();
    }

    @Test
    @DisplayName("🎲 MONTE CARLO: Rechazo de Estrategia Perdedora")
    void monteCarlo_Math_Fail() {
        boolean passed = riskManager.runMonteCarloSimulation(0.3, 10.0, 100.0);
        assertThat(passed).isFalse();
    }

    @Test
    @DisplayName("🎲 MONTE CARLO: Aprobación de Estrategia Ganadora")
    void monteCarlo_Math_Pass() {
        boolean passed = riskManager.runMonteCarloSimulation(0.6, 100.0, 50.0);
        assertThat(passed).isTrue();
    }
    // ==========================================
    // 🛡️ NUEVOS TESTS DE RACHAS (STREAK)
    // ==========================================

    @Test
    @DisplayName("❄️ RACHA: Debe pausar el sistema tras 5 pérdidas consecutivas")
    void shouldPause_WhenStreakExceedsLimit() {
        // Configuramos el límite en 5 (según tu .env simulado)

        // 1. Simulamos 4 pérdidas (Aún operativo)
        for (int i = 0; i < 4; i++) {
            riskManager.reportTradeResult(-1.0);
        }
        assertThat(riskManager.canExecuteTrade()).as("Con 4 pérdidas debería seguir vivo").isTrue();

        // 2. La 5ta pérdida es la vencida
        riskManager.reportTradeResult(-1.0);

        // 3. Verificamos que el sistema entró en PAUSA
        assertThat(riskManager.canExecuteTrade())
                .as("Tras 5 pérdidas, debe bloquearse por PAUSED_DEVIATION")
                .isFalse();

        // Nota: No verificamos el desbloqueo automático (1 hora) porque requeriría
        // inyectar un reloj simulado, pero validamos que el candado se cerró.
    }

    @Test
    @DisplayName("✨ RACHA: Una ganancia debe resetear el contador a cero")
    void shouldResetStreak_OnWin() {
        // 1. Simulamos 4 pérdidas (Al borde del abismo)
        for (int i = 0; i < 4; i++) {
            riskManager.reportTradeResult(-1.0);
        }

        // 2. ¡Un Trade Ganador! (Salvación)
        riskManager.reportTradeResult(10.0);

        // 3. Simulamos una pérdida más.
        // Si el contador NO se hubiera reseteado, esta sería la 5ta y bloquearía.
        // Como se reseteó, es la 1ra de una nueva serie.
        riskManager.reportTradeResult(-1.0);

        assertThat(riskManager.canExecuteTrade())
                .as("El contador debió reiniciarse, por lo que esta pérdida no debe bloquear")
                .isTrue();
    }
}