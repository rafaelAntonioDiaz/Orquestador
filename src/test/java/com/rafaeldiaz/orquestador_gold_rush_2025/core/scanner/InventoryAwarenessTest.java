package com.rafaeldiaz.orquestador_gold_rush_2025.core.scanner;

import com.rafaeldiaz.orquestador_gold_rush_2025.connect.ExchangeConnector;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.PortfolioHealthManager;
import com.rafaeldiaz.orquestador_gold_rush_2025.core.strategy.impl.AdaptiveSpatialStrategy;
import com.rafaeldiaz.orquestador_gold_rush_2025.model.ArbitrageOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("🛡️ Integración: Conciencia de Inventario")
class InventoryAwarenessTest {

    @Test
    @DisplayName("👻 GHOST BUSTER: Debe ignorar oportunidad si no hay saldo en el Seller")
    void shouldIgnoreOpportunity_WhenInventoryIsMissing() {
        // 1. SETUP DEL ESCENARIO
        String asset = "WIF";

        // Mock del CFO (El que sabe dónde está el dinero)
        PortfolioHealthManager mockCFO = mock(PortfolioHealthManager.class);

        // ESCENARIO CRÍTICO:
        // Tenemos WIF en MEXC y BYBIT.
        // PERO NO EN KUCOIN.
        when(mockCFO.getValidExchangesForAsset(asset))
                .thenReturn(Set.of("mexc", "bybit"));

        // 2. SETUP DE LA ESTRATEGIA
        // Umbral muy bajo (0.1%) para asegurar que el precio no sea el problema
        AdaptiveSpatialStrategy strategy = new AdaptiveSpatialStrategy(mockCFO);
        // 3. MERCADO SIMULADO (La trampa)
        // Precio en MEXC (Buy): $2.00
        // Precio en KUCOIN (Sell): $2.50  <-- ¡GRAN OPORTUNIDAD! (Pero no tenemos WIF en Kucoin)
        Map<String, Map<String, Double>> market = new HashMap<>();

        market.put("mexc", Map.of("WIFUSDT", 2.00));
        market.put("kucoin", Map.of("WIFUSDT", 2.50)); // Exchange vacío
        market.put("bybit", Map.of("WIFUSDT", 2.05));  // Exchange con saldo

        // 4. EJECUCIÓN
        List<ArbitrageOpportunity> results = strategy.findOpportunities(asset, market);

        // 5. VERIFICACIÓN (La hora de la verdad)
        System.out.println("🔍 Resultados encontrados: " + results.size());

        // A) No debe haber encontrado la oportunidad MEXC -> KUCOIN
        boolean hasKucoinSale = results.stream()
                .anyMatch(op -> op.sellExchange().equals("kucoin"));

        assertFalse(hasKucoinSale, "❌ ERROR: Intentó vender en Kucoin sin tener saldo!");

        // B) (Opcional) Podría haber encontrado MEXC -> BYBIT si el spread diera
        // Spread MEXC->BYBIT: (2.05 - 2.00)/2.00 = 0.025 (2.5%) > 0.1%.
        // Esta SÍ debería aparecer porque Bybit tiene saldo.
        boolean hasBybitSale = results.stream()
                .anyMatch(op -> op.sellExchange().equals("bybit"));

        assertTrue(hasBybitSale, "✅ CORRECTO: Encontró la ruta válida (Mexc -> Bybit)");
    }
}