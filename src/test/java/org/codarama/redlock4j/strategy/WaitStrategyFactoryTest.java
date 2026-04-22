/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.driver.RedisDriverException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WaitStrategyFactory.
 */
@Tag("unit")
class WaitStrategyFactoryTest {

    @Test
    void create_shouldCreatePollingStrategy() {
        LockWaitStrategy strategy = WaitStrategyFactory.create(WaitStrategy.POLLING, Collections.emptyList(),
                Duration.ofMillis(50));

        assertNotNull(strategy);
        assertEquals(WaitStrategy.POLLING, strategy.getType());
        assertTrue(strategy instanceof PollingWaitStrategy);

        strategy.close();
    }

    @Test
    void create_shouldCreateKeyspaceStrategy() throws RedisDriverException {
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-driver");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("Kgx");
        List<RedisDriver> drivers = Arrays.asList(mockDriver);

        LockWaitStrategy strategy = WaitStrategyFactory.create(WaitStrategy.KEYSPACE_NOTIFICATIONS, drivers,
                Duration.ofMillis(50));

        assertNotNull(strategy);
        assertEquals(WaitStrategy.KEYSPACE_NOTIFICATIONS, strategy.getType());
        assertTrue(strategy instanceof KeyspaceWaitStrategy);

        strategy.close();
    }

    @Test
    void create_shouldInitializeStrategy() {
        LockWaitStrategy strategy = WaitStrategyFactory.create(WaitStrategy.POLLING, Collections.emptyList(),
                Duration.ofMillis(100));

        // Strategy should be initialized and ready to use
        assertNotNull(strategy);
        assertEquals(WaitStrategy.POLLING, strategy.getType());

        strategy.close();
    }
}
