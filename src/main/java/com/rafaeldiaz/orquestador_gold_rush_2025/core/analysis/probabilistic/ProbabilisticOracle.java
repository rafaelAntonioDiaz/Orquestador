package com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.probabilistic;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator.BotConfig;
import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;

/**
 * 🔮 PROBABILISTIC ORACLE
 * Interpreta la memoria y emite veredictos de confianza.
 */
public class ProbabilisticOracle {

    private final MarketCortex cortex;
    private final String LEADER_EXCHANGE;

    public ProbabilisticOracle(MarketCortex cortex) {
        this.cortex = cortex;
        // Definición dinámica del líder desde .env
        // USO DE GETTER (Testable)
        String envAdvisor = BotConfig.getAdvisorRefExchange();
        this.LEADER_EXCHANGE = (envAdvisor != null && !envAdvisor.isBlank())
                ? envAdvisor.toLowerCase()
                : "binance";
        BotLogger.info("🔮 ORACLE: Advisor configurado -> " + LEADER_EXCHANGE.toUpperCase());
    }

    public Verdict getVerdict(String asset, double currentSpread, String targetExchange) {
        // USO DE GETTER (Testable)
        int ticks = BotConfig.getOracleLeadLagTicks();
        // 1. ANÁLISIS LEAD-LAG
        double leaderVel = cortex.getPriceVelocity(asset, LEADER_EXCHANGE, ticks);
        double followerVel = cortex.getPriceVelocity(asset, targetExchange, ticks);

        // Señal: Líder se mueve rápido (>0.15%) y Seguidor está quieto (<0.05%)
        // Nota: Umbrales de velocidad hardcodeados como "constantes físicas" por ahora,
        // pero la ventana de tiempo viene de Config.
        boolean leadLagSignal = Math.abs(leaderVel) > 0.0015 && Math.abs(followerVel) < 0.0005;

        double confidence = leadLagSignal ? 0.85 : 0.0;
        String source = leadLagSignal ? "LEAD_LAG" : "NONE";

        // 2. ANÁLISIS MEAN REVERSION (Registro + Cálculo)
        cortex.recordSpread(asset, currentSpread);
        double zScore = cortex.getSpreadZScore(asset, currentSpread);

        // USO DE GETTER (Testable)
        if (zScore > BotConfig.getOracleZScoreThreshold()) {
            confidence = Math.max(confidence, 0.75);
            source = source.equals("NONE") ? "MEAN_REV" : source + "+MEAN_REV";
        }

        // 3. DECISIÓN DE UMBRAL (Dynamic Thresholding)
        // USO DE GETTER (Testable)
        double suggestedThreshold = BotConfig.getMinScanSpread();
        if (confidence >= BotConfig.getOracleMinConfidence()) {
            // Si hay alta confianza, permitimos spread agresivo (ej. 0.10%)
            suggestedThreshold = BotConfig.getOracleAggressiveSpread();
        }

        return new Verdict(source, confidence, suggestedThreshold);
    }
}