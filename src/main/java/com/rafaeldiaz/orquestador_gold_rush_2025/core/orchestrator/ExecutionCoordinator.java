package com.rafaeldiaz.orquestador_gold_rush_2025.core.orchestrator;

import com.rafaeldiaz.orquestador_gold_rush_2025.utils.BotLogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 🚦 ÁRBITRO DE EJECUCIÓN (v4.0 - Fine-Grained Locking / Java 25 Ready)
 * Gestiona locks por cuenta específica para permitir paralelismo real (I/O no bloqueante entre cuentas).
 * Integra Circuit Breaker y prevención de Deadlocks.
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
    // Mapa de Mutex: Cada cuenta tiene su propio semáforo.
    private final ConcurrentHashMap<String, ReentrantLock> stripes = new ConcurrentHashMap<>();

    // --- ESTRUCTURA INTERNA LOCK (Java Record para inmutabilidad) ---
    private record LockLease(Thread owner, long expirationTime) {}

    /**
     * Obtiene el candado físico específico para una cuenta.
     */
    private ReentrantLock getStripe(String key) {
        return stripes.computeIfAbsent(key, k -> new ReentrantLock());
    }

    /**
     * Intenta adquirir acceso para UN solo exchange.
     * NO bloquea a otros exchanges.
     */
    public boolean tryAcquireLock(String accountName) {
        ReentrantLock lock = getStripe(accountName);
        lock.lock(); // 🔒 Bloqueo Físico (Solo este hilo toca esta cuenta)
        try {
            long now = System.currentTimeMillis();

            // 1. 🏥 CHEQUEO DE SALUD
            if (isInQuarantine(accountName, now)) {
                return false;
            }

            // 2. 🔐 LÓGICA DE LOCK DE NEGOCIO
            LockLease currentLease = activeLocks.get(accountName);

            // Caso A: Libre
            if (currentLease == null) {
                grantLock(accountName, now);
                return true;
            }

            // Caso B: Ocupado pero expirado (Zombie)
            if (now > currentLease.expirationTime()) {
                BotLogger.warn("🧟 ZOMBIE LOCK en " + accountName + ". Rompiendo candado.");
                grantLock(accountName, now);
                return true;
            }

            // Caso C: Ocupado y vigente
            return false;

        } finally {
            lock.unlock(); // 🔓 Liberar Bloqueo Físico
        }
    }

    /**
     * Intenta adquirir acceso para DOS exchanges (Arbitraje).
     * ⚠️ SAFETY CHECK: Ordena locks para evitar Deadlocks.
     */
    public boolean tryAcquireDualLock(String accountA, String accountB) {
        // 1. Ordenamos claves lexicográficamente.
        // Esto garantiza que si Hilo 1 quiere (A, B) y Hilo 2 quiere (B, A),
        // AMBOS intentarán bloquear A primero. Uno gana, el otro espera.
        // Sin esto, tendríamos Deadlock.
        String firstKey = accountA.compareTo(accountB) < 0 ? accountA : accountB;
        String secondKey = accountA.compareTo(accountB) < 0 ? accountB : accountA;

        ReentrantLock lock1 = getStripe(firstKey);
        ReentrantLock lock2 = getStripe(secondKey);

        lock1.lock(); // 🔒 Adquirir primero
        try {
            lock2.lock(); // 🔒 Adquirir segundo
            try {
                long now = System.currentTimeMillis();

                // Validaciones bajo doble llave
                if (isInQuarantine(accountA, now) || isInQuarantine(accountB, now)) return false;
                if (isLockedBusiness(accountA, now) || isLockedBusiness(accountB, now)) return false;

                // Éxito
                grantLock(accountA, now);
                grantLock(accountB, now);
                return true;

            } finally {
                lock2.unlock(); // 🔓 Liberar segundo
            }
        } finally {
            lock1.unlock(); // 🔓 Liberar primero
        }
    }

    /**
     * Libera el lock de negocio.
     */
    public void releaseLock(String accountName) {
        ReentrantLock lock = getStripe(accountName);
        lock.lock();
        try {
            LockLease currentLease = activeLocks.get(accountName);
            // Solo el dueño puede liberar
            if (currentLease != null && currentLease.owner() == Thread.currentThread()) {
                activeLocks.remove(accountName);
            }
        } finally {
            lock.unlock();
        }
    }

    // =========================================================================
    // 🏥 GESTIÓN DE INCIDENTES (Thread-Safe por diseño de ConcurrentMap)
    // =========================================================================

    public void reportFailure(String accountName) {
        // Atomicidad garantizada por compute
        failureCounts.compute(accountName, (key, val) -> {
            AtomicInteger counter = (val == null) ? new AtomicInteger(0) : val;
            int failures = counter.incrementAndGet();

            BotLogger.warn("⚠️ Fallo operativo en " + key + ". Strike " + failures + "/" + BotConfig.CB_MAX_CONSECUTIVE_FAILURES);

            if (failures >= BotConfig.CB_MAX_CONSECUTIVE_FAILURES) {
                long releaseTime = System.currentTimeMillis() + BotConfig.CB_QUARANTINE_DURATION_MS;
                quarantineUntil.put(key, releaseTime);
                BotLogger.error("🚨 CIRCUIT BREAKER ACTIVADO: " + key + " en cuarentena.");
            }
            return counter;
        });
    }

    public void reportSuccess(String accountName) {
        if (failureCounts.containsKey(accountName)) {
            failureCounts.get(accountName).set(0);
        }
    }

    // =========================================================================
    // 🕵️ HELPERS & UTILS
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

    // Helper interno: asume que ya tenemos el lock físico, solo verifica lógica
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