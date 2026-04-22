/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.driver;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.codarama.redlock4j.configuration.RedisNodeConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LettuceRedisDriver using Mockito mocks. These tests do not require a working Redis server.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class LettuceRedisDriverTest {

    @Mock
    private RedisClient mockRedisClient;

    @Mock
    private StatefulRedisConnection<String, String> mockConnection;

    @Mock
    private RedisCommands<String, String> mockCommands;

    private RedisNodeConfiguration testConfig;
    private LettuceRedisDriver driver;

    @BeforeEach
    void setUp() {
        testConfig = RedisNodeConfiguration.builder().host("localhost").port(6379).connectionTimeoutMs(5000)
                .socketTimeoutMs(5000).build();
    }

    @Test
    public void testDriverCreationWithBasicConfig() {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        assertNotNull(driver);
        assertEquals("redis://localhost:6379", driver.getIdentifier());
    }

    @Test
    public void testDriverCreationWithPassword() {
        RedisNodeConfiguration configWithPassword = RedisNodeConfiguration.builder().host("localhost").port(6379)
                .password("testpass").connectionTimeoutMs(5000).socketTimeoutMs(5000).build();

        driver = new LettuceRedisDriver(configWithPassword, mockRedisClient, mockConnection, mockCommands);

        assertNotNull(driver);
        assertEquals("redis://localhost:6379", driver.getIdentifier());
    }

    @Test
    public void testDriverCreationWithDatabase() {
        RedisNodeConfiguration configWithDb = RedisNodeConfiguration.builder().host("localhost").port(6379).database(2)
                .connectionTimeoutMs(5000).socketTimeoutMs(5000).build();

        driver = new LettuceRedisDriver(configWithDb, mockRedisClient, mockConnection, mockCommands);

        assertNotNull(driver);
        assertEquals("redis://localhost:6379", driver.getIdentifier());
    }

    @Test
    public void testGetIdentifierFormat() {
        // Test identifier format without creating multiple drivers
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);
        assertEquals("redis://localhost:6379", driver.getIdentifier());
        driver.close();
    }

    @Test
    public void testDriverCreationWithNullConfig() {
        assertThrows(NullPointerException.class,
                () -> new LettuceRedisDriver(null, mockRedisClient, mockConnection, mockCommands));
    }

    @Test
    public void testCloseDoesNotThrowException() {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        // Should not throw exception even if connection fails
        assertDoesNotThrow(() -> driver.close());

        // Verify close was called on mocked dependencies
        verify(mockConnection).close();
        verify(mockRedisClient).shutdown();
    }

    @Test
    public void testMultipleCloseCallsAreIdempotent() {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        // Multiple close calls should not throw exceptions
        assertDoesNotThrow(() -> {
            driver.close();
            driver.close();
            driver.close();
        });

        // Verify close was called multiple times
        verify(mockConnection, times(3)).close();
        verify(mockRedisClient, times(3)).shutdown();
    }

    @Test
    public void testSetIfNotExistsSuccess() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.set(eq("test-key"), eq("test-value"), any(SetArgs.class))).thenReturn("OK");

        boolean result = driver.setIfNotExists("test-key", "test-value", 10000);

        assertTrue(result);
        verify(mockCommands).set(eq("test-key"), eq("test-value"), any(SetArgs.class));
    }

    @Test
    public void testSetIfNotExistsFailure() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.set(eq("test-key"), eq("test-value"), any(SetArgs.class))).thenReturn(null);

        boolean result = driver.setIfNotExists("test-key", "test-value", 10000);

        assertFalse(result);
        verify(mockCommands).set(eq("test-key"), eq("test-value"), any(SetArgs.class));
    }

    @Test
    public void testSetIfNotExistsException() {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.set(eq("test-key"), eq("test-value"), any(SetArgs.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        RedisDriverException exception = assertThrows(RedisDriverException.class,
                () -> driver.setIfNotExists("test-key", "test-value", 10000));

        assertTrue(exception.getMessage().contains("Failed to execute SET NX PX command"));
        assertTrue(exception.getMessage().contains("redis://localhost:6379"));
    }

    @Test
    public void testDeleteIfValueMatchesSuccess() throws RedisDriverException {
        // Mock dispatch() for CAD detection (returns success, indicating native support)
        when(mockCommands.dispatch(any(), any(), any())).thenReturn(1L);

        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        boolean result = driver.deleteIfValueMatches("test-key", "test-value");

        assertTrue(result);
        // Verify dispatch was called (once for detection, once for actual delete)
        verify(mockCommands, atLeast(2)).dispatch(any(), any(), any());
    }

    @Test
    public void testDeleteIfValueMatchesFailure() throws RedisDriverException {
        // Mock dispatch() for CAD detection (returns success, indicating native support)
        // Then return 0 for the actual delete (key not deleted)
        when(mockCommands.dispatch(any(), any(), any())).thenReturn(1L, 0L);

        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        boolean result = driver.deleteIfValueMatches("test-key", "test-value");

        assertFalse(result);
        verify(mockCommands, atLeast(2)).dispatch(any(), any(), any());
    }

    @Test
    public void testDeleteIfValueMatchesException() {
        // Mock dispatch() for CAD detection (returns success, indicating native support)
        // Then throw exception for the actual delete
        when(mockCommands.dispatch(any(), any(), any())).thenReturn(1L)
                .thenThrow(new RuntimeException("DELEX execution failed"));

        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        RedisDriverException exception = assertThrows(RedisDriverException.class,
                () -> driver.deleteIfValueMatches("test-key", "test-value"));

        assertTrue(exception.getMessage().contains("Failed to execute DELEX command"));
        assertTrue(exception.getMessage().contains("redis://localhost:6379"));
    }

    @Test
    public void testIsConnectedSuccess() {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.ping()).thenReturn("PONG");

        boolean result = driver.isConnected();

        assertTrue(result);
        verify(mockCommands).ping();
    }

    @Test
    public void testIsConnectedFailure() {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.ping()).thenThrow(new RuntimeException("Connection failed"));

        boolean result = driver.isConnected();

        assertFalse(result);
        verify(mockCommands).ping();
    }

    @Test
    public void testGetIdentifierWithDifferentConfigurations() {
        RedisNodeConfiguration config1 = RedisNodeConfiguration.builder().host("redis1.example.com").port(6379).build();

        RedisNodeConfiguration config2 = RedisNodeConfiguration.builder().host("redis2.example.com").port(6380).build();

        LettuceRedisDriver driver1 = new LettuceRedisDriver(config1, mockRedisClient, mockConnection, mockCommands);
        LettuceRedisDriver driver2 = new LettuceRedisDriver(config2, mockRedisClient, mockConnection, mockCommands);

        assertEquals("redis://redis1.example.com:6379", driver1.getIdentifier());
        assertEquals("redis://redis2.example.com:6380", driver2.getIdentifier());

        driver1.close();
        driver2.close();
    }

    @Test
    public void testZAddSuccess() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.zadd(eq("test-key"), eq(1.0), eq("member1"))).thenReturn(1L);

        boolean result = driver.zAdd("test-key", 1.0, "member1");

        assertTrue(result);
        verify(mockCommands).zadd("test-key", 1.0, "member1");
    }

    @Test
    public void testZAddFailure() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.zadd(eq("test-key"), eq(1.0), eq("member1"))).thenReturn(0L);

        boolean result = driver.zAdd("test-key", 1.0, "member1");

        assertFalse(result);
    }

    @Test
    public void testZRemSuccess() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.zrem(eq("test-key"), eq("member1"))).thenReturn(1L);

        boolean result = driver.zRem("test-key", "member1");

        assertTrue(result);
    }

    @Test
    public void testZRange() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.zrange(eq("test-key"), eq(0L), eq(10L))).thenReturn(java.util.Arrays.asList("a", "b", "c"));

        java.util.List<String> result = driver.zRange("test-key", 0, 10);

        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
    }

    @Test
    public void testZRangeReturnsEmptyListWhenNull() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.zrange(eq("test-key"), eq(0L), eq(10L))).thenReturn(null);

        java.util.List<String> result = driver.zRange("test-key", 0, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testIncr() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.incr(eq("counter"))).thenReturn(42L);

        long result = driver.incr("counter");

        assertEquals(42, result);
    }

    @Test
    public void testDecr() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.decr(eq("counter"))).thenReturn(41L);

        long result = driver.decr("counter");

        assertEquals(41, result);
    }

    @Test
    public void testGet() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.get(eq("test-key"))).thenReturn("test-value");

        String result = driver.get("test-key");

        assertEquals("test-value", result);
    }

    @Test
    public void testSetex() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        driver.setex("test-key", "test-value", 5000);

        verify(mockCommands).psetex(eq("test-key"), eq(5000L), eq("test-value"));
    }

    @Test
    public void testDel() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.del(eq("key1"), eq("key2"))).thenReturn(2L);

        long result = driver.del("key1", "key2");

        assertEquals(2, result);
    }

    @Test
    public void testPublish() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.publish(eq("channel"), eq("message"))).thenReturn(5L);

        long result = driver.publish("channel", "message");

        assertEquals(5, result);
    }

    @Test
    public void testConfigGet() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        java.util.Map<String, String> configMap = new java.util.HashMap<>();
        configMap.put("maxmemory", "1gb");
        when(mockCommands.configGet(eq("maxmemory"))).thenReturn(configMap);

        String result = driver.configGet("maxmemory");

        assertEquals("1gb", result);
    }

    @Test
    public void testConfigGetReturnsNullWhenEmpty() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        when(mockCommands.configGet(eq("nonexistent"))).thenReturn(new java.util.HashMap<>());

        String result = driver.configGet("nonexistent");

        assertNull(result);
    }

    @Test
    public void testConfigSet() throws RedisDriverException {
        driver = new LettuceRedisDriver(testConfig, mockRedisClient, mockConnection, mockCommands);

        driver.configSet("maxmemory", "2gb");

        verify(mockCommands).configSet(eq("maxmemory"), eq("2gb"));
    }

}
