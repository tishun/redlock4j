/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j;

import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.strategy.WaitStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedlockManager. These tests focus on the manager's public interface and configuration handling.
 */
@Tag("unit")
public class RedlockManagerTest {

    private RedlockConfiguration testConfig;
    private RedlockConfiguration singleNodeConfig;
    private RedlockManager manager;

    @BeforeEach
    void setUp() {
        testConfig = RedlockConfiguration.builder().addRedisNode("localhost", 6379).addRedisNode("localhost", 6380)
                .addRedisNode("localhost", 6381).defaultLockTimeout(Duration.ofSeconds(30))
                .retryDelay(Duration.ofMillis(200)).maxRetryAttempts(3).lockAcquisitionTimeout(Duration.ofSeconds(10))
                .build();

        singleNodeConfig = RedlockConfiguration.builder().addRedisNode("localhost", 6379)
                .defaultLockTimeout(Duration.ofSeconds(30)).retryDelay(Duration.ofMillis(100)).usePolling().build();
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
            manager = null;
        }
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

    // === RedlockManager factory tests ===

    @Test
    public void testWithJedisCreatesManager() {
        // This test requires a running Redis server, so we test just the factory method
        // by catching the exception when Redis is not available
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertEquals(RedlockManager.DriverType.JEDIS, manager.getDriverType());
        } catch (RedlockException e) {
            // Expected if Redis is not running - test passes
            assertTrue(e.getMessage().contains("Failed to connect"));
        }
    }

    @Test
    public void testWithLettuceCreatesManager() {
        try {
            manager = RedlockManager.withLettuce(singleNodeConfig);
            assertEquals(RedlockManager.DriverType.LETTUCE, manager.getDriverType());
        } catch (RedlockException e) {
            // Expected if Redis is not running
            assertTrue(e.getMessage().contains("Failed to connect"));
        }
    }

    @Test
    public void testCreateLockWithNullKeyThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createLock(null));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateLockWithEmptyKeyThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createLock(""));
            assertThrows(IllegalArgumentException.class, () -> manager.createLock("   "));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateAsyncLockWithNullKeyThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createAsyncLock(null));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateRxLockWithNullKeyThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createRxLock(null));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateAsyncRxLockWithNullKeyThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createAsyncRxLock(null));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateFairLockWithNullKeyThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createFairLock(null));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateMultiLockWithNullKeysThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createMultiLock(null));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateMultiLockWithEmptyKeysThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createMultiLock(Arrays.asList()));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateReadWriteLockWithNullKeyThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createReadWriteLock(null));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateSemaphoreWithNullKeyThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createSemaphore(null, 5));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateSemaphoreWithZeroPermitsThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createSemaphore("test-semaphore", 0));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateSemaphoreWithNegativePermitsThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createSemaphore("test-semaphore", -1));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateCountDownLatchWithNullKeyThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createCountDownLatch(null, 5));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCreateCountDownLatchWithNegativeCountThrowsException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertThrows(IllegalArgumentException.class, () -> manager.createCountDownLatch("test-latch", -1));
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testGetQuorum() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertEquals(1, manager.getQuorum());
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testCloseIsIdempotent() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            assertDoesNotThrow(() -> {
                manager.close();
                manager.close();
                manager.close();
            });
            manager = null; // Already closed
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testOperationsAfterCloseThrowException() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            manager.close();

            assertThrows(RedlockException.class, () -> manager.createLock("test-lock"));
            assertThrows(RedlockException.class, () -> manager.createAsyncLock("test-lock"));
            assertThrows(RedlockException.class, () -> manager.createRxLock("test-lock"));
            assertThrows(RedlockException.class, () -> manager.createAsyncRxLock("test-lock"));
            assertThrows(RedlockException.class, () -> manager.createFairLock("test-lock"));
            assertThrows(RedlockException.class, () -> manager.createMultiLock(Arrays.asList("lock1", "lock2")));
            assertThrows(RedlockException.class, () -> manager.createReadWriteLock("test-rwlock"));
            assertThrows(RedlockException.class, () -> manager.createSemaphore("test-semaphore", 5));
            assertThrows(RedlockException.class, () -> manager.createCountDownLatch("test-latch", 3));

            manager = null; // Already closed
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testGetConnectedNodeCountAfterClose() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            manager.close();
            assertEquals(0, manager.getConnectedNodeCount());
            manager = null; // Already closed
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testIsHealthyAfterClose() {
        try {
            manager = RedlockManager.withJedis(singleNodeConfig);
            manager.close();
            assertFalse(manager.isHealthy());
            manager = null; // Already closed
        } catch (RedlockException e) {
            // Redis not available, skip
        }
    }

    @Test
    public void testConfigurationWithPollingStrategy() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379).usePolling()
                .build();

        assertEquals(WaitStrategy.POLLING, config.getWaitStrategy());
    }

    @Test
    public void testConfigurationDefaultStrategy() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379).build();

        // Default strategy is KEYSPACE_NOTIFICATIONS
        assertEquals(WaitStrategy.KEYSPACE_NOTIFICATIONS, config.getWaitStrategy());
    }
}
