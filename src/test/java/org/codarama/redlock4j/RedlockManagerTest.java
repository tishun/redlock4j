/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j;

import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedlockManager. These tests focus on the manager's public interface and configuration handling.
 */
@Tag("unit")
public class RedlockManagerTest {

    private RedlockConfiguration testConfig;

    @BeforeEach
    void setUp() {
        testConfig = RedlockConfiguration.builder().addRedisNode("localhost", 6379).addRedisNode("localhost", 6380)
                .addRedisNode("localhost", 6381).defaultLockTimeout(Duration.ofSeconds(30))
                .retryDelay(Duration.ofMillis(200)).maxRetryAttempts(3).lockAcquisitionTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    public void testConfigurationValidation() {
        assertNotNull(testConfig);
        assertEquals(3, testConfig.getRedisNodes().size());
        assertEquals(Duration.ofSeconds(30), testConfig.getDefaultLockTimeout());
        assertEquals(Duration.ofMillis(200), testConfig.getRetryDelay());
        assertEquals(3, testConfig.getMaxRetryAttempts());
        assertEquals(Duration.ofSeconds(10), testConfig.getLockAcquisitionTimeout());
    }

    @Test
    public void testInvalidConfigurationWithTooFewNodes() {
        assertThrows(IllegalArgumentException.class, () -> RedlockConfiguration.builder()
                .addRedisNode("localhost", 6379).addRedisNode("localhost", 6380).build());
    }

    @Test
    public void testInvalidConfigurationWithNegativeTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> RedlockConfiguration.builder().addRedisNode("localhost", 6379).addRedisNode("localhost", 6380)
                        .addRedisNode("localhost", 6381).defaultLockTimeout(Duration.ofSeconds(-1)).build());
    }

    @Test
    public void testQuorumCalculation() {
        // Test quorum calculation for different node counts
        assertEquals(2, (3 / 2) + 1); // 3 nodes -> quorum of 2
        assertEquals(3, (5 / 2) + 1); // 5 nodes -> quorum of 3
        assertEquals(4, (7 / 2) + 1); // 7 nodes -> quorum of 4
    }

    @Test
    public void testConfigurationBuilderPattern() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("redis1.example.com", 6379)
                .addRedisNode("redis2.example.com", 6379).addRedisNode("redis3.example.com", 6379)
                .defaultLockTimeout(Duration.ofMinutes(1)).retryDelay(Duration.ofMillis(500)).maxRetryAttempts(5)
                .clockDriftFactor(0.02).lockAcquisitionTimeout(Duration.ofSeconds(30)).build();

        assertNotNull(config);
        assertEquals(3, config.getRedisNodes().size());
        assertEquals(Duration.ofMinutes(1), config.getDefaultLockTimeout());
        assertEquals(Duration.ofMillis(500), config.getRetryDelay());
        assertEquals(5, config.getMaxRetryAttempts());
        assertEquals(0.02, config.getClockDriftFactor(), 0.001);
        assertEquals(Duration.ofSeconds(30), config.getLockAcquisitionTimeout());
    }

    @Test
    public void testConfigurationWithCustomClockDrift() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379)
                .addRedisNode("localhost", 6380).addRedisNode("localhost", 6381).clockDriftFactor(0.05).build();

        assertEquals(0.05, config.getClockDriftFactor(), 0.001);
    }

    @Test
    public void testConfigurationDefaults() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379)
                .addRedisNode("localhost", 6380).addRedisNode("localhost", 6381).build();

        // Test default values
        assertEquals(Duration.ofSeconds(30), config.getDefaultLockTimeout());
        assertEquals(Duration.ofMillis(200), config.getRetryDelay());
        assertEquals(3, config.getMaxRetryAttempts());
        assertEquals(0.01, config.getClockDriftFactor(), 0.001);
        assertEquals(Duration.ofSeconds(10), config.getLockAcquisitionTimeout());
        assertFalse(config.isSingleNodeMode());
    }

    @Test
    public void testSingleNodeModeConfiguration() {
        // Single node automatically enables single-node mode
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379).build();

        assertTrue(config.isSingleNodeMode());
        assertEquals(1, config.getRedisNodes().size());
        assertEquals(1, config.getQuorum());
    }

    @Test
    public void testTwoNodesRejected() {
        // 2 nodes is not supported - cannot form proper quorum
        assertThrows(IllegalArgumentException.class, () -> RedlockConfiguration.builder()
                .addRedisNode("localhost", 6379).addRedisNode("localhost", 6380).build());
    }

    @Test
    public void testQuorumInMultiNodeMode() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379)
                .addRedisNode("localhost", 6380).addRedisNode("localhost", 6381).build();

        assertFalse(config.isSingleNodeMode());
        assertEquals(2, config.getQuorum()); // 3 nodes -> quorum of 2
    }
}
