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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Implementation of the Redlock distributed locking algorithm that implements Java's Lock interface.
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class Redlock extends AbstractRedlock implements Lock {
    private static final Logger logger = LoggerFactory.getLogger(Redlock.class);

    private final String lockKey;

    // Thread-local storage for lock state
    private final ThreadLocal<LockState> lockState = new ThreadLocal<>();

    private static class LockState extends BaseLockState {
        LockState(String lockValue, Instant acquisitionTime, Duration validityDuration) {
            super(lockValue, acquisitionTime, validityDuration);
        }
    }

    /**
     * Creates a new distributed lock.
     *
     * @param lockKey
     *            the unique identifier for this lock
     * @param redisDrivers
     *            the Redis drivers to use
     * @param config
     *            the Redlock configuration
     * @param waitStrategy
     *            the wait strategy for lock contention
     */
    public Redlock(String lockKey, List<RedisDriver> redisDrivers, RedlockConfiguration config,
            LockWaitStrategy waitStrategy) {
        super(redisDrivers, config, waitStrategy);
        this.lockKey = lockKey;
    }

    /**
     * {@inheritDoc}
     *
     * @throws RedlockException
     *             if the lock cannot be acquired within the configured timeout
     */
    @Override
    public void lock() {
        try {
            if (!tryLock(config.getLockAcquisitionTimeout())) {
                throw new RedlockException("Failed to acquire lock within timeout: " + lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedlockException("Interrupted while acquiring lock: " + lockKey, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws RedlockException
     *             if the lock cannot be acquired within the configured timeout
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (!tryLock(config.getLockAcquisitionTimeout())) {
            throw new RedlockException("Failed to acquire lock within timeout: " + lockKey);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean tryLock() {
        try {
            return tryLock(Duration.ZERO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Attempts to acquire the lock within the specified timeout.
     *
     * @param timeout
     *            the maximum time to wait for the lock
     * @return true if the lock was acquired, false if timeout elapsed
     * @throws InterruptedException
     *             if the thread is interrupted while waiting
     */
    public boolean tryLock(Duration timeout) throws InterruptedException {
        // Check if current thread already holds the lock (reentrancy)
        LockState currentState = lockState.get();
        if (currentState != null && currentState.isValid()) {
            currentState.incrementHoldCount();
            logger.debug("Reentrant lock acquisition for {} (hold count: {})", lockKey, currentState.holdCount);
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
                logger.debug("Lock acquisition timeout exceeded for {} after {} attempts", lockKey, attempt);
                return false;
            }

            LockResult result = attemptLock();
            if (result.isAcquired()) {
                lockState.set(new LockState(result.getLockValue(), Instant.now(),
                        Duration.ofMillis(result.getValidityTimeMs())));
                logger.debug("Successfully acquired lock {} on attempt {}", lockKey, attempt + 1);
                return true;
            }

            // For zero timeout (tryLock()), only try once
            if (timeout.isZero()) {
                logger.debug("Lock {} not available for immediate acquisition", lockKey);
                return false;
            }

            // Wait before retrying
            remaining = Duration.between(Instant.now(), deadline);
            if (!remaining.isNegative()) {
                waitForLockReleaseWithJitter(remaining);
            }
            attempt++;
        }
    }

    /**
     * Waits for the lock to be released with added jitter for backward compatibility.
     */
    private void waitForLockReleaseWithJitter(Duration remainingTimeout) throws InterruptedException {
        if (waitStrategy != null) {
            waitForLockRelease(lockKey, remainingTimeout.toMillis());
        } else {
            // Fallback to simple sleep with jitter (backward compatibility for Redlock)
            long retryDelayMs = config.getRetryDelay().toMillis();
            long delay = retryDelayMs + ThreadLocalRandom.current().nextLong(retryDelayMs);
            Thread.sleep(delay);
        }
    }

    private LockResult attemptLock() {
        String lockValue = generateLockValue();
        // Delegate to execution strategy (SingleNode or MultiNode)
        return executionStrategy.acquireLock(lockKey, lockValue, config.getDefaultLockTimeout().toMillis());
    }

    /** {@inheritDoc} */
    @Override
    public void unlock() {
        LockState state = lockState.get();
        if (state == null) {
            logger.warn("Attempting to unlock {} but no lock state found for current thread", lockKey);
            return;
        }

        if (!state.isValid()) {
            logger.warn("Lock {} has expired, cannot safely unlock", lockKey);
            lockState.remove();
            return;
        }

        // Handle reentrancy - only release when hold count reaches 0
        int remainingHolds = state.decrementHoldCount();
        if (remainingHolds > 0) {
            logger.debug("Reentrant unlock for {} (remaining holds: {})", lockKey, remainingHolds);
            return;
        }

        // Final unlock - release the distributed lock
        releaseLock(state.lockValue);
        lockState.remove();
        logger.debug("Successfully released lock {}", lockKey);
    }

    private void releaseLock(String lockValue) {
        // Delegate to execution strategy (SingleNode or MultiNode)
        executionStrategy.releaseLock(lockKey, lockValue);
    }

    /**
     * Not supported - distributed locks cannot provide condition variables.
     *
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Conditions are not supported by distributed locks");
    }

    /**
     * Checks if the current thread holds this lock.
     *
     * @return true if the current thread holds the lock and it's still valid
     */
    public boolean isHeldByCurrentThread() {
        return isLockStateValid(lockState.get());
    }

    /**
     * Gets the remaining validity time of the lock for the current thread.
     *
     * @return remaining validity time, or {@link Duration#ZERO} if not held or expired
     */
    public Duration getRemainingValidityTime() {
        return Duration.ofMillis(calculateRemainingValidityTime(lockState.get()));
    }

    /**
     * Gets the hold count for the current thread. This indicates how many times the current thread has acquired this
     * lock.
     *
     * @return hold count, or 0 if not held by current thread
     */
    public int getHoldCount() {
        LockState state = lockState.get();
        return isLockStateValid(state) ? state.getHoldCount() : 0;
    }

    /**
     * Extends the validity time of the lock held by the current thread.
     * <p>
     * This method attempts to extend the lock on a quorum of Redis nodes using the same lock value. The extension is
     * only successful if:
     * <ul>
     * <li>The current thread holds a valid lock</li>
     * <li>The extension succeeds on at least a quorum (N/2+1) of nodes</li>
     * <li>The new validity time (after accounting for clock drift) is positive</li>
     * </ul>
     * <p>
     * <b>Important limitations:</b>
     * <ul>
     * <li>Lock extension is for efficiency, not correctness</li>
     * <li>Should not be used as a substitute for proper timeout configuration</li>
     * </ul>
     *
     * @param additionalTimeMs
     *            additional time in milliseconds to extend the lock
     * @return true if the lock was successfully extended on a quorum of nodes, false otherwise
     * @throws IllegalArgumentException
     *             if additionalTimeMs is negative or zero
     */
    public boolean extend(long additionalTimeMs) {
        if (additionalTimeMs <= 0) {
            throw new IllegalArgumentException("Additional time must be positive");
        }

        LockState state = lockState.get();
        if (state == null || !state.isValid()) {
            logger.debug("Cannot extend lock {} - not held or expired", lockKey);
            return false;
        }

        Duration additionalTime = Duration.ofMillis(additionalTimeMs);
        Duration newExpireTime = config.getDefaultLockTimeout().plus(additionalTime);

        // Delegate to execution strategy (SingleNode or MultiNode)
        boolean extended = executionStrategy.extendLock(lockKey, state.lockValue, newExpireTime.toMillis());

        if (extended) {
            // Calculate new validity time using strategy
            long newValidityTimeMs = executionStrategy.calculateValidityTime(newExpireTime.toMillis(), 0);
            Duration newValidityDuration = Duration.ofMillis(newValidityTimeMs);
            // Update local lock state with new validity time
            // Note: We create a new LockState to maintain immutability of timing fields
            LockState newState = new LockState(state.lockValue, Instant.now(), newValidityDuration);
            newState.holdCount = state.holdCount; // Preserve hold count
            lockState.set(newState);
            logger.debug("Successfully extended lock {} (new validity: {})", lockKey, newValidityDuration);
        } else {
            logger.debug("Failed to extend lock {}", lockKey);
        }

        return extended;
    }

    /**
     * Returns the lock key.
     *
     * @return the key identifying this lock
     */
    public String getLockKey() {
        return lockKey;
    }
}
