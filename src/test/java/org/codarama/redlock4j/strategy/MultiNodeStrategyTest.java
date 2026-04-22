/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.LockResult;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.driver.RedisDriverException;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiNodeStrategy.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class MultiNodeStrategyTest {

    @Mock
    private RedisDriver mockDriver1;
    @Mock
    private RedisDriver mockDriver2;
    @Mock
    private RedisDriver mockDriver3;

    private RedlockConfiguration config;
    private List<RedisDriver> drivers;
    private MultiNodeStrategy strategy;

    @BeforeEach
    void setUp() {
        config = RedlockConfiguration.builder().addRedisNode("localhost", 6379).addRedisNode("localhost", 6380)
                .addRedisNode("localhost", 6381).defaultLockTimeout(Duration.ofSeconds(30)).clockDriftFactor(0.01)
                .build();

        drivers = Arrays.asList(mockDriver1, mockDriver2, mockDriver3);

        lenient().when(mockDriver1.getIdentifier()).thenReturn("redis://localhost:6379");
        lenient().when(mockDriver2.getIdentifier()).thenReturn("redis://localhost:6380");
        lenient().when(mockDriver3.getIdentifier()).thenReturn("redis://localhost:6381");

        strategy = new MultiNodeStrategy(drivers, config);
    }

    @Test
    void testConstructorRejectsNullDrivers() {
        assertThrows(IllegalArgumentException.class, () -> new MultiNodeStrategy(null, config));
    }

    @Test
    void testConstructorRejectsEmptyDrivers() {
        assertThrows(IllegalArgumentException.class, () -> new MultiNodeStrategy(Collections.emptyList(), config));
    }

    @Test
    void testConstructorRejectsNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> new MultiNodeStrategy(drivers, null));
    }

    @Test
    void testIsNotSingleNodeMode() {
        assertFalse(strategy.isSingleNodeMode());
    }

    @Test
    void testAcquireLockWithQuorum() throws Exception {
        // 2 out of 3 succeed - quorum met
        when(mockDriver1.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);

        LockResult result = strategy.acquireLock("test-key", "test-value", 30000);

        assertTrue(result.isAcquired());
        assertEquals(2, result.getSuccessfulNodes());
        assertEquals(3, result.getTotalNodes());
    }

    @Test
    void testAcquireLockFailsWithoutQuorum() throws Exception {
        // Only 1 out of 3 succeed - quorum not met
        when(mockDriver1.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);

        LockResult result = strategy.acquireLock("test-key", "test-value", 30000);

        assertFalse(result.isAcquired());

        // Should release the partial lock on driver1
        verify(mockDriver1).deleteIfValueMatches("test-key", "test-value");
    }

    @Test
    void testReleaseLockOnAllNodes() throws Exception {
        strategy.releaseLock("test-key", "test-value");

        verify(mockDriver1).deleteIfValueMatches("test-key", "test-value");
        verify(mockDriver2).deleteIfValueMatches("test-key", "test-value");
        verify(mockDriver3).deleteIfValueMatches("test-key", "test-value");
    }

    @Test
    void testCalculateValidityTimeWithClockDrift() {
        // MultiNodeStrategy should apply clock drift compensation
        // driftTime = 30000 * 0.01 + 2 = 302ms
        long validity = strategy.calculateValidityTime(30000, 100);

        // Should be timeout - elapsed - driftTime = 30000 - 100 - 302 = 29598
        assertEquals(29598, validity);
    }

    @Test
    void testIsSuccessfulRequiresQuorum() {
        // Quorum for 3 nodes is 2
        assertFalse(strategy.isSuccessful(0));
        assertFalse(strategy.isSuccessful(1));
        assertTrue(strategy.isSuccessful(2));
        assertTrue(strategy.isSuccessful(3));
    }

    @Test
    void testExecuteOnNodesCountsSuccesses() {
        int count = strategy.executeOnNodes(driver -> {
            return driver == mockDriver1 || driver == mockDriver2;
        });
        assertEquals(2, count);
    }
}
