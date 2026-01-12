package com.rafaeldiaz.orquestador_gold_rush_2025.core.estimator;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector.OrderBook;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.FeeManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.interfaces.MarketDataProvider;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.BalanceSnapshot;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 🧪 STANDARD PROFIT ESTIMATOR TEST (Mockito Edition)
 * Valida la lógica financiera con precisión quirúrgica.
 */
@ExtendWith(MockitoExtension.class)
class StandardProfitEstimatorTest {

    @Mock private FeeManager feeManager;
    @Mock private PortfolioHealthManager cfo;
    @Mock private MarketDataProvider dataProvider;
    @Mock private BalanceSnapshot balances;

    private StandardProfitEstimator estimator;
    private final List<Double> testCapitals = Arrays.asList(100.0, 10.0); // Probaremos con $10 y $100

    @BeforeEach
    void setUp() {
        // Inicializamos el estimador con nuestros Mocks
        estimator = new StandardProfitEstimator(feeManager, cfo, testCapitals);

        // CONFIGURACIÓN BASE (Lenient para evitar errores de stubbing innecesarios en setups globales)
        // 1. Siempre hay saldo físico por defecto
        lenient().when(balances.getAvailableBalance(anyString(), anyString())).thenReturn(5000.0);

        // 2. Siempre hay saldo virtual por defecto
        lenient().when(cfo.getVirtualAvailableBalance(anyString(), anyString())).thenReturn(5000.0);
        lenient().when(cfo.tryReserveFunds(anyString(), anyString(), anyDouble())).thenReturn(true);
    }

    // =========================================================================
    // 🟢 CASO 1: EL EFECTO MEXC (0% FEES)
    // =========================================================================
    @Test
    @DisplayName("Caso MEXC: Debe calcular Profit SIN descontar fee de venta")
    void testMexcZeroFeeProfitability() {
        printTestHeader("CASO 1: BINANCE -> MEXC (La ventaja del 0%)");

        // --- 1. DATOS DEL MERCADO ---
        // Binance (Compra): Precio 1.000 | Fee 0.1%
        // MEXC (Venta): Precio 1.005 (+0.5% Spread) | Fee 0.0% (¡GRATIS!)
        double priceBinance = 1.000;
        double priceMexc = 1.005;

        mockOrderBook("BINANCE", "BTCUSDT", priceBinance); // Venden a 1.00
        mockOrderBook("MEXC", "BTCUSDT", priceMexc);       // Compran a 1.005

        // --- 2. CONFIGURACIÓN DE FEES ---
        when(feeManager.getTradingFee("BINANCE", "BTCUSDT", "TAKER")).thenReturn(0.001); // 0.1%
        when(feeManager.getTradingFee("MEXC", "BTCUSDT", "TAKER")).thenReturn(0.000);    // 0.0% !

        ArbitrageOpportunity opp = createOpp("SPATIAL", "BTC", "BINANCE", "MEXC", priceBinance, priceMexc);

        // --- 3. EJECUCIÓN ---
        ArbitrageOpportunity result = estimator.estimateProfitability(opp, balances, dataProvider);

        // --- 4. VERIFICACIÓN Y DESGLOSE ---
        assertNotNull(result, "La oportunidad debería ser aprobada");

        // Validamos que haya usado el capital de $100 (porque hay liquidez)
        double capitalUsado = result.quantity() * result.priceEntry();
        assertEquals(100.0, capitalUsado, 0.01, "Debe usar el capital máximo posible ($100)");

        // CÁLCULO MANUAL ESPERADO PARA VALIDAR
        // Compra: $100 / 1.00 = 100 BTC
        // Costo Total (Contable): $100 * (1 + 0.001) = $100.10
        // Venta Bruta: 100 BTC * 1.005 = $100.50
        // Neto Venta (MEXC 0%): $100.50 * (1 - 0.0) = $100.50
        // Profit: $100.50 - $100.10 = $0.40

        assertEquals(0.40, result.expectedProfit(), 0.0001, "El profit debe reflejar el 0% fee de venta");

        imprimirDesgloseForense(capitalUsado, priceBinance, priceMexc, 0.001, 0.000, result.expectedProfit());
    }

