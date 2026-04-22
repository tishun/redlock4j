/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.RedlockException;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.driver.RedisDriver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LockExecutionStrategyFactory.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class LockExecutionStrategyFactoryTest {

    @Mock
    private RedisDriver mockDriver1;
    @Mock
    private RedisDriver mockDriver2;
    @Mock
    private RedisDriver mockDriver3;

    @Test
    void testCreateSingleNodeStrategy() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379)
                .defaultLockTimeout(Duration.ofSeconds(30)).build();

        List<RedisDriver> drivers = Collections.singletonList(mockDriver1);

        LockExecutionStrategy strategy = LockExecutionStrategyFactory.create(drivers, config);

        assertNotNull(strategy);
        assertTrue(strategy instanceof SingleNodeStrategy);
        assertTrue(strategy.isSingleNodeMode());
    }

    @Test
    void testCreateMultiNodeStrategy() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379)
                .addRedisNode("localhost", 6380).addRedisNode("localhost", 6381)
                .defaultLockTimeout(Duration.ofSeconds(30)).build();

        List<RedisDriver> drivers = Arrays.asList(mockDriver1, mockDriver2, mockDriver3);

        LockExecutionStrategy strategy = LockExecutionStrategyFactory.create(drivers, config);

        assertNotNull(strategy);
        assertTrue(strategy instanceof MultiNodeStrategy);
        assertFalse(strategy.isSingleNodeMode());
    }

    @Test
    void testCreateWithNullDriversThrows() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379)
                .defaultLockTimeout(Duration.ofSeconds(30)).build();

        assertThrows(RedlockException.class, () -> LockExecutionStrategyFactory.create(null, config));
    }

    @Test
    void testCreateWithEmptyDriversThrows() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379)
                .defaultLockTimeout(Duration.ofSeconds(30)).build();

        assertThrows(RedlockException.class,
                () -> LockExecutionStrategyFactory.create(Collections.emptyList(), config));
    }
}
