/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.LockResult;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.driver.RedisDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Lock execution strategy for multi-node Redis deployments using the Redlock algorithm.
 * 
 * <p>
 * This strategy implements distributed consensus:
 * </p>
 * <ul>
 * <li>Quorum-based lock acquisition (N/2 + 1 nodes must agree)</li>
 * <li>Clock drift compensation for distributed safety</li>
 * <li>Atomic release on all nodes</li>
 * </ul>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class MultiNodeStrategy implements LockExecutionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(MultiNodeStrategy.class);

    private final List<RedisDriver> drivers;
    private final int quorum;
    private final double clockDriftFactor;

    /**
     * Creates a multi-node strategy with the given drivers and configuration.
     *
     * @param drivers
     *            the list of Redis drivers for all nodes
     * @param config
     *            the Redlock configuration
     */
    public MultiNodeStrategy(List<RedisDriver> drivers, RedlockConfiguration config) {
        if (drivers == null || drivers.isEmpty()) {
            throw new IllegalArgumentException("At least one driver is required");
        }
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        this.drivers = drivers;
        this.quorum = config.getQuorum();
        this.clockDriftFactor = config.getClockDriftFactor();
    }

    @Override
    public LockResult acquireLock(String key, String value, long timeoutMs) {
        Instant startTime = Instant.now();
        int successfulNodes = 0;

        for (RedisDriver driver : drivers) {
            try {
                if (driver.setIfNotExists(key, value, timeoutMs)) {
                    successfulNodes++;
                }
            } catch (RedisDriverException e) {
                logger.warn("Failed to acquire lock on {}: {}", driver.getIdentifier(), e.getMessage());
            }
        }

        Duration elapsed = Duration.between(startTime, Instant.now());
        long validityTime = calculateValidityTime(timeoutMs, elapsed.toMillis());

        boolean acquired = successfulNodes >= quorum && validityTime > 0;

        if (!acquired) {
            // Release any partial locks
            releaseLock(key, value);
            logger.debug("Failed to acquire lock: key={}, successfulNodes={}/{}, quorum={}", key, successfulNodes,
                    drivers.size(), quorum);
            return LockResult.NOT_ACQUIRED;
        }

        logger.debug("Lock acquired: key={}, successfulNodes={}/{}, validityTime={}ms", key, successfulNodes,
                drivers.size(), validityTime);
        return new LockResult(true, validityTime, value, successfulNodes, drivers.size());
    }

    @Override
    public void releaseLock(String key, String value) {
        for (RedisDriver driver : drivers) {
            try {
                driver.deleteIfValueMatches(key, value);
            } catch (RedisDriverException e) {
                logger.warn("Failed to release lock on {}: {}", driver.getIdentifier(), e.getMessage());
            }
        }
        logger.debug("Lock released on all nodes: key={}", key);
    }

    @Override
    public boolean extendLock(String key, String currentValue, long newTimeoutMs) {
        Instant startTime = Instant.now();
        int successfulNodes = 0;

        for (RedisDriver driver : drivers) {
            try {
                if (driver.setIfValueMatches(key, currentValue, currentValue, newTimeoutMs)) {
                    successfulNodes++;
                }
            } catch (RedisDriverException e) {
                logger.warn("Failed to extend lock on {}: {}", driver.getIdentifier(), e.getMessage());
            }
        }

        Duration elapsed = Duration.between(startTime, Instant.now());
        long validityTime = calculateValidityTime(newTimeoutMs, elapsed.toMillis());

        boolean extended = successfulNodes >= quorum && validityTime > 0;
        logger.debug("Lock extension {}: key={}, successfulNodes={}/{}", extended ? "succeeded" : "failed", key,
                successfulNodes, drivers.size());
        return extended;
    }

    @Override
    public long calculateValidityTime(long timeoutMs, long elapsedMs) {
        // Account for clock drift between Redis servers
        long driftTime = (long) (timeoutMs * clockDriftFactor) + 2;
        return timeoutMs - elapsedMs - driftTime;
    }

    @Override
    public int executeOnNodes(Function<RedisDriver, Boolean> operation) {
        int successCount = 0;
        for (RedisDriver driver : drivers) {
            try {
                Boolean result = operation.apply(driver);
                if (Boolean.TRUE.equals(result)) {
                    successCount++;
                }
            } catch (Exception e) {
                logger.warn("Error executing operation on {}: {}", driver.getIdentifier(), e.getMessage());
            }
        }
        return successCount;
    }

    @Override
    public boolean isSuccessful(int successCount) {
        return successCount >= quorum;
    }

    @Override
    public boolean isSingleNodeMode() {
        return false;
    }
}
