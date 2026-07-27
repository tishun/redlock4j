/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j;

import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.driver.RedisDriverException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiLock using Mockito mocks.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class MultiLockTest {

    @Mock
    private RedisDriver mockDriver1;

    @Mock
    private RedisDriver mockDriver2;

    @Mock
    private RedisDriver mockDriver3;

    private RedlockConfiguration testConfig;
    private List<RedisDriver> drivers;

    @BeforeEach
    void setUp() {
        testConfig = RedlockConfiguration.builder().addRedisNode("localhost", 6379).addRedisNode("localhost", 6380)
                .addRedisNode("localhost", 6381).defaultLockTimeout(Duration.ofSeconds(30))
                .retryDelay(Duration.ofMillis(10)).maxRetryAttempts(3).lockAcquisitionTimeout(Duration.ofSeconds(10))
                .build();

        drivers = Arrays.asList(mockDriver1, mockDriver2, mockDriver3);

        lenient().when(mockDriver1.getIdentifier()).thenReturn("redis://localhost:6379");
        lenient().when(mockDriver2.getIdentifier()).thenReturn("redis://localhost:6380");
        lenient().when(mockDriver3.getIdentifier()).thenReturn("redis://localhost:6381");
    }

    // ========== Validation ==========

    @Test
    void shouldRejectNullKeyList() {
        assertThrows(IllegalArgumentException.class, () -> new MultiLock(null, drivers, testConfig, null));
    }

    @Test
    void shouldRejectEmptyKeyList() {
        assertThrows(IllegalArgumentException.class, () -> new MultiLock(Arrays.asList(), drivers, testConfig, null));
    }

    // ========== Acquisition ==========

    @Test
    void shouldAcquireAllLocksWhenQuorumSucceeds() throws RedisDriverException, InterruptedException {
        when(mockDriver1.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);

        MultiLock lock = new MultiLock(Arrays.asList("key1", "key2", "key3"), drivers, testConfig, null);
        boolean acquired = lock.tryLock(Duration.ofSeconds(1));

        assertTrue(acquired);

        // Verify all keys were locked on all drivers
        verify(mockDriver1, times(3)).setIfNotExists(anyString(), anyString(), eq(30000L));
        verify(mockDriver2, times(3)).setIfNotExists(anyString(), anyString(), eq(30000L));
    }

    @Test
    void shouldRollbackOnPartialFailure() throws RedisDriverException, InterruptedException {
        // First key succeeds, second fails on driver1
        when(mockDriver1.setIfNotExists(eq("key1"), anyString(), anyLong())).thenReturn(true);
        when(mockDriver1.setIfNotExists(eq("key2"), anyString(), anyLong())).thenReturn(false);
        // All fail on driver2 and driver3 for simplicity
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);

        MultiLock lock = new MultiLock(Arrays.asList("key1", "key2"), drivers, testConfig, null);
        boolean acquired = lock.tryLock(Duration.ofMillis(100));

        assertFalse(acquired);

        // Verify rollback was attempted for key1 on driver1
        verify(mockDriver1, atLeastOnce()).deleteIfValueMatches(eq("key1"), anyString());
    }

    // ========== Key Ordering ==========

    @Test
    void shouldSortKeysToPreventDeadlock() throws RedisDriverException, InterruptedException {
        when(mockDriver1.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);

        // Pass keys in reverse order
        MultiLock lock = new MultiLock(Arrays.asList("z", "a", "m"), drivers, testConfig, null);
        lock.tryLock(Duration.ofSeconds(1));

        // Verify keys are acquired in sorted order: a, m, z
        org.mockito.InOrder order = inOrder(mockDriver1);
        order.verify(mockDriver1).setIfNotExists(eq("a"), anyString(), anyLong());
        order.verify(mockDriver1).setIfNotExists(eq("m"), anyString(), anyLong());
        order.verify(mockDriver1).setIfNotExists(eq("z"), anyString(), anyLong());
    }

    @Test
    void shouldDeduplicateKeys() throws RedisDriverException, InterruptedException {
        when(mockDriver1.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(true);

        // Pass duplicate keys
        MultiLock lock = new MultiLock(Arrays.asList("key1", "key1", "key2"), drivers, testConfig, null);
        lock.tryLock(Duration.ofSeconds(1));

        // Should only lock 2 unique keys
        verify(mockDriver1, times(2)).setIfNotExists(anyString(), anyString(), anyLong());
    }

    // ========== Utility ==========

    @Test
    void shouldThrowOnNewCondition() {
        MultiLock lock = new MultiLock(Arrays.asList("key1"), drivers, testConfig, null);
        assertThrows(UnsupportedOperationException.class, lock::newCondition);
    }

    // ========== Blocking / Failure Paths ==========

    /**
     * Builds a configuration with a short acquisition timeout and retry delay so that failure paths complete quickly
     * without mutating the shared setUp config.
     */
    private RedlockConfiguration shortTimeoutConfig() {
        return RedlockConfiguration.builder().addRedisNode("localhost", 6379).addRedisNode("localhost", 6380)
                .addRedisNode("localhost", 6381).defaultLockTimeout(Duration.ofSeconds(30))
                .retryDelay(Duration.ofMillis(10)).maxRetryAttempts(3).lockAcquisitionTimeout(Duration.ofMillis(150))
                .build();
    }

    @Test
    void shouldReturnFalseWhenTryLockWithTimeoutNeverAcquires() throws RedisDriverException, InterruptedException {
        when(mockDriver1.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);

        MultiLock lock = new MultiLock(Arrays.asList("a", "b"), drivers, shortTimeoutConfig(), null);

        assertFalse(lock.tryLock(Duration.ofMillis(150)));
    }

    @Test
    void shouldReturnFalseWhenTryLockZeroTimeoutUnavailable() throws RedisDriverException {
        when(mockDriver1.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);

        MultiLock lock = new MultiLock(Arrays.asList("a", "b"), drivers, testConfig, null);

        assertFalse(lock.tryLock());
    }

    @Test
    void shouldThrowRedlockExceptionWhenLockFails() throws RedisDriverException {
        when(mockDriver1.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);

        MultiLock lock = new MultiLock(Arrays.asList("a", "b"), drivers, shortTimeoutConfig(), null);

        assertThrows(RedlockException.class, lock::lock);
    }

    @Test
    void shouldThrowRedlockExceptionWhenLockInterruptiblyFails() throws RedisDriverException {
        when(mockDriver1.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver2.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(mockDriver3.setIfNotExists(anyString(), anyString(), anyLong())).thenReturn(false);

        MultiLock lock = new MultiLock(Arrays.asList("a", "b"), drivers, shortTimeoutConfig(), null);

        assertThrows(RedlockException.class, lock::lockInterruptibly);
    }

    @Test
    void shouldThrowInterruptedExceptionWhenTryLockPreInterrupted() {
        // No setIfNotExists stubs: the interrupt check at the top of the retry loop fires before
        // any acquisition attempt, so the drivers are never invoked.
        MultiLock lock = new MultiLock(Arrays.asList("a", "b"), drivers, testConfig, null);

        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> lock.tryLock(Duration.ofSeconds(1)));
        } finally {
            // Clear interrupt status so it does not leak into subsequent tests
            Thread.interrupted();
        }
    }

    @Test
    void shouldThrowRedlockExceptionWhenLockPreInterrupted() {
        // No setIfNotExists stubs: lock() delegates to tryLock which throws InterruptedException at
        // the top of the retry loop before any acquisition attempt reaches the drivers.
        MultiLock lock = new MultiLock(Arrays.asList("a", "b"), drivers, testConfig, null);

        Thread.currentThread().interrupt();
        try {
            assertThrows(RedlockException.class, lock::lock);
            // lock() catches InterruptedException and re-sets the interrupt flag before wrapping
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Clear interrupt status so it does not leak into subsequent tests
            Thread.interrupted();
        }
    }
}
