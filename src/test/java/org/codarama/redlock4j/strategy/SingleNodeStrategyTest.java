/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.LockResult;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.driver.RedisDriverException;
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
 * Unit tests for SingleNodeStrategy.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class SingleNodeStrategyTest {

    @Mock
    private RedisDriver mockDriver;

    private SingleNodeStrategy strategy;

    @BeforeEach
    void setUp() {
        lenient().when(mockDriver.getIdentifier()).thenReturn("redis://localhost:6379");
        strategy = new SingleNodeStrategy(mockDriver);
    }

    @Test
    void testConstructorRejectsNullDriver() {
        assertThrows(IllegalArgumentException.class, () -> new SingleNodeStrategy(null));
    }

    @Test
    void testIsSingleNodeMode() {
        assertTrue(strategy.isSingleNodeMode());
    }

    @Test
    void testAcquireLockSuccess() throws Exception {
        when(mockDriver.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);

        LockResult result = strategy.acquireLock("test-key", "test-value", 30000);

        assertTrue(result.isAcquired());
        assertEquals("test-value", result.getLockValue());
        assertEquals(1, result.getSuccessfulNodes());
        assertEquals(1, result.getTotalNodes());
        assertTrue(result.getValidityTimeMs() > 0);
        assertTrue(result.getValidityTimeMs() <= 30000);

        verify(mockDriver).setIfNotExists("test-key", "test-value", 30000);
    }

    @Test
    void testAcquireLockFailure() throws Exception {
        when(mockDriver.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);

        LockResult result = strategy.acquireLock("test-key", "test-value", 30000);

        assertFalse(result.isAcquired());
    }

    @Test
    void testAcquireLockWithException() throws Exception {
        when(mockDriver.setIfNotExists(anyString(), anyString(), anyLong()))
                .thenThrow(new RedisDriverException("Connection failed"));

        LockResult result = strategy.acquireLock("test-key", "test-value", 30000);

        assertFalse(result.isAcquired());
    }

    @Test
    void testReleaseLock() throws Exception {
        strategy.releaseLock("test-key", "test-value");

        verify(mockDriver).deleteIfValueMatches("test-key", "test-value");
    }

    @Test
    void testReleaseLockWithException() throws Exception {
        doThrow(new RedisDriverException("Connection failed")).when(mockDriver).deleteIfValueMatches(anyString(),
                anyString());

        // Should not throw - just log warning
        assertDoesNotThrow(() -> strategy.releaseLock("test-key", "test-value"));
    }

    @Test
    void testExtendLockSuccess() throws Exception {
        when(mockDriver.setIfValueMatches(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

        boolean result = strategy.extendLock("test-key", "test-value", 60000);

        assertTrue(result);
        verify(mockDriver).setIfValueMatches("test-key", "test-value", "test-value", 60000);
    }

    @Test
    void testExtendLockFailure() throws Exception {
        when(mockDriver.setIfValueMatches(anyString(), anyString(), anyString(), anyLong())).thenReturn(false);

        boolean result = strategy.extendLock("test-key", "test-value", 60000);

        assertFalse(result);
    }

    @Test
    void testCalculateValidityTimeNoClockDrift() {
        // SingleNodeStrategy should NOT apply clock drift compensation
        long validity = strategy.calculateValidityTime(30000, 100);

        // Should be exactly timeout - elapsed (no drift subtraction)
        assertEquals(29900, validity);
    }

    @Test
    void testExecuteOnNodesSuccess() {
        int count = strategy.executeOnNodes(driver -> true);
        assertEquals(1, count);
    }

    @Test
    void testExecuteOnNodesFailure() {
        int count = strategy.executeOnNodes(driver -> false);
        assertEquals(0, count);
    }

    @Test
    void testExecuteOnNodesWithException() {
        int count = strategy.executeOnNodes(driver -> {
            throw new RuntimeException("Test exception");
        });
        assertEquals(0, count);
    }

    @Test
    void testIsSuccessful() {
        assertTrue(strategy.isSuccessful(1));
        assertFalse(strategy.isSuccessful(0));
    }
}
