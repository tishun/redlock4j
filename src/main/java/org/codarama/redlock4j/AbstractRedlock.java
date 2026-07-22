/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j;

import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.strategy.LockExecutionStrategy;
import org.codarama.redlock4j.strategy.LockExecutionStrategyFactory;
import org.codarama.redlock4j.strategy.BackoffCalculator;
import org.codarama.redlock4j.strategy.LockWaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Abstract base class for Redlock implementations providing common functionality for distributed locking and
 * synchronization primitives with Redis.
 *
 * <p>
 * This class provides common fields, lock value generation, wait strategies, and helper methods shared across all lock
 * and semaphore implementations.
 * </p>
 *
 * <p>
 * Classes implementing {@link Lock} should also override {@link #newCondition()} to throw
 * {@link UnsupportedOperationException}.
 * </p>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public abstract class AbstractRedlock {

    private static final Logger logger = LoggerFactory.getLogger(AbstractRedlock.class);

    protected final List<RedisDriver> redisDrivers;
    protected final RedlockConfiguration config;
    protected final SecureRandom secureRandom;
    protected final LockWaitStrategy waitStrategy;
    protected final LockExecutionStrategy executionStrategy;

    /**
     * Base lock state class containing common fields for tracking lock ownership.
     */
    protected static class BaseLockState {
        public final String lockValue;
        public final Instant acquisitionTime;
        public final Duration validityDuration;
        protected int holdCount;

        public BaseLockState(String lockValue, Instant acquisitionTime, Duration validityDuration) {
            this.lockValue = lockValue;
            this.acquisitionTime = acquisitionTime;
            this.validityDuration = validityDuration;
            this.holdCount = 1;
        }

        public boolean isValid() {
            return Instant.now().isBefore(getExpiryTime());
        }

        public Instant getExpiryTime() {
            return acquisitionTime.plus(validityDuration);
        }

        public void incrementHoldCount() {
            holdCount++;
        }

        public int decrementHoldCount() {
            return --holdCount;
        }

        public int getHoldCount() {
            return holdCount;
        }
    }

    protected AbstractRedlock(List<RedisDriver> redisDrivers, RedlockConfiguration config,
            LockWaitStrategy waitStrategy) {
        this.redisDrivers = redisDrivers;
        this.config = config;
        this.secureRandom = new SecureRandom();
        this.waitStrategy = waitStrategy;
        this.executionStrategy = LockExecutionStrategyFactory.create(redisDrivers, config);
    }

    /**
     * Attempts to acquire the lock within the given timeout. Subclasses implementing {@link Lock} must override this
     * method.
     *
     * @param timeout
     *            the maximum time to wait for the lock
     * @return true if the lock was acquired, false if the timeout elapsed
     * @throws InterruptedException
     *             if the current thread is interrupted
     * @throws UnsupportedOperationException
     *             if the subclass does not support this operation
     */
    public boolean tryLock(Duration timeout) throws InterruptedException {
        throw new UnsupportedOperationException("tryLock not supported by this class");
    }

    /**
     * Attempts to acquire the lock within the given time. This method satisfies the {@link Lock} interface contract and
     * delegates to {@link #tryLock(Duration)}.
     *
     * @param time
     *            the maximum time to wait for the lock
     * @param unit
     *            the time unit of the time argument
     * @return true if the lock was acquired, false if the timeout elapsed
     * @throws InterruptedException
     *             if the current thread is interrupted
     */
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return tryLock(Duration.ofNanos(unit.toNanos(time)));
    }

    /**
     * Generates a unique random lock value using a secure random generator. The value is a 40-character hexadecimal
     * string (20 random bytes).
     *
     * @return a unique lock value
     */
    protected String generateLockValue() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(40);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Waits for a lock to be released using the configured wait strategy. If no wait strategy is configured, falls back
     * to a simple sleep with the configured retry delay.
     *
     * @param lockKey
     *            the key of the lock to wait for
     * @param remainingTimeoutMs
     *            remaining timeout in milliseconds
     * @throws InterruptedException
     *             if the thread is interrupted while waiting
     */
    protected void waitForLockRelease(String lockKey, long remainingTimeoutMs) throws InterruptedException {
        waitForLockRelease(lockKey, remainingTimeoutMs, 0);
    }

    /**
     * Attempt-aware variant of {@link #waitForLockRelease(String, long)}. The {@code attempt} parameter is forwarded to
     * the configured wait strategy so that strategies honoring exponential backoff can grow the wait between successive
     * retries. The no-wait-strategy fallback applies the same backoff formula via {@link BackoffCalculator}.
     *
     * @param lockKey
     *            the key of the lock to wait for
     * @param remainingTimeoutMs
     *            remaining timeout in milliseconds
     * @param attempt
     *            0-based attempt counter
     * @throws InterruptedException
     *             if the thread is interrupted while waiting
     */
    protected void waitForLockRelease(String lockKey, long remainingTimeoutMs, int attempt)
            throws InterruptedException {
        if (waitStrategy != null) {
            waitStrategy.waitForRelease(lockKey, Duration.ofMillis(Math.max(remainingTimeoutMs, 1)), attempt);
        } else {
            Duration delay = BackoffCalculator.compute(config.getRetryDelay(), config.getMaxRetryDelay(),
                    config.getRetryDelayMultiplier(), config.getRetryDelayJitterRatio(), attempt);
            long sleepMs = Math.min(delay.toMillis(), Math.max(remainingTimeoutMs, 1));
            if (sleepMs > 0) {
                Thread.sleep(sleepMs);
            }
        }
    }

    /**
     * Checks if the lock is currently held and valid.
     *
     * @param state
     *            the lock state to check
     * @return true if the state is not null and valid
     */
    protected boolean isLockStateValid(BaseLockState state) {
        return state != null && state.isValid();
    }

    /**
     * Calculates the remaining validity time for a lock state.
     *
     * @param state
     *            the lock state
     * @return remaining validity time in milliseconds, or 0 if not held or expired
     */
    protected long calculateRemainingValidityTime(BaseLockState state) {
        if (state == null) {
            return 0;
        }
        Duration remaining = Duration.between(Instant.now(), state.getExpiryTime());
        return Math.max(0, remaining.toMillis());
    }

    /**
     * Gets the logger for subclasses.
     *
     * @return the logger instance
     */
    protected Logger getLogger() {
        return logger;
    }
}
