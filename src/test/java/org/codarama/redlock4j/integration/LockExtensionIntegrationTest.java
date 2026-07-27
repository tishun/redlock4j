/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.integration;

import org.codarama.redlock4j.Redlock;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for lock extension against real Redis, exercising the driver-level CAS ({@code setIfValueMatches})
 * paths that unit tests with mocked drivers cannot reach.
 */
@Tag("integration")
@Testcontainers
public class LockExtensionIntegrationTest {

    @Container
    static GenericContainer<?> redis1 = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> redis2 = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> redis3 = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static RedlockConfiguration configuration;

    @BeforeAll
    static void setUp() {
        redis1.start();
        redis2.start();
        redis3.start();

        configuration = RedlockConfiguration.builder().addRedisNode("localhost", redis1.getMappedPort(6379))
                .addRedisNode("localhost", redis2.getMappedPort(6379))
                .addRedisNode("localhost", redis3.getMappedPort(6379)).defaultLockTimeout(Duration.ofSeconds(10))
                .retryDelay(Duration.ofMillis(100)).maxRetryAttempts(3).lockAcquisitionTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    public void testJedisLockExtension() {
        try (RedlockManager manager = RedlockManager.withJedis(configuration)) {
            Redlock lock = manager.createLock("extend-jedis");

            assertTrue(lock.tryLock(), "Should acquire the lock");
            try {
                Duration before = lock.getRemainingValidityTime();

                boolean extended = lock.extend(10000);

                assertTrue(extended, "Extension should succeed on a held lock");
                assertTrue(lock.isHeldByCurrentThread());
                assertTrue(lock.getRemainingValidityTime().compareTo(before) > 0,
                        "Validity should increase after extension");
            } finally {
                lock.unlock();
            }
        }
    }

    @Test
    public void testLettuceLockExtension() {
        try (RedlockManager manager = RedlockManager.withLettuce(configuration)) {
            Redlock lock = manager.createLock("extend-lettuce");

            assertTrue(lock.tryLock(), "Should acquire the lock");
            try {
                Duration before = lock.getRemainingValidityTime();

                boolean extended = lock.extend(10000);

                assertTrue(extended, "Extension should succeed on a held lock");
                assertTrue(lock.isHeldByCurrentThread());
                assertTrue(lock.getRemainingValidityTime().compareTo(before) > 0,
                        "Validity should increase after extension");
            } finally {
                lock.unlock();
            }
        }
    }
}
