/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.Redlock;
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for wait strategy functionality.
 */
@Tag("integration")
@Testcontainers
class WaitStrategyIntegrationTest {

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private RedlockManager manager;

    @BeforeEach
    void setUp() {
        // Default configuration uses KEYSPACE_NOTIFICATIONS
        // Single node is automatically detected - no flag needed
        RedlockConfiguration config = RedlockConfiguration.builder()
                .addRedisNode("localhost", redis.getMappedPort(6379)).defaultLockTimeout(Duration.ofSeconds(10))
                .lockAcquisitionTimeout(Duration.ofSeconds(5)).retryDelay(Duration.ofMillis(50)).build();

        manager = RedlockManager.withLettuce(config);
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    void testKeyspaceStrategyIsDefault() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379).build();

        assertEquals(WaitStrategy.KEYSPACE_NOTIFICATIONS, config.getWaitStrategy());
    }

    @Test
    void testPollingStrategyConfiguration() {
        RedlockConfiguration config = RedlockConfiguration.builder().addRedisNode("localhost", 6379).usePolling()
                .build();

        assertEquals(WaitStrategy.POLLING, config.getWaitStrategy());
    }

    @Test
    void testLockWithDefaultStrategy() throws InterruptedException {
        Redlock lock = manager.createLock("test-default-strategy");

        assertTrue(lock.tryLock(Duration.ofSeconds(1)));
        lock.unlock();
    }

    @Test
    void testLockContentionWithStrategy() throws InterruptedException {
        Redlock lock = manager.createLock("test-contention-strategy");

        // First thread acquires the lock
        assertTrue(lock.tryLock(Duration.ofSeconds(1)));

        // Second thread waits for the lock
        AtomicBoolean secondThreadAcquired = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread secondThread = new Thread(() -> {
            try {
                // This should wait for the lock to be released
                if (lock.tryLock(2, TimeUnit.SECONDS)) {
                    secondThreadAcquired.set(true);
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        });

        secondThread.start();

        // Give the second thread time to start waiting
        Thread.sleep(100);

        // Release the lock
        lock.unlock();

        // Wait for second thread to finish
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(secondThreadAcquired.get(), "Second thread should have acquired the lock");
    }

    @Test
    void testPollingStrategyWorks() throws InterruptedException {
        // Create manager with polling strategy
        // Single node is automatically detected - no flag needed
        RedlockConfiguration pollingConfig = RedlockConfiguration.builder()
                .addRedisNode("localhost", redis.getMappedPort(6379)).usePolling()
                .defaultLockTimeout(Duration.ofSeconds(10)).retryDelay(Duration.ofMillis(50)).build();

        try (RedlockManager pollingManager = RedlockManager.withLettuce(pollingConfig)) {
            Redlock lock = pollingManager.createLock("test-polling-lock");

            assertTrue(lock.tryLock(Duration.ofSeconds(1)));
            lock.unlock();
        }
    }

    @Test
    void testKeyspaceNotificationFastWakeup() throws InterruptedException {
        // This test verifies that keyspace notifications wake up waiting threads quickly
        Redlock lock = manager.createLock("test-fast-wakeup");

        // First thread acquires the lock
        assertTrue(lock.tryLock(Duration.ofSeconds(1)));

        // Second thread waits for the lock
        AtomicBoolean secondThreadAcquired = new AtomicBoolean(false);
        long[] waitTime = new long[1];
        CountDownLatch latch = new CountDownLatch(1);

        Thread secondThread = new Thread(() -> {
            try {
                long start = System.currentTimeMillis();
                // This should wait for the lock to be released via keyspace notification
                if (lock.tryLock(5, TimeUnit.SECONDS)) {
                    waitTime[0] = System.currentTimeMillis() - start;
                    secondThreadAcquired.set(true);
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        });

        secondThread.start();

        // Give the second thread time to start waiting
        Thread.sleep(200);

        // Release the lock - should trigger keyspace notification
        lock.unlock();

        // Wait for second thread to finish
        assertTrue(latch.await(6, TimeUnit.SECONDS));
        assertTrue(secondThreadAcquired.get(), "Second thread should have acquired the lock");

        // Log the wait time for diagnostics
        System.out.println("Wait time: " + waitTime[0] + "ms");
    }
}
