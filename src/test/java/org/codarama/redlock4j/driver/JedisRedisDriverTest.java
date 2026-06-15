/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.driver;

import static org.junit.jupiter.api.Assertions.*;

import org.codarama.redlock4j.configuration.RedisNodeConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * Unit tests for JedisRedisDriver.
 *
 * <p>
 * Note: The driver now uses RedisClient instead of JedisPool. These tests verify basic driver creation and
 * configuration without requiring a live Redis connection for most tests.
 * </p>
 */
@Tag("unit")
public class JedisRedisDriverTest {

    private RedisNodeConfiguration testConfig;
    private JedisRedisDriver driver;

    @BeforeEach
    void setUp() {
        testConfig = RedisNodeConfiguration.builder().host("localhost").port(6379).connectionTimeoutMs(5000)
                .socketTimeoutMs(5000).build();
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.close();
        }
    }

    // ========== Connection Timeout Tests ==========

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testIsConnectedReturnsFalseForUnreachableHost() {
        // Use a non-routable IP address that will timeout quickly
        RedisNodeConfiguration unreachableConfig = RedisNodeConfiguration.builder().host("192.0.2.1") // TEST-NET-1 -
                                                                                                      // guaranteed to
                                                                                                      // be unreachable
                .port(6379).connectionTimeoutMs(500).socketTimeoutMs(500).build();

        driver = new JedisRedisDriver(unreachableConfig);

        // isConnected should return false, not throw
        assertFalse(driver.isConnected());
    }

    /**
     * Verifies that all Redis operations throw RedisDriverException when connection fails. Uses a single test with
     * multiple assertions to avoid spawning many slow connection-timeout tests.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void allOperationsThrowOnConnectionFailure() {
        RedisNodeConfiguration unreachableConfig = RedisNodeConfiguration.builder().host("192.0.2.1") // TEST-NET-1 -
                                                                                                      // guaranteed to
                                                                                                      // be unreachable
                .port(6379).connectionTimeoutMs(500).socketTimeoutMs(500).build();

        driver = new JedisRedisDriver(unreachableConfig);

        // setIfNotExists
        RedisDriverException ex1 = assertThrows(RedisDriverException.class,
                () -> driver.setIfNotExists("test-key", "test-value", 10000));
        assertTrue(ex1.getMessage().contains("Failed to execute"));

        // deleteIfValueMatches
        RedisDriverException ex2 = assertThrows(RedisDriverException.class,
                () -> driver.deleteIfValueMatches("test-key", "test-value"));
        assertTrue(ex2.getMessage().contains("Failed to execute"));

        // get
        RedisDriverException ex3 = assertThrows(RedisDriverException.class, () -> driver.get("test-key"));
        assertTrue(ex3.getMessage().contains("Failed to execute"));

        // incr
        RedisDriverException ex4 = assertThrows(RedisDriverException.class, () -> driver.incr("counter"));
        assertTrue(ex4.getMessage().contains("Failed to execute"));

        // decr
        RedisDriverException ex5 = assertThrows(RedisDriverException.class, () -> driver.decr("counter"));
        assertTrue(ex5.getMessage().contains("Failed to execute"));

        // setex
        RedisDriverException ex6 = assertThrows(RedisDriverException.class,
                () -> driver.setex("test-key", "test-value", 10000));
        assertTrue(ex6.getMessage().contains("Failed to execute"));

        // del
        RedisDriverException ex7 = assertThrows(RedisDriverException.class, () -> driver.del("test-key"));
        assertTrue(ex7.getMessage().contains("Failed to execute"));

        // zAdd
        RedisDriverException ex8 = assertThrows(RedisDriverException.class,
                () -> driver.zAdd("sorted-set", 1.0, "member"));
        assertTrue(ex8.getMessage().contains("Failed to execute"));

        // configGet
        RedisDriverException ex9 = assertThrows(RedisDriverException.class,
                () -> driver.configGet("notify-keyspace-events"));
        assertTrue(ex9.getMessage().contains("Failed to execute"));
    }

    // ========== Basic Driver Tests ==========

    @Test
    public void testDriverCreationWithBasicConfig() {
        driver = new JedisRedisDriver(testConfig);

        assertNotNull(driver);
        assertEquals("redis://localhost:6379", driver.getIdentifier());
    }

    @Test
    public void testDriverCreationWithPassword() {
        RedisNodeConfiguration configWithPassword = RedisNodeConfiguration.builder().host("localhost").port(6379)
                .password("testpass").connectionTimeoutMs(5000).socketTimeoutMs(5000).build();

        driver = new JedisRedisDriver(configWithPassword);

        assertNotNull(driver);
        assertEquals("redis://localhost:6379", driver.getIdentifier());
    }

    @Test
    public void testDriverCreationWithDatabase() {
        RedisNodeConfiguration configWithDb = RedisNodeConfiguration.builder().host("localhost").port(6379).database(2)
                .connectionTimeoutMs(5000).socketTimeoutMs(5000).build();

        driver = new JedisRedisDriver(configWithDb);

        assertNotNull(driver);
        assertEquals("redis://localhost:6379", driver.getIdentifier());
    }

    @Test
    public void testGetIdentifierFormat() {
        driver = new JedisRedisDriver(testConfig);
        assertEquals("redis://localhost:6379", driver.getIdentifier());
    }

    @Test
    public void testDriverCreationWithNullConfig() {
        assertThrows(NullPointerException.class, () -> new JedisRedisDriver(null));
    }

    @Test
    public void testCloseDoesNotThrowException() {
        driver = new JedisRedisDriver(testConfig);
        assertDoesNotThrow(() -> driver.close());
    }

    @Test
    public void testMultipleCloseCallsAreIdempotent() {
        driver = new JedisRedisDriver(testConfig);

        assertDoesNotThrow(() -> {
            driver.close();
            driver.close();
            driver.close();
        });
    }

    @Test
    public void testIdentifierWithDifferentPorts() {
        RedisNodeConfiguration config1 = RedisNodeConfiguration.builder().host("redis1.example.com").port(6380)
                .connectionTimeoutMs(5000).socketTimeoutMs(5000).build();

        driver = new JedisRedisDriver(config1);
        assertEquals("redis://redis1.example.com:6380", driver.getIdentifier());
    }

    @Test
    public void testIdentifierWithSpecialCharacters() {
        RedisNodeConfiguration config = RedisNodeConfiguration.builder().host("my-redis-host.cluster.local").port(16379)
                .connectionTimeoutMs(5000).socketTimeoutMs(5000).build();

        driver = new JedisRedisDriver(config);
        assertEquals("redis://my-redis-host.cluster.local:16379", driver.getIdentifier());
    }

    @Test
    public void testDriverCreationWithAllOptions() {
        RedisNodeConfiguration fullConfig = RedisNodeConfiguration.builder().host("redis.example.com").port(6380)
                .password("secret").database(5).connectionTimeoutMs(10000).socketTimeoutMs(10000).build();

        driver = new JedisRedisDriver(fullConfig);
        assertNotNull(driver);
        assertEquals("redis://redis.example.com:6380", driver.getIdentifier());
    }

    @Test
    public void testDriverCreationWithDefaultTimeouts() {
        RedisNodeConfiguration minimalConfig = RedisNodeConfiguration.builder().host("localhost").port(6379).build();

        driver = new JedisRedisDriver(minimalConfig);
        assertNotNull(driver);
        assertEquals("redis://localhost:6379", driver.getIdentifier());
    }

}
