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
 * Integration tests against Redis 8.x, where native CAS/CAD commands ({@code DELEX}/{@code SET IFEQ}) are available.
 *
 * <p>
 * On Redis 8.x the drivers select the NATIVE strategy, so acquire + extend + release exercises
 * {@code setIfValueMatchesNative} and {@code deleteIfValueMatchesNative} — paths that the Redis 7 suites (which fall
 * back to Lua scripts for Jedis) cannot reach.
 * </p>
 */
@Tag("integration")
@Testcontainers
public class NativeCasCadIntegrationTest {

    @Container
    static GenericContainer<?> redis1 = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> redis2 = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> redis3 = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
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
    public void testJedisNativeCasCadFullCycle() {
        try (RedlockManager manager = RedlockManager.withJedis(configuration)) {
            Redlock lock = manager.createLock("native-jedis");

            assertTrue(lock.tryLock(), "Should acquire the lock (SET NX)");
            boolean extended = lock.extend(10000); // native SET IFEQ
            assertTrue(extended, "Native CAS extend should succeed");
            assertTrue(lock.isHeldByCurrentThread());

            lock.unlock(); // native DELEX
            assertFalse(lock.isHeldByCurrentThread(), "Lock should be released after unlock");

            // Lock is free again: a fresh acquisition must succeed.
            assertTrue(lock.tryLock(), "Should re-acquire after release");
            lock.unlock();
        }
    }

    @Test
    public void testLettuceNativeCasCadFullCycle() {
        try (RedlockManager manager = RedlockManager.withLettuce(configuration)) {
            Redlock lock = manager.createLock("native-lettuce");

            assertTrue(lock.tryLock(), "Should acquire the lock (SET NX)");
            boolean extended = lock.extend(10000); // native SET IFEQ
            assertTrue(extended, "Native CAS extend should succeed");
            assertTrue(lock.isHeldByCurrentThread());

            lock.unlock(); // native DELEX
            assertFalse(lock.isHeldByCurrentThread(), "Lock should be released after unlock");

            assertTrue(lock.tryLock(), "Should re-acquire after release");
            lock.unlock();
        }
    }
}
