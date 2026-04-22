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
    private volatile boolean closed = false;

    @Override
    public void initialize(List<RedisDriver> drivers, Duration retryDelay) {
        this.retryDelay = retryDelay;
        logger.info("Polling wait strategy initialized with retry delay {}", retryDelay);
    }

    @Override
    public boolean waitForRelease(String lockKey, Duration timeout) throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("Strategy has been closed");
        }

        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            // Sleep for the retry delay
            Duration remaining = Duration.between(Instant.now(), deadline);
            Duration sleepDuration = remaining.compareTo(retryDelay) < 0 ? remaining : retryDelay;

            if (!sleepDuration.isNegative() && !sleepDuration.isZero()) {
                Thread.sleep(sleepDuration.toMillis());
            }

            // Return true to signal that caller should try to acquire
            // The caller will then check if the lock is available
            return true;
        }

        return false;
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