    // =========================================================================
    // 🟡 CASO 2: LA COMPARACIÓN (BINANCE -> BYBIT CON FEES)
    // =========================================================================
    @Test
    @DisplayName("Caso Estándar: Debe descontar fees en AMBOS lados")
    void testStandardFeesProfitability() {
        printTestHeader("CASO 2: BINANCE -> BYBIT (Fees Normales)");

        // Binance (Compra): 1.00 | Fee 0.1%
        // Bybit (Venta): 1.005 (+0.5% Spread) | Fee 0.1% (PAGAMOS AQUÍ TAMBIÉN)
        double priceBinance = 1.000;
        double priceBybit = 1.005;

        mockOrderBook("BINANCE", "BTCUSDT", priceBinance);
        mockOrderBook("BYBIT", "BTCUSDT", priceBybit);

        when(feeManager.getTradingFee("BINANCE", "BTCUSDT", "TAKER")).thenReturn(0.001);
        when(feeManager.getTradingFee("BYBIT", "BTCUSDT", "TAKER")).thenReturn(0.001); // 0.1% Fee

        ArbitrageOpportunity opp = createOpp("SPATIAL", "BTC", "BINANCE", "BYBIT", priceBinance, priceBybit);
        ArbitrageOpportunity result = estimator.estimateProfitability(opp, balances, dataProvider);

        assertNotNull(result);

        // CÁLCULO MANUAL:
        // Costo Total: $100.10 (Igual que antes)
        // Venta Bruta: $100.50
        // Neto Venta (Bybit 0.1%): $100.50 * (1 - 0.001) = $100.3995
        // Profit: $100.3995 - $100.10 = $0.2995
        // NOTA: Ganamos $0.29 vs $0.40 en MEXC. ¡Esa es la diferencia!

        assertEquals(0.2995, result.expectedProfit(), 0.0001);

        imprimirDesgloseForense(100.0, priceBinance, priceBybit, 0.001, 0.001, result.expectedProfit());
    }

    // =========================================================================
    // 🔴 CASO 3: EL FILTRO DEL CFO
    // =========================================================================
    @Test
    @DisplayName("CFO: Debe rechazar si no hay saldo virtual disponible")
    void testCfoRejection() {
        printTestHeader("CASO 3: INTENTO CON SALDO BLOQUEADO");

        mockOrderBook("BINANCE", "BTCUSDT", 1.00);
        mockOrderBook("MEXC", "BTCUSDT", 1.05); // ¡5% de ganancia! Una mina de oro.

        // PERO... El CFO dice que el saldo está ocupado
        when(cfo.getVirtualAvailableBalance("BINANCE", "USDT")).thenReturn(0.0); // 0 disponibles

        ArbitrageOpportunity opp = createOpp("SPATIAL", "BTC", "BINANCE", "MEXC", 1.00, 1.05);
        ArbitrageOpportunity result = estimator.estimateProfitability(opp, balances, dataProvider);

        assertNull(result, "El CFO debió bloquear la operación");
        System.out.println("❌ RESULTADO: Operación rechazada correctamente por el CFO (Saldo Virtual Insuficiente).");
    }

    // =========================================================================
    // 🛠️ HELPERS Y VISUALIZACIÓN
    // =========================================================================

    private void mockOrderBook(String exchange, String pair, double price) {
        // Simulamos liquidez infinita para simplificar el test de fees
        List<double[]> levels = Collections.singletonList(new double[]{price, 9999.0});
        OrderBook book = new OrderBook(levels, levels);
        lenient().when(dataProvider.getOrderBook(eq(exchange), eq(pair), anyInt())).thenReturn(book);
    }

    private ArbitrageOpportunity createOpp(String type, String asset, String bEx, String sEx, double entry, double exit) {
        return new ArbitrageOpportunity(type, asset, bEx, sEx, entry, exit, (exit - entry) / entry, 0, 0, System.currentTimeMillis());
    }

    private void printTestHeader(String title) {
        System.out.println("\n══════════════════════════════════════════════════════════════════════");
        System.out.println(" " + title);
        System.out.println("══════════════════════════════════════════════════════════════════════");
    }

    // 🕵️ MÉTODO FORENSE: Imprime lo que pasó paso a paso
    private void imprimirDesgloseForense(double capital, double pCompra, double pVenta, double feeB, double feeS, double profitFinal) {
        System.out.println("📊 DESGLOSE MATEMÁTICO DE LA ESTRATEGIA:");
        System.out.printf("   1. Capital Base       : $%.2f%n", capital);
        System.out.printf("   2. Precio Compra      : $%.4f%n", pCompra);

        double qty = capital / pCompra;
        System.out.printf("   3. Cantidad Comprada  : %.6f BTC%n", qty);

        double costoTotal = capital * (1 + feeB);
        System.out.printf("   4. Costo Contable     : $%.4f (Incluye fee compra %.2f%%)%n", costoTotal, feeB * 100);

        System.out.printf("   5. Precio Venta       : $%.4f%n", pVenta);

        double ventaBruta = qty * pVenta;
        System.out.printf("   6. Venta Bruta        : $%.4f%n", ventaBruta);

        double ventaNeta = ventaBruta * (1 - feeS);
        System.out.printf("   7. Venta Neta         : $%.4f (Tras fee venta %.2f%%)%n", ventaNeta, feeS * 100);

        System.out.println("   ──────────────────────────────────────────");
        System.out.printf("   🏁 PROFIT FINAL       : $%.4f %s%n", profitFinal, (profitFinal > 0 ? "✅" : "❌"));

        if (feeS == 0.0) {
            System.out.println("   💎 NOTA: ¡Se aplicó beneficio Zero-Fee en Venta!");
        }
    }
}