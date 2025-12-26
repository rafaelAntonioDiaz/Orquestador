package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🚦 ÁRBITRO DE EJECUCIÓN (v3.1 - Configurable vía DotEnv)
 * Gestiona locks y SALUD OPERATIVA por exchange.
 * Parametrización externa para ajustes en caliente sin recompilación.
 */
public class ExecutionCoordinator {

    // NOTA: Las constantes hardcoded se han movido a BotConfig

    // Estado de Locks (Concurrencia)
    private final Map<String, LockLease> activeLocks = new ConcurrentHashMap<>();

    // Estado de Salud (Circuit Breaker)
    private final Map<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> quarantineUntil = new ConcurrentHashMap<>();

    // Validación de Snapshots
    private final Map<String, Long> lastAccountUpdate = new ConcurrentHashMap<>();

    // --- ESTRUCTURA INTERNA LOCK ---
    private static class LockLease {
        final Thread owner;
        final long expirationTime;
        LockLease(Thread owner, long expirationTime) { this.owner = owner; this.expirationTime = expirationTime; }
    }

    /**
     * Intenta adquirir acceso. REVISA SI EL EXCHANGE ESTÁ EN CUARENTENA.
     */
    public synchronized boolean tryAcquireLock(String accountName) {
        long now = System.currentTimeMillis();

        // 1. 🏥 CHEQUEO DE SALUD (Circuit Breaker)
        if (isInQuarantine(accountName, now)) {
            return false; // Bloqueado por fallos previos
        }

        // 2. 🔐 LÓGICA DE LOCK
        LockLease currentLease = activeLocks.get(accountName);
        if (currentLease == null) {
            grantLock(accountName, now);
            return true;
        }

        // ZOMBIE CHECK
        if (now > currentLease.expirationTime) {
            BotLogger.error("🧟 ZOMBIE LOCK en " + accountName + ". Rompiendo candado.");
            grantLock(accountName, now);
            return true;
        }
        return false;
    }

    public synchronized boolean tryAcquireDualLock(String accountA, String accountB) {
        long now = System.currentTimeMillis();

        // Chequeo de Salud Dual
        if (isInQuarantine(accountA, now) || isInQuarantine(accountB, now)) return false;

        // Chequeo de Locks
        if (isLocked(accountA, now) || isLocked(accountB, now)) return false;

        grantLock(accountA, now);
        grantLock(accountB, now);
        return true;
    }

    public synchronized void releaseLock(String accountName) {
        LockLease currentLease = activeLocks.get(accountName);
        if (currentLease != null && currentLease.owner == Thread.currentThread()) {
            activeLocks.remove(accountName);
        }
    }

    // =========================================================================
    // 🏥 GESTIÓN DE INCIDENTES (REPORTING)
    // =========================================================================

    /**
     * Reporta que una operación en este exchange FALLÓ.
     */
    public void reportFailure(String accountName) {
        AtomicInteger counter = failureCounts.computeIfAbsent(accountName, k -> new AtomicInteger(0));
        int failures = counter.incrementAndGet();

        // ✅ AHORA USAMOS LA CONFIGURACIÓN DINÁMICA
        BotLogger.warn("⚠️ Fallo operativo en " + accountName + ". Strike " + failures + "/" + BotConfig.CB_MAX_CONSECUTIVE_FAILURES);

        if (failures >= BotConfig.CB_MAX_CONSECUTIVE_FAILURES) {
            // ✅ DURACIÓN DINÁMICA DE LA CUARENTENA
            long releaseTime = System.currentTimeMillis() + BotConfig.CB_QUARANTINE_DURATION_MS;
            quarantineUntil.put(accountName, releaseTime);
            BotLogger.error("🚨 CIRCUIT BREAKER ACTIVADO: " + accountName + " en cuarentena temporal.");
        }
    }

    /**
     * Reporta que una operación fue EXITOSA. Resetea los contadores.
     */
    public void reportSuccess(String accountName) {
        if (failureCounts.containsKey(accountName) && failureCounts.get(accountName).get() > 0) {
            failureCounts.get(accountName).set(0);
        }
    }

    // =========================================================================
    // 🕵️ HELPERS
    // =========================================================================

    private boolean isInQuarantine(String account, long now) {
        Long until = quarantineUntil.get(account);
        if (until == null) return false;

        if (now > until) {
            // La cuarentena expiró
            quarantineUntil.remove(account);
            failureCounts.get(account).set(0);
            BotLogger.info("🟢 LEVANTANDO CUARENTENA de " + account + ". Bienvenido de vuelta.");
            return false;
        }
        return true; // Sigue castigado
    }

    public void markAsDirty(String accountName) {
        lastAccountUpdate.put(accountName, System.currentTimeMillis());
    }

    public boolean isSnapshotStale(String accountName, long snapshotTime) {
        long lastUpdate = lastAccountUpdate.getOrDefault(accountName, 0L);
        return lastUpdate > snapshotTime;
    }

    private void grantLock(String account, long now) {
        // ✅ TIMEOUT DINÁMICO
        activeLocks.put(account, new LockLease(Thread.currentThread(), now + BotConfig.EXECUTION_LOCK_TIMEOUT_MS));
    }

    private boolean isLocked(String account, long now) {
        LockLease lease = activeLocks.get(account);
        if (lease == null) return false;
        return now <= lease.expirationTime;
    }
}