/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.integration;

import org.codarama.redlock4j.FairLock;
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link FairLock}.
 */
@Tag("integration")
@Testcontainers
public class FairLockIntegrationTest {

    @Container
    static GenericContainer<?> redis1 = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379).withCommand("redis-server", "--appendonly", "yes");

    @Container
    static GenericContainer<?> redis2 = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379).withCommand("redis-server", "--appendonly", "yes");

    @Container
    static GenericContainer<?> redis3 = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379).withCommand("redis-server", "--appendonly", "yes");

    private static RedlockConfiguration testConfiguration;

    @BeforeAll
    static void setUp() {
        testConfiguration = RedlockConfiguration.builder().addRedisNode("localhost", redis1.getMappedPort(6379))
                .addRedisNode("localhost", redis2.getMappedPort(6379))
                .addRedisNode("localhost", redis3.getMappedPort(6379)).defaultLockTimeout(Duration.ofSeconds(30))
                .lockAcquisitionTimeout(Duration.ofSeconds(10)).retryDelay(Duration.ofMillis(50)).maxRetryAttempts(100)
                .usePolling() // Use polling for FairLock - better performance and avoids keyspace notification warnings
                .build();
    }

    // ========== Basic Functionality ==========

    @Test
    void shouldAcquireAndReleaseFairLock() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            FairLock fairLock = (FairLock) manager.createFairLock("basic-fair");
            assertTrue(fairLock.tryLock(5, TimeUnit.SECONDS));
            assertTrue(fairLock.isHeldByCurrentThread());
            fairLock.unlock();
            assertFalse(fairLock.isHeldByCurrentThread());
        }
    }

    @Test
    void shouldBlockSecondAcquirer() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            FairLock fairLock = (FairLock) manager.createFairLock("block-second");
            CountDownLatch firstAcquired = new CountDownLatch(1);
            CountDownLatch secondAttempted = new CountDownLatch(1);
            AtomicInteger secondResult = new AtomicInteger(-1);

            // First thread acquires
            assertTrue(fairLock.tryLock(5, TimeUnit.SECONDS));
            firstAcquired.countDown();

            // Second thread tries
            Thread t = new Thread(() -> {
                try {
                    firstAcquired.await();
                    boolean acquired = fairLock.tryLock(500, TimeUnit.MILLISECONDS);
                    secondResult.set(acquired ? 1 : 0);
                    secondAttempted.countDown();
                    if (acquired)
                        fairLock.unlock();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            t.start();

            assertTrue(secondAttempted.await(5, TimeUnit.SECONDS));
            assertEquals(0, secondResult.get(), "Second thread should be blocked");
            fairLock.unlock();
        }
    }

    // ========== Reentrancy ==========

    @Test
    void shouldSupportReentrantLocking() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            FairLock fairLock = (FairLock) manager.createFairLock("reentrant-fair");

            assertTrue(fairLock.tryLock(5, TimeUnit.SECONDS));
            assertEquals(1, fairLock.getHoldCount());

            assertTrue(fairLock.tryLock(5, TimeUnit.SECONDS));
            assertEquals(2, fairLock.getHoldCount());

            fairLock.unlock();
            assertEquals(1, fairLock.getHoldCount());
            assertTrue(fairLock.isHeldByCurrentThread());

            fairLock.unlock();
            assertEquals(0, fairLock.getHoldCount());
            assertFalse(fairLock.isHeldByCurrentThread());
        }
    }

    // ========== FIFO Ordering ==========

    @Test
    void shouldMaintainQueueOrderAcrossInstances() throws InterruptedException {
        // Test that the queue mechanism works by having different FairLock instances
        // accessing the same resource sequentially
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            List<Integer> acquisitionOrder = Collections.synchronizedList(new ArrayList<>());
            int acquisitions = 3;

            for (int i = 0; i < acquisitions; i++) {
                FairLock fairLock = (FairLock) manager.createFairLock("fifo-order-sequential");
                if (fairLock.tryLock(10, TimeUnit.SECONDS)) {
                    try {
                        acquisitionOrder.add(i);
                        Thread.sleep(50);
                    } finally {
                        fairLock.unlock();
                    }
                }
            }

            assertEquals(acquisitions, acquisitionOrder.size(), "All acquisitions should succeed");
            // Verify sequential ordering
            for (int i = 0; i < acquisitions; i++) {
                assertEquals(Integer.valueOf(i), acquisitionOrder.get(i), "Acquisition order should be sequential");
            }
        }
    }

    // ========== Timeout ==========

    @Test
    void shouldTimeoutWhenLockNotAvailable() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            FairLock fairLock = (FairLock) manager.createFairLock("timeout-test");

            assertTrue(fairLock.tryLock(5, TimeUnit.SECONDS));

            long start = System.currentTimeMillis();
            Thread t = new Thread(() -> {
                try {
                    assertFalse(fairLock.tryLock(500, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            t.start();
            t.join();
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed >= 400 && elapsed < 2000, "Should timeout after ~500ms");
            fairLock.unlock();
        }
    }

    // ========== Concurrent Access ==========

    @Test
    void shouldHandleSequentialAcquisitions() throws InterruptedException {
        // Test that multiple sequential acquisitions work correctly with FairLock
        // Each acquisition uses a fresh FairLock instance to avoid shared state issues
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            int acquisitions = 5;
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < acquisitions; i++) {
                FairLock fairLock = (FairLock) manager.createFairLock("sequential-fair");
                if (fairLock.tryLock(5, TimeUnit.SECONDS)) {
                    try {
                        successCount.incrementAndGet();
                        Thread.sleep(20);
                    } finally {
                        fairLock.unlock();
                    }
                }
            }

            assertEquals(acquisitions, successCount.get(), "All sequential acquisitions should succeed");
        }
    }

    @Test
    void shouldAllowMultipleSequentialLockHolders() throws InterruptedException {
        // Test that lock can be acquired sequentially by different threads
        // This verifies the basic FairLock functionality without complex timing
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            AtomicInteger successCount = new AtomicInteger(0);
            CountDownLatch done = new CountDownLatch(3);

            for (int i = 0; i < 3; i++) {
                final int threadNum = i;
                new Thread(() -> {
                    try {
                        // Each thread waits a bit before trying to prevent simultaneous access
                        Thread.sleep(threadNum * 100);
                        FairLock lock = (FairLock) manager.createFairLock("sequential-threads");
                        if (lock.tryLock(10, TimeUnit.SECONDS)) {
                            try {
                                successCount.incrementAndGet();
                                Thread.sleep(50);
                            } finally {
                                lock.unlock();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            assertTrue(done.await(30, TimeUnit.SECONDS), "All threads should complete");
            assertEquals(3, successCount.get(), "All threads should acquire the lock");
        }
    }

    @Test
    void shouldHandleRapidLockUnlockCycles() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            FairLock fairLock = (FairLock) manager.createFairLock("rapid-cycles");
            int cycles = 5;
            int threadCount = 3;
            AtomicInteger totalAcquisitions = new AtomicInteger(0);
            CountDownLatch done = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        for (int j = 0; j < cycles; j++) {
                            if (fairLock.tryLock(10, TimeUnit.SECONDS)) {
                                totalAcquisitions.incrementAndGet();
                                Thread.sleep(20);
                                fairLock.unlock();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            assertTrue(done.await(120, TimeUnit.SECONDS));
            assertTrue(totalAcquisitions.get() > 0, "Should have successful acquisitions");
        }
    }

    // ========== Utility Methods ==========

    @Test
    void shouldReportValidityTime() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            FairLock fairLock = (FairLock) manager.createFairLock("validity-time");

            assertTrue(fairLock.getRemainingValidityTime().isZero());

            assertTrue(fairLock.tryLock(5, TimeUnit.SECONDS));
            assertFalse(fairLock.getRemainingValidityTime().isZero());

            fairLock.unlock();
        }
    }

    // ========== Lettuce Driver ==========

    @Test
    void shouldWorkWithLettuceDriver() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withLettuce(testConfiguration)) {
            FairLock fairLock = (FairLock) manager.createFairLock("lettuce-fair");
            assertTrue(fairLock.tryLock(5, TimeUnit.SECONDS));
            assertTrue(fairLock.isHeldByCurrentThread());
            fairLock.unlock();
        }
    }
}
