/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.LockResult;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.driver.RedisDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

/**
 * Optimized lock execution strategy for single-node Redis deployments.
 * 
 * <p>
 * This strategy bypasses all multi-node consensus logic:
 * </p>
 * <ul>
 * <li>No quorum calculations</li>
 * <li>No clock drift compensation</li>
 * <li>No node iteration</li>
 * <li>Direct Redis operations</li>
 * </ul>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class SingleNodeStrategy implements LockExecutionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SingleNodeStrategy.class);

    private final RedisDriver driver;

    /**
     * Creates a single-node strategy with the given driver.
     *
     * @param driver
     *            the Redis driver for the single node
     */
    public SingleNodeStrategy(RedisDriver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("Driver cannot be null");
        }
        this.driver = driver;
    }

    @Override
    public LockResult acquireLock(String key, String value, long timeoutMs) {
        Instant startTime = Instant.now();

        try {
            boolean acquired = driver.setIfNotExists(key, value, timeoutMs);

            if (acquired) {
                Duration elapsed = Duration.between(startTime, Instant.now());
                // No clock drift compensation - full validity time available
                long validityTime = calculateValidityTime(timeoutMs, elapsed.toMillis());

                logger.debug("Lock acquired on single node: key={}, validityTime={}ms", key, validityTime);
                return new LockResult(true, validityTime, value, 1, 1);
            }

            logger.debug("Failed to acquire lock on single node: key={}", key);
            return LockResult.NOT_ACQUIRED;

        } catch (RedisDriverException e) {
            logger.warn("Error acquiring lock: {}", e.getMessage());
            return LockResult.NOT_ACQUIRED;
        }
    }

    @Override
    public void releaseLock(String key, String value) {
        try {
            driver.deleteIfValueMatches(key, value);
            logger.debug("Lock released on single node: key={}", key);
        } catch (RedisDriverException e) {
            logger.warn("Error releasing lock: {}", e.getMessage());
        }
    }

    @Override
    public boolean extendLock(String key, String currentValue, long newTimeoutMs) {
        try {
            boolean extended = driver.setIfValueMatches(key, currentValue, currentValue, newTimeoutMs);
            logger.debug("Lock extension {}: key={}", extended ? "succeeded" : "failed", key);
            return extended;
        } catch (RedisDriverException e) {
            logger.warn("Error extending lock: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public long calculateValidityTime(long timeoutMs, long elapsedMs) {
        // No clock drift compensation needed for single node
        return timeoutMs - elapsedMs;
    }

    @Override
    public int executeOnNodes(Function<RedisDriver, Boolean> operation) {
        try {
            Boolean result = operation.apply(driver);
            return Boolean.TRUE.equals(result) ? 1 : 0;
        } catch (Exception e) {
            logger.warn("Error executing operation on node: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean isSuccessful(int successCount) {
        return successCount >= 1;
    }

    @Override
    public boolean isSingleNodeMode() {
        return true;
    }
}
