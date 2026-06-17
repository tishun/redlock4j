/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j;

import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.strategy.LockWaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A distributed semaphore implementation that limits the number of concurrent accesses to a shared resource. Unlike a
 * lock which allows only one holder, a semaphore allows a configurable number of permits.
 *
 * <p>
 * <b>Key Features:</b>
 * </p>
 * <ul>
 * <li>Configurable number of permits</li>
 * <li>Multiple threads can acquire permits simultaneously</li>
 * <li>Blocks when no permits are available</li>
 * <li>Automatic permit release on timeout</li>
 * </ul>
 *
 * <p>
 * <b>Use Cases:</b>
 * </p>
 * <ul>
 * <li>Rate limiting: Limit concurrent API calls</li>
 * <li>Resource pooling: Limit concurrent database connections</li>
 * <li>Throttling: Control concurrent access to expensive operations</li>
 * </ul>
 *
 * <p>
 * <b>Example Usage:</b>
 * </p>
 *
 * <pre>
 * {
 *     &#64;code
 *     // Create a semaphore with 5 permits
 *     RedlockSemaphore semaphore = new RedlockSemaphore("api-limiter", 5, redisDrivers, config);
 *
 *     // Acquire a permit
 *     if (semaphore.tryAcquire(Duration.ofSeconds(5))) {
 *         try {
 *             // Perform rate-limited operation
 *             callExternalAPI();
 *         } finally {
 *             semaphore.release();
 *         }
 *     }
 * }
 * </pre>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class RedlockSemaphore extends AbstractRedlock {
    private static final Logger logger = LoggerFactory.getLogger(RedlockSemaphore.class);

    private final String semaphoreKey;
    private final int maxPermits;

    // Thread-local storage for permit state
    private final ThreadLocal<PermitState> permitState = new ThreadLocal<>();

    private static class PermitState {
        final List<String> permitIds; // IDs of acquired permits
        final Instant acquisitionTime;
        final Duration validityDuration;

        PermitState(List<String> permitIds, Instant acquisitionTime, Duration validityDuration) {
            this.permitIds = new ArrayList<>(permitIds);
            this.acquisitionTime = acquisitionTime;
            this.validityDuration = validityDuration;
        }

        boolean isValid() {
            return Instant.now().isBefore(getExpiryTime());
        }

        Instant getExpiryTime() {
            return acquisitionTime.plus(validityDuration);
        }
    }

    /**
     * Creates a new distributed semaphore.
     *
     * @param semaphoreKey
     *            the key for this semaphore
     * @param maxPermits
     *            the maximum number of permits available
     * @param redisDrivers
     *            the Redis drivers to use
     * @param config
     *            the Redlock configuration
     */
    public RedlockSemaphore(String semaphoreKey, int maxPermits, List<RedisDriver> redisDrivers,
            RedlockConfiguration config, LockWaitStrategy waitStrategy) {
        super(redisDrivers, config, waitStrategy);
        if (maxPermits <= 0) {
            throw new IllegalArgumentException("Max permits must be positive");
        }

        this.semaphoreKey = semaphoreKey;
        this.maxPermits = maxPermits;

        logger.debug("Created RedlockSemaphore {} with {} permits", semaphoreKey, maxPermits);
    }

    /**
     * Acquires a permit, blocking until one is available.
     *
     * @throws RedlockException
     *             if unable to acquire within the configured timeout
     */
    public void acquire() throws InterruptedException {
        if (!tryAcquire(config.getLockAcquisitionTimeout())) {
            throw new RedlockException("Failed to acquire semaphore permit within timeout: " + semaphoreKey);
        }
    }

    /**
     * Acquires the specified number of permits, blocking until they are available.
     *
     * @param permits
     *            the number of permits to acquire
     * @throws RedlockException
     *             if unable to acquire within the configured timeout
     */
    public void acquire(int permits) throws InterruptedException {
        if (!tryAcquire(permits, config.getLockAcquisitionTimeout())) {
            throw new RedlockException(
                    "Failed to acquire " + permits + " semaphore permits within timeout: " + semaphoreKey);
        }
    }

    /**
     * Acquires a permit if one is immediately available.
     *
     * @return true if a permit was acquired, false otherwise
     */
    public boolean tryAcquire() {
        try {
            return tryAcquire(1, Duration.ZERO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Acquires a permit, waiting up to the specified time if necessary.
     *
     * @param timeout
     *            the maximum time to wait
     * @return true if a permit was acquired, false if the timeout elapsed
     */
    public boolean tryAcquire(Duration timeout) throws InterruptedException {
        return tryAcquire(1, timeout);
    }

    /**
     * Acquires the specified number of permits, waiting up to the specified time if necessary.
     *
     * @param permits
     *            the number of permits to acquire
     * @param timeout
     *            the maximum time to wait
     * @return true if the permits were acquired, false if the timeout elapsed
     */
    public boolean tryAcquire(int permits, Duration timeout) throws InterruptedException {
        if (permits <= 0 || permits > maxPermits) {
            throw new IllegalArgumentException("Invalid number of permits: " + permits);
        }

        // Check if current thread already has permits
        PermitState currentState = permitState.get();
        if (currentState != null && currentState.isValid()) {
            logger.warn("Thread already holds permits, release before acquiring more");
            return false;
        }

        Instant deadline = Instant.now().plus(timeout);

        for (int attempt = 0; attempt <= config.getMaxRetryAttempts(); attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            SemaphoreResult result = attemptAcquire(permits);
            if (result.isAcquired()) {
                permitState.set(new PermitState(result.getPermitIds(), Instant.now(),
                        Duration.ofMillis(result.getValidityTimeMs())));
                logger.debug("Successfully acquired {} permit(s) for {} on attempt {}", permits, semaphoreKey,
                        attempt + 1);
                return true;
            }

            // Check if we've exceeded the timeout
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (!timeout.isZero() && remaining.isNegative()) {
                logger.debug("Semaphore acquisition timeout exceeded for {}", semaphoreKey);
                break;
            }

            // Wait before retrying
            if (attempt < config.getMaxRetryAttempts()) {
                waitForLockRelease(semaphoreKey, remaining.toMillis(), attempt);
            }
        }

        return false;
    }

    /**
     * Releases a permit, returning it to the semaphore.
     */
    public void release() {
        release(1);
    }

    /**
     * Releases the specified number of permits.
     * 
     * @param permits
     *            the number of permits to release
     */
    public void release(int permits) {
        PermitState state = permitState.get();
        if (state == null) {
            logger.warn("Attempting to release semaphore permits but no permit state found");
            return;
        }

        if (permits > state.permitIds.size()) {
            logger.warn("Attempting to release more permits than held");
            permits = state.permitIds.size();
        }

        // Release the specified number of permits
        List<String> toRelease = state.permitIds.subList(0, permits);
        releasePermits(toRelease);

        // Update or clear state
        if (permits >= state.permitIds.size()) {
            permitState.remove();
        } else {
            state.permitIds.subList(0, permits).clear();
        }

        logger.debug("Successfully released {} permit(s) for {}", permits, semaphoreKey);
    }

    /**
     * Returns the number of permits currently available (approximate). Note: This is an estimate and may not be
     * accurate in a distributed environment.
     */
    public int availablePermits() {
        // Note: This would require counting active permits across all nodes
        // For now, return a placeholder
        return maxPermits;
    }

    /**
     * Attempts to acquire the specified number of permits.
     */
    private SemaphoreResult attemptAcquire(int permits) {
        List<String> permitIds = new ArrayList<>();
        Instant startTime = Instant.now();

        // Try to acquire permits by creating unique keys
        for (int i = 0; i < permits; i++) {
            String permitId = generateLockValue();
            String permitKey = semaphoreKey + ":permit:" + permitId;

            // Use execution strategy to acquire permit on nodes
            LockResult result = executionStrategy.acquireLock(permitKey, permitId,
                    config.getDefaultLockTimeout().toMillis());

            if (result.isAcquired()) {
                permitIds.add(permitId);
            } else {
                // Failed to acquire this permit, rollback
                releasePermits(permitIds);
                return new SemaphoreResult(false, 0, new ArrayList<>());
            }
        }

        Duration elapsed = Duration.between(startTime, Instant.now());
        // Use strategy to calculate validity time (handles single-node vs multi-node drift)
        long validityTime = executionStrategy.calculateValidityTime(config.getDefaultLockTimeout().toMillis(),
                elapsed.toMillis());

        boolean acquired = permitIds.size() == permits && validityTime > 0;

        if (!acquired) {
            releasePermits(permitIds);
            return new SemaphoreResult(false, 0, new ArrayList<>());
        }

        return new SemaphoreResult(true, validityTime, permitIds);
    }

    /**
     * Releases the specified permits.
     */
    private void releasePermits(List<String> permitIds) {
        for (String permitId : permitIds) {
            String permitKey = semaphoreKey + ":permit:" + permitId;
            // Use execution strategy to release permit on all appropriate nodes
            executionStrategy.releaseLock(permitKey, permitId);
        }
    }

    /**
     * Result of a semaphore acquisition attempt.
     */
    private static class SemaphoreResult {
        private final boolean acquired;
        private final long validityTimeMs;
        private final List<String> permitIds;

        SemaphoreResult(boolean acquired, long validityTimeMs, List<String> permitIds) {
            this.acquired = acquired;
            this.validityTimeMs = validityTimeMs;
            this.permitIds = permitIds;
        }

        boolean isAcquired() {
            return acquired;
        }

        long getValidityTimeMs() {
            return validityTimeMs;
        }

        List<String> getPermitIds() {
            return permitIds;
        }
    }
}
