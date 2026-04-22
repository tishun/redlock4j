/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.integration;

import org.codarama.redlock4j.*;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for single-node mode. Verifies that single-node optimizations work correctly with a real Redis
 * instance.
 */
@Tag("integration")
@Testcontainers
public class SingleNodeModeIntegrationTest {

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private RedlockManager manager;
    private RedlockConfiguration config;

    @BeforeEach
    void setUp() {
        // Single node configuration - no singleNodeMode() call needed
        config = RedlockConfiguration.builder().addRedisNode("localhost", redis.getMappedPort(6379))
                .defaultLockTimeout(Duration.ofSeconds(10)).lockAcquisitionTimeout(Duration.ofSeconds(5))
                .retryDelay(Duration.ofMillis(50)).build();

        manager = RedlockManager.withLettuce(config);
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    void testSingleNodeModeIsAutoDetected() {
        assertTrue(config.isSingleNodeMode());
        assertEquals(1, config.getQuorum());
    }

    @Test
    void testBasicLockAcquireAndRelease() {
        Redlock lock = manager.createLock("test-single-node-lock");

        assertTrue(lock.tryLock());
        lock.unlock();

        // Should be able to acquire again after release
        assertTrue(lock.tryLock());
        lock.unlock();
    }

    @Test
    void testLockExclusivity() throws InterruptedException {
        Redlock lock = manager.createLock("test-exclusivity");
        AtomicInteger concurrentHolders = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(10);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (lock.tryLock(Duration.ofSeconds(5))) {
                        try {
                            int current = concurrentHolders.incrementAndGet();
                            maxConcurrent.updateAndGet(max -> Math.max(max, current));
                            Thread.sleep(10); // Hold lock briefly
                        } finally {
                            concurrentHolders.decrementAndGet();
                            lock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Only one thread should hold lock at a time
        assertEquals(1, maxConcurrent.get());
    }

    @Test
    void testLockExtension() throws InterruptedException {
        Redlock lock = manager.createLock("test-extension");

        assertTrue(lock.tryLock());

        // Extend the lock
        assertTrue(lock.extend(5000));

        lock.unlock();
    }

    @Test
    void testFairLockInSingleNodeMode() throws InterruptedException {
        java.util.concurrent.locks.Lock fairLock = manager.createFairLock("test-fair-single-node");

        assertTrue(fairLock.tryLock(5, TimeUnit.SECONDS));
        fairLock.unlock();
    }

    @Test
    void testMultiLockInSingleNodeMode() throws InterruptedException {
        java.util.concurrent.locks.Lock multiLock = manager
                .createMultiLock(java.util.Arrays.asList("key1", "key2", "key3"));

        assertTrue(multiLock.tryLock(5, TimeUnit.SECONDS));
        multiLock.unlock();
    }

    @Test
    void testSemaphoreInSingleNodeMode() throws InterruptedException {
        RedlockSemaphore semaphore = manager.createSemaphore("test-sem", 3);

        assertTrue(semaphore.tryAcquire(2, Duration.ofSeconds(5)));
        semaphore.release(2);
    }

    @Test
    void testReadWriteLockInSingleNodeMode() {
        RedlockReadWriteLock rwLock = manager.createReadWriteLock("test-rw");

        // Multiple readers should work
        assertTrue(rwLock.readLock().tryLock());
        rwLock.readLock().unlock();

        // Writer should work
        assertTrue(rwLock.writeLock().tryLock());
        rwLock.writeLock().unlock();
    }
}
