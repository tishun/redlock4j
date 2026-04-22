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
