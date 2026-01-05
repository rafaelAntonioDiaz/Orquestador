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
        String envAdvisor = BotConfig.ADVISOR_REF_EXCHANGE;
        this.LEADER_EXCHANGE = (envAdvisor != null && !envAdvisor.isBlank())
                ? envAdvisor.toLowerCase()
                : "binance";
        BotLogger.info("🔮 ORACLE: Advisor configurado -> " + LEADER_EXCHANGE.toUpperCase());
    }

    public OracleVerdict getVerdict(String asset, double currentSpread, String targetExchange) {
        // 1. ANÁLISIS LEAD-LAG
        double leaderVel = cortex.getPriceVelocity(asset, LEADER_EXCHANGE, BotConfig.ORACLE_LEAD_LAG_TICKS);
        double followerVel = cortex.getPriceVelocity(asset, targetExchange, BotConfig.ORACLE_LEAD_LAG_TICKS);

        // Señal: Líder se mueve rápido (>0.15%) y Seguidor está quieto (<0.05%)
        // Nota: Umbrales de velocidad hardcodeados como "constantes físicas" por ahora,
        // pero la ventana de tiempo viene de Config.
        boolean leadLagSignal = Math.abs(leaderVel) > 0.0015 && Math.abs(followerVel) < 0.0005;

        double confidence = leadLagSignal ? 0.85 : 0.0;
        String source = leadLagSignal ? "LEAD_LAG" : "NONE";

        // 2. ANÁLISIS MEAN REVERSION (Registro + Cálculo)
        cortex.recordSpread(asset, currentSpread);
        double zScore = cortex.getSpreadZScore(asset, currentSpread);

        if (zScore > BotConfig.ORACLE_Z_SCORE_THRESHOLD) {
            confidence = Math.max(confidence, 0.75);
            source = source.equals("NONE") ? "MEAN_REV" : source + "+MEAN_REV";
        }

        // 3. DECISIÓN DE UMBRAL (Dynamic Thresholding)
        double suggestedThreshold = BotConfig.MIN_SCAN_SPREAD; // Default (0.30% o lo que sea)

        if (confidence >= BotConfig.ORACLE_MIN_CONFIDENCE) {
            // Si hay alta confianza, permitimos spread agresivo (ej. 0.10%)
            suggestedThreshold = BotConfig.ORACLE_AGGRESSIVE_SPREAD;
        }

        return new OracleVerdict(confidence, suggestedThreshold, source);
    }

    public record OracleVerdict(
            double confidenceScore,
            double suggestedThreshold,
            String signalSource
    ) {}
}