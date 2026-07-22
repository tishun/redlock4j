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
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * A distributed multi-lock implementation that allows atomic acquisition of multiple resources. This prevents deadlocks
 * by always acquiring locks in a consistent order (lexicographically sorted by key).
 *
 * <p>
 * The MultiLock is useful when you need to perform operations that span multiple resources and require exclusive access
 * to all of them simultaneously.
 * </p>
 *
 * <p>
 * <b>Key Features:</b>
 * </p>
 * <ul>
 * <li>Atomic acquisition of multiple locks</li>
 * <li>Deadlock prevention through consistent ordering</li>
 * <li>All-or-nothing semantics: either all locks are acquired or none</li>
 * <li>Automatic cleanup on failure</li>
 * </ul>
 *
 * <p>
 * <b>Example Usage:</b>
 * </p>
 *
 * <pre>
 * {
 *     &#64;code
 *     MultiLock multiLock = new MultiLock(Arrays.asList("account:1", "account:2", "account:3"), redisDrivers, config);
 *
 *     multiLock.lock();
 *     try {
 *         // All three accounts are now locked
 *         transferBetweenAccounts();
 *     } finally {
 *         multiLock.unlock();
 *     }
 * }
 * </pre>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class MultiLock extends AbstractRedlock implements Lock {
    private static final Logger logger = LoggerFactory.getLogger(MultiLock.class);

    private final List<String> lockKeys;

    // Thread-local storage for lock state
    private final ThreadLocal<MultiLockState> lockState = new ThreadLocal<>();

    private static class MultiLockState {
        final Map<String, String> lockValues; // key -> lockValue
        final Instant acquisitionTime;
        final Duration validityDuration;
        int holdCount;

        MultiLockState(Map<String, String> lockValues, Instant acquisitionTime, Duration validityDuration) {
            this.lockValues = new HashMap<>(lockValues);
            this.acquisitionTime = acquisitionTime;
            this.validityDuration = validityDuration;
            this.holdCount = 1;
        }

        boolean isValid() {
            return Instant.now().isBefore(getExpiryTime());
        }

        Instant getExpiryTime() {
            return acquisitionTime.plus(validityDuration);
        }

        void incrementHoldCount() {
            holdCount++;
        }

        int decrementHoldCount() {
            return --holdCount;
        }
    }

    /**
     * Creates a new MultiLock for the specified resources.
     *
     * @param lockKeys
     *            the keys to lock (will be sorted internally to prevent deadlocks)
     * @param redisDrivers
     *            the Redis drivers to use
     * @param config
     *            the Redlock configuration
     */
    public MultiLock(List<String> lockKeys, List<RedisDriver> redisDrivers, RedlockConfiguration config,
            LockWaitStrategy waitStrategy) {
        super(redisDrivers, config, waitStrategy);
        if (lockKeys == null || lockKeys.isEmpty()) {
            throw new IllegalArgumentException("Lock keys cannot be null or empty");
        }

        // Sort keys to ensure consistent ordering and prevent deadlocks
        this.lockKeys = lockKeys.stream().distinct().sorted().collect(Collectors.toList());

        logger.debug("Created MultiLock for {} resources: {}", this.lockKeys.size(), this.lockKeys);
    }

    @Override
    public void lock() {
        try {
            if (!tryLock(config.getLockAcquisitionTimeout())) {
                throw new RedlockException("Failed to acquire multi-lock within timeout for keys: " + lockKeys);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedlockException("Interrupted while acquiring multi-lock for keys: " + lockKeys, e);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (!tryLock(config.getLockAcquisitionTimeout())) {
            throw new RedlockException("Failed to acquire multi-lock within timeout for keys: " + lockKeys);
        }
    }

    @Override
    public boolean tryLock() {
        try {
            return tryLock(Duration.ZERO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean tryLock(Duration timeout) throws InterruptedException {
        // Check if current thread already holds the lock (reentrancy)
        MultiLockState currentState = lockState.get();
        if (currentState != null && currentState.isValid()) {
            currentState.incrementHoldCount();
            logger.debug("Reentrant multi-lock acquisition for {} keys (hold count: {})", lockKeys.size(),
                    currentState.holdCount);
            return true;
        }

        Instant deadline = Instant.now().plus(timeout);

        int attempt = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            // Check if we've exceeded the timeout
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (!timeout.isZero() && remaining.isNegative()) {
                logger.debug("Multi-lock acquisition timeout exceeded for keys: {} after {} attempts", lockKeys,
                        attempt);
                return false;
            }

            MultiLockResult result = attemptMultiLock();
            if (result.isAcquired()) {
                lockState.set(new MultiLockState(result.getLockValues(), Instant.now(),
                        Duration.ofMillis(result.getValidityTimeMs())));
                logger.debug("Successfully acquired multi-lock for {} keys on attempt {}", lockKeys.size(),
                        attempt + 1);
                return true;
            }

            // For zero timeout (tryLock()), only try once
            if (timeout.isZero()) {
                logger.debug("Multi-lock for keys {} not available for immediate acquisition", lockKeys);
                return false;
            }

            // Wait before retrying
            remaining = Duration.between(Instant.now(), deadline);
            if (!remaining.isNegative()) {
                // Wait on the first lock key (any release might allow us to proceed)
                waitForLockRelease(lockKeys.get(0), remaining.toMillis(), attempt);
            }
            attempt++;
        }
    }

    @Override
    public void unlock() {
        MultiLockState state = lockState.get();
        if (state == null) {
            logger.warn("Attempting to unlock multi-lock but no lock state found for current thread");
            return;
        }

        if (!state.isValid()) {
            logger.warn("Multi-lock has expired, cannot safely unlock");
            lockState.remove();
            return;
        }

        // Handle reentrancy
        int remainingHolds = state.decrementHoldCount();
        if (remainingHolds > 0) {
            logger.debug("Reentrant unlock for multi-lock (remaining holds: {})", remainingHolds);
            return;
        }

        // Final unlock - release all locks
        releaseAllLocks(state.lockValues);
        lockState.remove();
        logger.debug("Successfully released multi-lock for {} keys", lockKeys.size());
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Conditions are not supported by distributed multi-locks");
    }

    /**
     * Attempts to acquire all locks atomically.
     */
    private MultiLockResult attemptMultiLock() {
        Map<String, String> lockValues = new HashMap<>();
        Instant startTime = Instant.now();

        // Generate unique lock values for each key
        for (String key : lockKeys) {
            lockValues.put(key, generateLockValue());
        }

        // Use execution strategy to acquire all locks on nodes
        int successfulNodes = executionStrategy.executeOnNodes(driver -> acquireAllOnNode(driver, lockValues));

        Duration elapsed = Duration.between(startTime, Instant.now());
        // Use strategy to calculate validity time (handles single-node vs multi-node drift)
        long validityTime = executionStrategy.calculateValidityTime(config.getDefaultLockTimeout().toMillis(),
                elapsed.toMillis());

        // Use strategy to check if we have enough successful nodes
        boolean acquired = executionStrategy.isSuccessful(successfulNodes) && validityTime > 0;

        if (!acquired) {
            // Release any locks we managed to acquire
            releaseAllLocks(lockValues);
        }

        return new MultiLockResult(acquired, validityTime, lockValues, successfulNodes, redisDrivers.size());
    }

    /**
     * Attempts to acquire all locks on a single Redis node.
     */
    private boolean acquireAllOnNode(RedisDriver driver, Map<String, String> lockValues) {
        List<String> acquiredKeys = new ArrayList<>();

        try {
            // Try to acquire each lock in order
            for (String key : lockKeys) {
                String lockValue = lockValues.get(key);
                if (driver.setIfNotExists(key, lockValue, config.getDefaultLockTimeout().toMillis())) {
                    acquiredKeys.add(key);
                } else {
                    // Failed to acquire this lock, rollback
                    rollbackOnNode(driver, lockValues, acquiredKeys);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logger.debug("Failed to acquire multi-lock on {}: {}", driver.getIdentifier(), e.getMessage());
            rollbackOnNode(driver, lockValues, acquiredKeys);
            return false;
        }
    }

    /**
     * Rolls back locks acquired on a single node.
     */
    private void rollbackOnNode(RedisDriver driver, Map<String, String> lockValues, List<String> acquiredKeys) {
        for (String key : acquiredKeys) {
            try {
                driver.deleteIfValueMatches(key, lockValues.get(key));
            } catch (Exception e) {
                logger.warn("Failed to rollback lock {} on {}: {}", key, driver.getIdentifier(), e.getMessage());
            }
        }
    }

    /**
     * Releases all locks across all nodes.
     */
    private void releaseAllLocks(Map<String, String> lockValues) {
        // Use execution strategy to release locks on all appropriate nodes
        executionStrategy.executeOnNodes(driver -> {
            for (Map.Entry<String, String> entry : lockValues.entrySet()) {
                try {
                    driver.deleteIfValueMatches(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    logger.warn("Failed to release lock {} on {}: {}", entry.getKey(), driver.getIdentifier(),
                            e.getMessage());
                }
            }
            return true;
        });
    }

    /**
     * Result of a multi-lock acquisition attempt.
     */
    private static class MultiLockResult {
        private final boolean acquired;
        private final long validityTimeMs;
        private final Map<String, String> lockValues;
        private final int successfulNodes;
        private final int totalNodes;

        MultiLockResult(boolean acquired, long validityTimeMs, Map<String, String> lockValues, int successfulNodes,
                int totalNodes) {
            this.acquired = acquired;
            this.validityTimeMs = validityTimeMs;
            this.lockValues = lockValues;
            this.successfulNodes = successfulNodes;
            this.totalNodes = totalNodes;
        }

        boolean isAcquired() {
            return acquired;
        }

        long getValidityTimeMs() {
            return validityTimeMs;
        }

        Map<String, String> getLockValues() {
            return lockValues;
        }
    }
}
