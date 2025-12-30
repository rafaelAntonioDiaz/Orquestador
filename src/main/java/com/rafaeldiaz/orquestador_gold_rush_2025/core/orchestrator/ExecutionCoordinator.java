package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 🚦 ÁRBITRO DE EJECUCIÓN (v4.1 - Global Lockdown Ready)
 * Gestiona locks por cuenta específica para permitir paralelismo real.
 * Integra Circuit Breaker, prevención de Deadlocks y Kill Switch Global.
 */
public class ExecutionCoordinator {

    // Estado de Locks de Negocio (Quién tiene el turno LÓGICO de operar)
    private final Map<String, LockLease> activeLocks = new ConcurrentHashMap<>();

    // Estado de Salud (Circuit Breaker)
    private final Map<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> quarantineUntil = new ConcurrentHashMap<>();

    // Validación de Snapshots
    private final Map<String, Long> lastAccountUpdate = new ConcurrentHashMap<>();

    // 🔐 GESTIÓN DE CONCURRENCIA FÍSICA
    private final ConcurrentHashMap<String, ReentrantLock> stripes = new ConcurrentHashMap<>();

    // 🚨 ESTADO DE EMERGENCIA (Kill Switch)
    private volatile boolean globalEmergencyState = false;

    // --- ESTRUCTURA INTERNA LOCK ---
    private record LockLease(Thread owner, long expirationTime) {}

    private ReentrantLock getStripe(String key) {
        return stripes.computeIfAbsent(key, k -> new ReentrantLock());
    }

    /**
     * Intenta adquirir acceso para UN solo exchange.
     */
    public boolean tryAcquireLock(String accountName) {
        ReentrantLock lock = getStripe(accountName);
        lock.lock();
        try {
            // 🚨 0. CHEQUEO DE EMERGENCIA GLOBAL
            if (globalEmergencyState) return false;

            long now = System.currentTimeMillis();

            // 1. 🏥 CHEQUEO DE SALUD
            if (isInQuarantine(accountName, now)) {
                return false;
            }

            // 2. 🔐 LÓGICA DE LOCK DE NEGOCIO
            LockLease currentLease = activeLocks.get(accountName);

            if (currentLease == null) {
                grantLock(accountName, now);
                return true;
            }

            if (now > currentLease.expirationTime()) {
                BotLogger.warn("🧟 ZOMBIE LOCK en " + accountName + ". Rompiendo candado.");
                grantLock(accountName, now);
                return true;
            }

            return false;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Intenta adquirir acceso para DOS exchanges (Arbitraje).
     */
    public boolean tryAcquireDualLock(String accountA, String accountB) {
        String firstKey = accountA.compareTo(accountB) < 0 ? accountA : accountB;
        String secondKey = accountA.compareTo(accountB) < 0 ? accountB : accountA;

        ReentrantLock lock1 = getStripe(firstKey);
        ReentrantLock lock2 = getStripe(secondKey);

        lock1.lock();
        try {
            lock2.lock();
            try {
                // 🚨 0. CHEQUEO DE EMERGENCIA GLOBAL
                if (globalEmergencyState) return false;

                long now = System.currentTimeMillis();

                if (isInQuarantine(accountA, now) || isInQuarantine(accountB, now)) return false;
                if (isLockedBusiness(accountA, now) || isLockedBusiness(accountB, now)) return false;

                grantLock(accountA, now);
                grantLock(accountB, now);
                return true;

            } finally {
                lock2.unlock();
            }
        } finally {
            lock1.unlock();
        }
    }

    public void releaseLock(String accountName) {
        ReentrantLock lock = getStripe(accountName);
        lock.lock();
        try {
            LockLease currentLease = activeLocks.get(accountName);
            if (currentLease != null && currentLease.owner() == Thread.currentThread()) {
                activeLocks.remove(accountName);
            }
        } finally {
            lock.unlock();
        }
    }

    // =========================================================================
    // 🛑 GESTIÓN DE EMERGENCIA GLOBAL (Implementación solicitada)
    // =========================================================================

    public void forceGlobalLockdown(String reason) {
        this.globalEmergencyState = true;
        BotLogger.error("🔥🔥🔥 GLOBAL LOCKDOWN ACTIVADO: " + reason + " 🔥🔥🔥");
        BotLogger.error("⛔ Todas las operaciones han sido suspendidas.");
    }

    public void liftGlobalLockdown() {
        this.globalEmergencyState = false;
        BotLogger.warn("⚠️ ALERTA: Lockdown Global levantado manualmente.");
    }

    // =========================================================================
    // 🏥 GESTIÓN DE INCIDENTES
    // =========================================================================

    public void reportFailure(String accountName) {
        failureCounts.compute(accountName, (key, val) -> {
            AtomicInteger counter = (val == null) ? new AtomicInteger(0) : val;
            int failures = counter.incrementAndGet();

            BotLogger.warn("⚠️ Fallo operativo en " + key + ". Strike " + failures + "/" + BotConfig.CB_MAX_CONSECUTIVE_FAILURES);

            if (failures >= BotConfig.CB_MAX_CONSECUTIVE_FAILURES) {
                long releaseTime = System.currentTimeMillis() + BotConfig.CB_QUARANTINE_DURATION_MS;
                quarantineUntil.put(key, releaseTime);
                BotLogger.error("🚨 CIRCUIT BREAKER: " + key + " bloqueado por " +
                        (BotConfig.CB_QUARANTINE_DURATION_MS / 1000) + "s tras fallos consecutivos.");            }
            return counter;
        });
    }

    public void reportSuccess(String accountName) {
        if (failureCounts.containsKey(accountName)) {
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
            quarantineUntil.remove(account);
            reportSuccess(account);
            BotLogger.info("🟢 LEVANTANDO CUARENTENA de " + account);
            return false;
        }
        return true;
    }

    private void grantLock(String account, long now) {
        activeLocks.put(account, new LockLease(Thread.currentThread(), now + BotConfig.EXECUTION_LOCK_TIMEOUT_MS));
    }

    private boolean isLockedBusiness(String account, long now) {
        LockLease lease = activeLocks.get(account);
        if (lease == null) return false;
        return now <= lease.expirationTime();
    }

    public void markAsDirty(String accountName) {
        lastAccountUpdate.put(accountName, System.currentTimeMillis());
    }

    public boolean isSnapshotStale(String accountName, long snapshotTime) {
        long lastUpdate = lastAccountUpdate.getOrDefault(accountName, 0L);
        return lastUpdate > snapshotTime;
    }
}