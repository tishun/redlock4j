/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.driver.RedisDriver;

import java.time.Duration;
import java.util.List;

/**
 * Factory for creating wait strategy instances.
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public final class WaitStrategyFactory {

    private WaitStrategyFactory() {
        // Utility class
    }

    /**
     * Creates and initializes a wait strategy based on the configuration.
     *
     * @param strategyType
     *            the type of strategy to create
     * @param drivers
     *            the Redis drivers to use
     * @param retryDelay
     *            the retry delay
     * @return an initialized wait strategy
     */
    public static LockWaitStrategy create(WaitStrategy strategyType, List<RedisDriver> drivers, Duration retryDelay) {
        return create(strategyType, drivers, retryDelay, retryDelay, 1.0, 0.0);
    }

    /**
     * Creates and initializes a wait strategy, passing backoff parameters to strategies that honor exponential backoff.
     *
     * @param strategyType
     *            the type of strategy to create
     * @param drivers
     *            the Redis drivers to use
     * @param retryDelay
     *            the base retry delay
     * @param maxRetryDelay
     *            the upper bound on the retry delay after backoff growth
     * @param retryDelayMultiplier
     *            multiplier per attempt (1.0 disables growth)
     * @param retryDelayJitterRatio
     *            jitter ratio in [0.0, 1.0]
     * @return an initialized wait strategy
     */
    public static LockWaitStrategy create(WaitStrategy strategyType, List<RedisDriver> drivers, Duration retryDelay,
            Duration maxRetryDelay, double retryDelayMultiplier, double retryDelayJitterRatio) {
        LockWaitStrategy strategy;

        switch (strategyType) {
            case KEYSPACE_NOTIFICATIONS :
                strategy = new KeyspaceWaitStrategy();
                break;
            case POLLING :
                strategy = new PollingWaitStrategy();
                break;
            default :
                throw new IllegalArgumentException("Unknown wait strategy: " + strategyType);
        }

        strategy.initialize(drivers, retryDelay, maxRetryDelay, retryDelayMultiplier, retryDelayJitterRatio);
        return strategy;
    }
}
