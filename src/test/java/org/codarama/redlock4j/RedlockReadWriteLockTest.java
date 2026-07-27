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
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedlockReadWriteLock using Mockito mocks.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class RedlockReadWriteLockTest {

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

    // ========== Read Lock ==========

    @Test
    void shouldAcquireReadLockWhenNoWriter() throws RedisDriverException, InterruptedException {
        // No write lock exists
        when(mockDriver1.get(contains(":write"))).thenReturn(null);
        when(mockDriver2.get(contains(":write"))).thenReturn(null);
        when(mockDriver3.get(contains(":write"))).thenReturn(null);

        // Increment succeeds
        when(mockDriver1.incr(anyString())).thenReturn(1L);
        when(mockDriver2.incr(anyString())).thenReturn(1L);
        when(mockDriver3.incr(anyString())).thenReturn(1L);

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, testConfig, null);
        RedlockReadWriteLock.ReadLock readLock = (RedlockReadWriteLock.ReadLock) rwLock.readLock();

        boolean acquired = readLock.tryLock(Duration.ofSeconds(1));

        assertTrue(acquired);
        verify(mockDriver1, atLeastOnce()).incr(contains(":readers"));
    }

    @Test
    void shouldFailReadLockWhenWriterHoldsLock() throws RedisDriverException, InterruptedException {
        // Write lock exists on quorum
        when(mockDriver1.get(contains(":write"))).thenReturn("some-lock-value");
        when(mockDriver2.get(contains(":write"))).thenReturn("some-lock-value");
        when(mockDriver3.get(contains(":write"))).thenReturn(null);

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, testConfig, null);
        RedlockReadWriteLock.ReadLock readLock = (RedlockReadWriteLock.ReadLock) rwLock.readLock();

        boolean acquired = readLock.tryLock(Duration.ofMillis(100));

        assertFalse(acquired);
    }

    // ========== Write Lock ==========

    @Test
    void shouldAcquireWriteLockWhenNoReaders() throws RedisDriverException, InterruptedException {
        // No readers
        when(mockDriver1.get(contains(":readers"))).thenReturn(null);
        when(mockDriver2.get(contains(":readers"))).thenReturn(null);
        when(mockDriver3.get(contains(":readers"))).thenReturn(null);

        // Lock acquisition succeeds
        when(mockDriver1.setIfNotExists(contains(":write"), anyString(), anyLong())).thenReturn(true);
        when(mockDriver2.setIfNotExists(contains(":write"), anyString(), anyLong())).thenReturn(true);
        when(mockDriver3.setIfNotExists(contains(":write"), anyString(), anyLong())).thenReturn(true);

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, testConfig, null);
        RedlockReadWriteLock.WriteLock writeLock = (RedlockReadWriteLock.WriteLock) rwLock.writeLock();

        boolean acquired = writeLock.tryLock(Duration.ofSeconds(1));

        assertTrue(acquired);
    }

    @Test
    void shouldFailWriteLockWhenReadersExist() throws RedisDriverException, InterruptedException {
        // Active readers on quorum
        when(mockDriver1.get(contains(":readers"))).thenReturn("2");
        when(mockDriver2.get(contains(":readers"))).thenReturn("2");
        when(mockDriver3.get(contains(":readers"))).thenReturn(null);

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, testConfig, null);
        RedlockReadWriteLock.WriteLock writeLock = (RedlockReadWriteLock.WriteLock) rwLock.writeLock();

        boolean acquired = writeLock.tryLock(Duration.ofMillis(100));

        assertFalse(acquired);
    }

    // ========== Utility ==========

    @Test
    void shouldReturnSameLockInstances() {
        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, testConfig, null);

        Lock read1 = rwLock.readLock();
        Lock read2 = rwLock.readLock();
        Lock write1 = rwLock.writeLock();
        Lock write2 = rwLock.writeLock();

        assertSame(read1, read2);
        assertSame(write1, write2);
    }

    @Test
    void shouldThrowOnNewConditionForReadLock() {
        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, testConfig, null);
        assertThrows(UnsupportedOperationException.class, () -> rwLock.readLock().newCondition());
    }

    @Test
    void shouldThrowOnNewConditionForWriteLock() {
        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, testConfig, null);
        assertThrows(UnsupportedOperationException.class, () -> rwLock.writeLock().newCondition());
    }

    // ========== Read Lock - blocking / failure paths ==========

    /**
     * Builds a config with a short acquisition timeout and retry delay so blocking paths reach their timeout branch
     * quickly without mutating the shared setUp config.
     */
    private RedlockConfiguration shortTimeoutConfig() {
        return RedlockConfiguration.builder().addRedisNode("localhost", 6379).addRedisNode("localhost", 6380)
                .addRedisNode("localhost", 6381).defaultLockTimeout(Duration.ofSeconds(30))
                .retryDelay(Duration.ofMillis(5)).maxRetryAttempts(3).lockAcquisitionTimeout(Duration.ofMillis(150))
                .build();
    }

    @Test
    void shouldFailReadTryLockWithTimeoutWhenWriterHolds() throws RedisDriverException, InterruptedException {
        // Writer holds the lock on quorum, so the reader is blocked for the whole timeout.
        lenient().when(mockDriver1.get(contains(":write"))).thenReturn("writer-token");
        lenient().when(mockDriver2.get(contains(":write"))).thenReturn("writer-token");
        lenient().when(mockDriver3.get(contains(":write"))).thenReturn("writer-token");

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, shortTimeoutConfig(), null);
        RedlockReadWriteLock.ReadLock readLock = (RedlockReadWriteLock.ReadLock) rwLock.readLock();

        boolean acquired = readLock.tryLock(Duration.ofMillis(150));

        assertFalse(acquired);
    }

    @Test
    void shouldFailReadZeroTimeoutTryLockWhenWriterHolds() throws RedisDriverException {
        // Writer holds the lock on quorum -> tryLock() (zero timeout) returns false.
        lenient().when(mockDriver1.get(contains(":write"))).thenReturn("writer-token");
        lenient().when(mockDriver2.get(contains(":write"))).thenReturn("writer-token");
        lenient().when(mockDriver3.get(contains(":write"))).thenReturn("writer-token");

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, shortTimeoutConfig(), null);
        RedlockReadWriteLock.ReadLock readLock = (RedlockReadWriteLock.ReadLock) rwLock.readLock();

        assertFalse(readLock.tryLock());
    }

    @Test
    void shouldThrowWhenReadLockCannotBeAcquiredWithinTimeout() throws RedisDriverException {
        // Writer holds the lock on quorum -> lock() exceeds lockAcquisitionTimeout and throws.
        lenient().when(mockDriver1.get(contains(":write"))).thenReturn("writer-token");
        lenient().when(mockDriver2.get(contains(":write"))).thenReturn("writer-token");
        lenient().when(mockDriver3.get(contains(":write"))).thenReturn("writer-token");

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, shortTimeoutConfig(), null);
        RedlockReadWriteLock.ReadLock readLock = (RedlockReadWriteLock.ReadLock) rwLock.readLock();

        assertThrows(RedlockException.class, readLock::lock);
    }

    @Test
    void shouldThrowWhenReadLockInterruptiblyCannotBeAcquiredWithinTimeout() throws RedisDriverException {
        lenient().when(mockDriver1.get(contains(":write"))).thenReturn("writer-token");
        lenient().when(mockDriver2.get(contains(":write"))).thenReturn("writer-token");
        lenient().when(mockDriver3.get(contains(":write"))).thenReturn("writer-token");

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, shortTimeoutConfig(), null);
        RedlockReadWriteLock.ReadLock readLock = (RedlockReadWriteLock.ReadLock) rwLock.readLock();

        assertThrows(RedlockException.class, readLock::lockInterruptibly);
    }

    @Test
    void shouldThrowInterruptedExceptionWhenReadTryLockInterrupted() {
        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, shortTimeoutConfig(), null);
        RedlockReadWriteLock.ReadLock readLock = (RedlockReadWriteLock.ReadLock) rwLock.readLock();

        // Pre-interrupt the current thread; the acquisition loop checks the flag first and throws.
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> readLock.tryLock(Duration.ofSeconds(1)));
        } finally {
            // Clear the interrupt so it doesn't leak into other tests.
            Thread.interrupted();
        }
    }

    // ========== Write Lock - blocking / failure paths ==========

    @Test
    void shouldFailWriteTryLockWithTimeoutWhenReadersActive() throws RedisDriverException, InterruptedException {
        // Readers never drain -> tryLock(Duration) times out waiting for readers.
        lenient().when(mockDriver1.get(contains(":readers"))).thenReturn("3");
        lenient().when(mockDriver2.get(contains(":readers"))).thenReturn("3");
        lenient().when(mockDriver3.get(contains(":readers"))).thenReturn("3");

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, shortTimeoutConfig(), null);
        RedlockReadWriteLock.WriteLock writeLock = (RedlockReadWriteLock.WriteLock) rwLock.writeLock();

        boolean acquired = writeLock.tryLock(Duration.ofMillis(150));

        assertFalse(acquired);
    }

    @Test
    void shouldThrowWhenWriteLockCannotBeAcquiredWithinTimeout() throws RedisDriverException {
        // No readers, but the underlying write lock can never be acquired (setIfNotExists returns
        // false by default), so lock() exceeds the acquisition timeout and throws.
        lenient().when(mockDriver1.get(contains(":readers"))).thenReturn(null);
        lenient().when(mockDriver2.get(contains(":readers"))).thenReturn(null);
        lenient().when(mockDriver3.get(contains(":readers"))).thenReturn(null);

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, shortTimeoutConfig(), null);
        RedlockReadWriteLock.WriteLock writeLock = (RedlockReadWriteLock.WriteLock) rwLock.writeLock();

        assertThrows(RedlockException.class, writeLock::lock);
    }

    @Test
    void shouldThrowWhenWriteLockInterruptiblyCannotBeAcquiredWithinTimeout() throws RedisDriverException {
        lenient().when(mockDriver1.get(contains(":readers"))).thenReturn(null);
        lenient().when(mockDriver2.get(contains(":readers"))).thenReturn(null);
        lenient().when(mockDriver3.get(contains(":readers"))).thenReturn(null);

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, shortTimeoutConfig(), null);
        RedlockReadWriteLock.WriteLock writeLock = (RedlockReadWriteLock.WriteLock) rwLock.writeLock();

        assertThrows(RedlockException.class, writeLock::lockInterruptibly);
    }

    @Test
    void shouldThrowInterruptedExceptionWhenWriteLockInterruptiblyInterrupted() throws RedisDriverException {
        // No readers so waitForReadersToFinish returns immediately; the pre-interrupt then surfaces
        // when the underlying Redlock checks the interrupt flag before attempting acquisition.
        lenient().when(mockDriver1.get(contains(":readers"))).thenReturn(null);
        lenient().when(mockDriver2.get(contains(":readers"))).thenReturn(null);
        lenient().when(mockDriver3.get(contains(":readers"))).thenReturn(null);

        RedlockReadWriteLock rwLock = new RedlockReadWriteLock("test-rw", drivers, shortTimeoutConfig(), null);
        RedlockReadWriteLock.WriteLock writeLock = (RedlockReadWriteLock.WriteLock) rwLock.writeLock();

        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> writeLock.lockInterruptibly());
        } finally {
            Thread.interrupted();
        }
    }
}
