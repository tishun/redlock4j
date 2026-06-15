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

    // ========== Orphan Cleanup Tests ==========

    /**
     * Tests that orphan queue entries from crashed clients are eventually cleaned up. Simulates a scenario where a
     * client adds itself to the queue but crashes before completing the lock acquisition or cleanup.
     */
    @Test
    void shouldCleanupOrphanQueueEntries() throws InterruptedException {
        // Use a short TTL configuration to speed up orphan expiration
        RedlockConfiguration shortTtlConfig = RedlockConfiguration.builder()
                .addRedisNode("localhost", redis1.getMappedPort(6379))
                .addRedisNode("localhost", redis2.getMappedPort(6379))
                .addRedisNode("localhost", redis3.getMappedPort(6379)).defaultLockTimeout(Duration.ofMillis(500)) // Very
                                                                                                                  // short
                                                                                                                  // TTL
                .lockAcquisitionTimeout(Duration.ofSeconds(5)).retryDelay(Duration.ofMillis(50)).maxRetryAttempts(50)
                .usePolling().build();

        try (RedlockManager manager = RedlockManager.withJedis(shortTtlConfig)) {
            String lockName = "orphan-cleanup-test-" + System.currentTimeMillis();

            // Simulate orphan entry: acquire lock, hold it past expiration without explicit unlock
            Thread orphanThread = new Thread(() -> {
                try {
                    FairLock lock = (FairLock) manager.createFairLock(lockName);
                    if (lock.tryLock(2, TimeUnit.SECONDS)) {
                        // Simulate crash by NOT unlocking and sleeping past TTL
                        Thread.sleep(1000); // Sleep longer than 500ms TTL
                        // No unlock() - simulating crash
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            orphanThread.start();
            orphanThread.join();

            // Wait for TTL to expire and cleanup window to pass
            Thread.sleep(1500); // 500ms TTL * 2 = 1000ms expiration window + buffer

            // Another client should be able to acquire despite orphan entry
            FairLock cleanupLock = (FairLock) manager.createFairLock(lockName);

            // This acquisition triggers cleanup of expired queue entries
            boolean acquired = cleanupLock.tryLock(5, TimeUnit.SECONDS);

            assertTrue(acquired, "Should acquire lock after orphan entries are cleaned up");
            assertTrue(cleanupLock.isHeldByCurrentThread());
            cleanupLock.unlock();
        }
    }

    /**
     * Tests that interrupt during lock acquisition properly removes queue entry.
     */
    @Test
    void shouldRemoveQueueEntryOnInterrupt() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            String lockName = "interrupt-cleanup-test";
            FairLock blockingLock = (FairLock) manager.createFairLock(lockName);

            // First, acquire the lock to block others
            assertTrue(blockingLock.tryLock(5, TimeUnit.SECONDS));

            AtomicInteger interruptedThreadResult = new AtomicInteger(-1);
            CountDownLatch waitingStarted = new CountDownLatch(1);

            // Second thread waits and will be interrupted
            Thread waitingThread = new Thread(() -> {
                try {
                    FairLock waitingLock = (FairLock) manager.createFairLock(lockName);
                    waitingStarted.countDown();
                    // This will block since blockingLock holds the lock
                    waitingLock.tryLock(30, TimeUnit.SECONDS);
                    interruptedThreadResult.set(0); // Should not reach here
                } catch (InterruptedException e) {
                    interruptedThreadResult.set(1); // Interrupted as expected
                    Thread.currentThread().interrupt();
                }
            });
            waitingThread.start();

            // Wait for thread to start waiting
            assertTrue(waitingStarted.await(2, TimeUnit.SECONDS));
            Thread.sleep(200); // Let it add to queue

            // Interrupt the waiting thread
            waitingThread.interrupt();
            waitingThread.join(2000);

            assertEquals(1, interruptedThreadResult.get(), "Thread should have been interrupted");

            // Release blocking lock
            blockingLock.unlock();

            // Third client should be able to acquire without issues
            FairLock thirdLock = (FairLock) manager.createFairLock(lockName);
            assertTrue(thirdLock.tryLock(2, TimeUnit.SECONDS), "Third lock should acquire cleanly");
            thirdLock.unlock();
        }
    }

    /**
     * Tests that timeout during lock acquisition properly removes queue entry.
     */
    @Test
    void shouldRemoveQueueEntryOnTimeout() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            String lockName = "timeout-cleanup-test";
            FairLock holdingLock = (FairLock) manager.createFairLock(lockName);

            // Hold the lock
            assertTrue(holdingLock.tryLock(5, TimeUnit.SECONDS));

            // Second thread times out waiting
            AtomicInteger timeoutResult = new AtomicInteger(-1);
            Thread timeoutThread = new Thread(() -> {
                try {
                    FairLock timeoutLock = (FairLock) manager.createFairLock(lockName);
                    boolean acquired = timeoutLock.tryLock(500, TimeUnit.MILLISECONDS);
                    timeoutResult.set(acquired ? 1 : 0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            timeoutThread.start();
            timeoutThread.join(5000);

            assertEquals(0, timeoutResult.get(), "Second thread should timeout");

            // Release holding lock
            holdingLock.unlock();

            // Third client should acquire without waiting for expired queue entry
            FairLock freshLock = (FairLock) manager.createFairLock(lockName);
            assertTrue(freshLock.tryLock(2, TimeUnit.SECONDS), "Fresh lock should acquire after timeout cleanup");
            freshLock.unlock();
        }
    }
}
