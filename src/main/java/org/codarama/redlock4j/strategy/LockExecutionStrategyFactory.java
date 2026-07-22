/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.RedlockException;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.driver.RedisDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Factory for creating the appropriate {@link LockExecutionStrategy} based on configuration.
 * 
 * <p>
 * Strategy selection is based on the number of available Redis drivers:
 * </p>
 * <ul>
 * <li>1 driver → {@link SingleNodeStrategy} (optimized, no consensus overhead)</li>
 * <li>3+ drivers → {@link MultiNodeStrategy} (distributed Redlock with quorum)</li>
 * </ul>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public final class LockExecutionStrategyFactory {

    private static final Logger logger = LoggerFactory.getLogger(LockExecutionStrategyFactory.class);

    private LockExecutionStrategyFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates the appropriate execution strategy based on driver count.
     *
     * @param drivers
     *            the list of Redis drivers
     * @param config
     *            the Redlock configuration
     * @return the appropriate lock execution strategy
     * @throws RedlockException
     *             if no drivers are available
     */
    public static LockExecutionStrategy create(List<RedisDriver> drivers, RedlockConfiguration config) {
        if (drivers == null || drivers.isEmpty()) {
            throw new RedlockException("No Redis drivers available");
        }

        if (drivers.size() == 1) {
            logger.debug("Using SingleNodeStrategy - optimized for single Redis instance");
            return new SingleNodeStrategy(drivers.get(0));
        }

        logger.debug("Using MultiNodeStrategy with {} nodes, quorum={}", drivers.size(), config.getQuorum());
        return new MultiNodeStrategy(drivers, config);
    }
}
