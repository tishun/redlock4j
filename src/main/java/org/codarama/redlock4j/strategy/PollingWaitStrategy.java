/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.driver.RedisDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Wait strategy that uses polling to check for lock release.
 *
 * <p>
 * This strategy periodically attempts to acquire the lock at a configurable interval. It is the fallback strategy for
 * environments where keyspace notifications are not available.
 * </p>
 *
 * <p>
 * <b>Performance characteristics:</b>
 * <ul>
 * <li>Uncontended lock: ~0.6ms (same as keyspace)</li>
 * <li>Contended lock: 50-100ms average (depends on retry delay)</li>
 * <li>CPU usage: Higher than keyspace (busy-waiting)</li>
 * </ul>
 * </p>
 *
 * @see WaitStrategy#POLLING
 * @since 1.0
 * @author Tihomir Mateev
 */
public class PollingWaitStrategy implements LockWaitStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PollingWaitStrategy.class);

    private Duration retryDelay;
    private Duration maxRetryDelay;
    private double retryDelayMultiplier;
    private double retryDelayJitterRatio;
    private volatile boolean closed = false;

    @Override
    public void initialize(List<RedisDriver> drivers, Duration retryDelay) {
        initialize(drivers, retryDelay, retryDelay, 1.0, 0.0);
    }

    @Override
    public void initialize(List<RedisDriver> drivers, Duration retryDelay, Duration maxRetryDelay,
            double retryDelayMultiplier, double retryDelayJitterRatio) {
        this.retryDelay = retryDelay;
        this.maxRetryDelay = maxRetryDelay != null ? maxRetryDelay : retryDelay;
        this.retryDelayMultiplier = retryDelayMultiplier;
        this.retryDelayJitterRatio = retryDelayJitterRatio;
        logger.info("Polling wait strategy initialized with retry delay {}, max {} , multiplier {}, jitter ratio {}",
                retryDelay, this.maxRetryDelay, retryDelayMultiplier, retryDelayJitterRatio);
    }

    @Override
    public boolean waitForRelease(String lockKey, Duration timeout) throws InterruptedException {
        return waitForRelease(lockKey, timeout, 0);
    }

    @Override
    public boolean waitForRelease(String lockKey, Duration timeout, int attempt) throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("Strategy has been closed");
        }

        Instant deadline = Instant.now().plus(timeout);

        if (Instant.now().isBefore(deadline)) {
            Duration remaining = Duration.between(Instant.now(), deadline);
            Duration backoff = computeBackoff(attempt);
            Duration sleepDuration = remaining.compareTo(backoff) < 0 ? remaining : backoff;

            if (!sleepDuration.isNegative() && !sleepDuration.isZero()) {
                Thread.sleep(sleepDuration.toMillis());
            }

            // Return true to signal that caller should try to acquire
            // The caller will then check if the lock is available
            return true;
        }

        return false;
    }

    /**
     * Computes the backoff delay for the given attempt index, capped at {@link #maxRetryDelay} and jittered.
     */
    private Duration computeBackoff(int attempt) {
        return BackoffCalculator.compute(retryDelay, maxRetryDelay, retryDelayMultiplier, retryDelayJitterRatio,
                attempt);
    }

    @Override
    public WaitStrategy getType() {
        return WaitStrategy.POLLING;
    }

    @Override
    public void close() {
        closed = true;
        logger.debug("Polling wait strategy closed");
    }
}
