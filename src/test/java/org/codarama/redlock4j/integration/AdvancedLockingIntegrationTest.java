/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.integration;

import org.codarama.redlock4j.*;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests verifying all locking primitives work together in a single test run. Detailed tests for each primitive
 * are in their respective integration test classes:
 * <ul>
 * <li>{@link FairLockIntegrationTest}</li>
 * <li>{@link MultiLockIntegrationTest}</li>
 * <li>{@link RedlockReadWriteLockIntegrationTest}</li>
 * <li>{@link RedlockSemaphoreIntegrationTest}</li>
 * <li>{@link RedlockCountDownLatchIntegrationTest}</li>
 * </ul>
 */
@Tag("integration")
@Testcontainers
public class AdvancedLockingIntegrationTest {

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
                .addRedisNode("localhost", redis3.getMappedPort(6379)).defaultLockTimeout(Duration.ofSeconds(10))
                .retryDelay(Duration.ofMillis(100)).maxRetryAttempts(5).lockAcquisitionTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Single smoke test verifying all primitives can be created and used within one RedlockManager session.
     */
    @Test
    public void allPrimitivesWorkTogether() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            // Redlock (basic)
            Lock basicLock = manager.createLock("smoke-basic");
            assertTrue(basicLock.tryLock(), "Basic lock");
            basicLock.unlock();

            // FairLock
            Lock fairLock = manager.createFairLock("smoke-fair");
            assertTrue(fairLock.tryLock(), "Fair lock");
            fairLock.unlock();

            // MultiLock
            Lock multiLock = manager.createMultiLock(Arrays.asList("smoke-m1", "smoke-m2"));
            assertTrue(multiLock.tryLock(), "Multi lock");
            multiLock.unlock();

            // ReadWriteLock
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("smoke-rw");
            assertTrue(rwLock.readLock().tryLock(), "Read lock");
            rwLock.readLock().unlock();
            assertTrue(rwLock.writeLock().tryLock(), "Write lock");
            rwLock.writeLock().unlock();

            // Semaphore
            RedlockSemaphore semaphore = manager.createSemaphore("smoke-sem", 3);
            semaphore.acquire();
            semaphore.release();

            // CountDownLatch
            RedlockCountDownLatch latch = manager.createCountDownLatch("smoke-latch", 2);
            assertEquals(2, latch.getCount());
            latch.countDown();
            assertEquals(1, latch.getCount());
        }
    }

    /**
     * Verifies Lettuce driver works with all primitives.
     */
    @Test
    public void allPrimitivesWorkWithLettuce() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withLettuce(testConfiguration)) {
            // Basic lock
            Lock basicLock = manager.createLock("lettuce-basic");
            assertTrue(basicLock.tryLock());
            basicLock.unlock();

            // FairLock
            Lock fairLock = manager.createFairLock("lettuce-fair");
            assertTrue(fairLock.tryLock());
            fairLock.unlock();

            // MultiLock
            Lock multiLock = manager.createMultiLock(Arrays.asList("lettuce-m1", "lettuce-m2"));
            assertTrue(multiLock.tryLock());
            multiLock.unlock();

            // ReadWriteLock
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("lettuce-rw");
            assertTrue(rwLock.readLock().tryLock());
            rwLock.readLock().unlock();

            // Semaphore
            RedlockSemaphore semaphore = manager.createSemaphore("lettuce-sem", 2);
            semaphore.acquire();
            semaphore.release();

            // CountDownLatch
            RedlockCountDownLatch latch = manager.createCountDownLatch("lettuce-latch", 1);
            assertEquals(1, latch.getCount());
        }
    }
}
