package com.rafaeldiaz.orquestador_gold_rush_2025.execution;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.OrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 🎯 TRADE EXECUTOR TEST (Integridad Secuencial)
 * Versión Sincronizada con OrderResult v5.1 (Record de 8 campos).
 */
class TriangularExecutorTest {

    @Mock
    private ExchangeConnector mockConnector;

    private TriangularExecutor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executor = new TriangularExecutor(mockConnector);
        executor.setDryRun(false); // 🔥 FUEGO REAL (Simulado)
    }

    @Test
    @DisplayName("✅ SECUENCIA PERFECTA: Entry -> Bridge -> Exit")
    void shouldExecutePerfectSequence() {
        // [Escenario Ideal]
        // 1. ENTRY: USDT -> PEPE (Limit FOK)
        // Compramos 1000 PEPE. Costo (Quote): 10 USDT.
        OrderResult r1 = new OrderResult(
                "1", "FILLED",
                1000.0, 1000.0, // Requested vs Executed Qty
                10.0,           // Quote Qty (USDT gastados)
                0.01, 0.0, "BNB" // Limit, Fee, FeeAsset
        );

        when(mockConnector.placeOrder(anyString(), eq("PEPEUSDT"), eq("BUY"), contains("FOK"), anyDouble(), anyDouble()))
                .thenReturn(r1);

        // 2. BRIDGE: PEPE -> SOL (Market)
        // Vendemos 1000 PEPE. Recibimos (Quote): 20 SOL.
        OrderResult r2 = new OrderResult(
                "2", "FILLED",
                1000.0, 1000.0, // Vendemos 1000 PEPE
                20.0,           // Quote Qty (20 SOL Recibidos -> CRÍTICO para el siguiente paso)
                0.0, 0.0, "BNB"
        );

        when(mockConnector.placeOrder(anyString(), eq("PEPESOL"), eq("SELL"), eq("MARKET"), eq(1000.0), anyDouble()))
                .thenReturn(r2);

        // Mockeamos el balance por si acaso el safety check lo pide
        when(mockConnector.fetchBalance(anyString(), eq("SOL"))).thenReturn(20.0);

        // 3. EXIT: SOL -> USDT (Market)
        // Vendemos 20 SOL. Recibimos (Quote): 1010 USDT.
        OrderResult r3 = new OrderResult(
                "3", "FILLED",
                20.0, 20.0,     // Vendemos 20 SOL
                1010.0,         // Quote Qty (1010 USDT Recibidos -> Profit!)
                0.0, 0.0, "BNB"
        );

        when(mockConnector.placeOrder(anyString(), eq("SOLUSDT"), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble()))
                .thenReturn(r3);

        // --- EJECUCIÓN ---
        // Capital inicial: 1000 USDT. Precio ref: 1.0
        executor.executeSequence("binance", "PEPE", "SOL", "PEPEUSDT", "PEPESOL", "SOLUSDT", 1000.0, 1.0);

        // --- VALIDACIÓN DE ORDEN (Strict Order) ---
        InOrder inOrder = inOrder(mockConnector);

        // 1. Compra PEPE
        inOrder.verify(mockConnector).placeOrder(eq("binance"), eq("PEPEUSDT"), eq("BUY"), contains("FOK"), anyDouble(), anyDouble());

        // 2. Vende PEPE por SOL (Usando los 1000 adquiridos en r1.executedQty)
        inOrder.verify(mockConnector).placeOrder(eq("binance"), eq("PEPESOL"), eq("SELL"), eq("MARKET"), eq(1000.0), anyDouble());

        // 3. Vende SOL por USDT (Usando los 20 adquiridos en r2.executedValue -> 19.98 con buffer)
        inOrder.verify(mockConnector).placeOrder(eq("binance"), eq("SOLUSDT"), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble());

        System.out.println("✅ TEST PERFECT SEQUENCE: Pasó (Flujo validado con Record v5.1)");
    }

    @Test
    @DisplayName("🚑 EMERGENCIA: Fallo en Paso 2 -> Reversión Inmediata")
    void shouldHandleEmergencyExit_WhenBridgeFails() {
        // [Escenario de Pánico]
        // 1. ENTRY: Éxito. Compramos 1000 PEPE.
        OrderResult r1 = new OrderResult(
                "1", "FILLED",
                1000.0, 1000.0,
                10.0, 0.01, 0.0, "BNB"
        );

        when(mockConnector.placeOrder(anyString(), eq("PEPEUSDT"), eq("BUY"), contains("FOK"), anyDouble(), anyDouble()))
                .thenReturn(r1);

        // 2. BRIDGE: FALLO (REJECTED/CANCELED).
        // El exchange dice "No puedo vender PEPE x SOL".
        OrderResult r2Fail = new OrderResult(
                "2", "REJECTED",
                1000.0, 0.0, // 0 Ejecutado
                0.0,         // 0 Recibido
                0.0, 0.0, "BNB"
        );

        when(mockConnector.placeOrder(anyString(), eq("PEPESOL"), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble()))
                .thenReturn(r2Fail);

        // --- EJECUCIÓN ---
        executor.executeSequence("binance", "PEPE", "SOL", "PEPEUSDT", "PEPESOL", "SOLUSDT", 1000.0, 1.0);

        // --- VALIDACIÓN ---
        // Paso 1: OK
        verify(mockConnector).placeOrder(eq("binance"), eq("PEPEUSDT"), eq("BUY"), anyString(), anyDouble(), anyDouble());
        // Paso 2: Intentó y falló
        verify(mockConnector).placeOrder(eq("binance"), eq("PEPESOL"), eq("SELL"), anyString(), anyDouble(), anyDouble());

        // Paso 3 (SOL -> USDT): NO DEBIÓ OCURRIR
        verify(mockConnector, never()).placeOrder(eq("binance"), eq("SOLUSDT"), eq("SELL"), anyString(), anyDouble(), anyDouble());

        // EMERGENCIA (PEPE -> USDT): SÍ DEBIÓ OCURRIR
        // Vende los 1000 PEPE originales de vuelta a USDT
        verify(mockConnector).placeOrder(eq("binance"), eq("PEPEUSDT"), eq("SELL"), eq("MARKET"), eq(1000.0), anyDouble());

        System.out.println("✅ TEST EMERGENCY EXIT: Detectó fallo en puente y activó venta de pánico.");
    }

    @Test
    @DisplayName("🛡️ SEGURIDAD: Dry Run no dispara nada")
    void shouldRespectDryRun() {
        executor.setDryRun(true);
        executor.executeSequence("binance", "A", "B", "P1", "P2", "P3", 100.0, 1.0);
        verifyNoInteractions(mockConnector);
        System.out.println("✅ TEST DRY RUN: Confirmado.");
    }
    @Test
    @DisplayName("🧹 SWEEP: Fallo en Paso 3 (Exit) -> Activa Venta de Barrido Final")
    void shouldHandleSweep_WhenExitFails() {
        // [Escenario: Quedamos atrapados en el Bridge]

        // 1. ENTRY: Éxito (Compramos 1000 PEPE)
        OrderResult r1 = new OrderResult("1", "FILLED", 1000.0, 1000.0, 10.0, 0.0, 0.0, "BNB");
        when(mockConnector.placeOrder(anyString(), eq("PEPEUSDT"), eq("BUY"), contains("FOK"), anyDouble(), anyDouble()))
                .thenReturn(r1);

        // 2. BRIDGE: Éxito (Vendemos PEPE -> Recibimos 20 SOL)
        OrderResult r2 = new OrderResult("2", "FILLED", 1000.0, 1000.0, 20.0, 0.0, 0.0, "BNB");
        when(mockConnector.placeOrder(anyString(), eq("PEPESOL"), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble()))
                .thenReturn(r2);

        // 3. EXIT: FALLO INICIAL (El exchange rechaza la venta calculada)
        OrderResult r3Fail = new OrderResult("3", "REJECTED", 0.0, 0.0, 0.0, 0.0, 0.0, "BNB");

        // 4. EXIT: INTENTO DE SWEEP (Éxito)
        OrderResult r3Sweep = new OrderResult("4", "FILLED", 20.0, 20.0, 1000.0, 0.0, 0.0, "BNB");

        // Configuración del Mock para SOLUSDT
        when(mockConnector.placeOrder(anyString(), eq("SOLUSDT"), eq("SELL"), eq("MARKET"), anyDouble(), anyDouble()))
                .thenReturn(r3Fail)   // 1ra llamada: Falla
                .thenReturn(r3Sweep); // 2da llamada: Sweep Éxito

        // Mockeamos el balance real atrapado (20 SOL)
        when(mockConnector.fetchBalance(anyString(), eq("SOL"))).thenReturn(20.0);

        // --- EJECUCIÓN ---
        executor.executeSequence("binance", "PEPE", "SOL", "PEPEUSDT", "PEPESOL", "SOLUSDT", 1000.0, 1.0);

        // --- VALIDACIÓN ---
        InOrder inOrder = inOrder(mockConnector);

        // Pasos 1 y 2
        inOrder.verify(mockConnector).placeOrder(anyString(), eq("PEPEUSDT"), anyString(), anyString(), anyDouble(), anyDouble());
        inOrder.verify(mockConnector).placeOrder(anyString(), eq("PEPESOL"), anyString(), anyString(), anyDouble(), anyDouble());

        // Paso 3: INTENTO FALLIDO (Venta con buffer < 20.0)
        // 🔥 CORRECCIÓN AQUÍ: Usamos doubleThat() para evitar el NPE
        inOrder.verify(mockConnector).placeOrder(
                anyString(),
                eq("SOLUSDT"),
                eq("SELL"),
                eq("MARKET"),
                doubleThat(qty -> qty < 20.0), // <--- doubleThat es seguro para primitivos
                anyDouble()
        );

        // Paso 3: CONSULTA DE BALANCE
        inOrder.verify(mockConnector).fetchBalance(anyString(), eq("SOL"));

        // Paso 3: SWEEP (BARRIDO TOTAL)
        inOrder.verify(mockConnector).placeOrder(anyString(), eq("SOLUSDT"), eq("SELL"), eq("MARKET"), eq(20.0), anyDouble());

        System.out.println("✅ TEST SWEEP: Fallo en salida manejado correctamente con barrido de saldo.");
    }
}