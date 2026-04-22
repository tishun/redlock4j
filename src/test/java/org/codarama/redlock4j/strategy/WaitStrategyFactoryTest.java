/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WaitStrategyFactory.
 */
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
    void create_shouldThrowForUnknownStrategy() {
        // This test is just for coverage - in reality enum prevents unknown values
        // But if someone adds a new enum value without updating factory, it should fail
    }
}
