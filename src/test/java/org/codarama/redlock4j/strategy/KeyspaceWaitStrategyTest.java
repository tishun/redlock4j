/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.RedlockException;
import org.codarama.redlock4j.driver.RedisDriver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KeyspaceWaitStrategy helper methods.
 */
@Tag("unit")
class KeyspaceWaitStrategyTest {

    @Test
    void mergeFlags_shouldMergeWithEmptyExisting() {
        String result = KeyspaceWaitStrategy.mergeFlags("", "Kgx");
        assertTrue(result.contains("K"));
        assertTrue(result.contains("g"));
        assertTrue(result.contains("x"));
    }

    @Test
    void mergeFlags_shouldMergeWithNullExisting() {
        String result = KeyspaceWaitStrategy.mergeFlags(null, "Kgx");
        assertTrue(result.contains("K"));
        assertTrue(result.contains("g"));
        assertTrue(result.contains("x"));
    }

    @Test
    void mergeFlags_shouldPreserveExistingFlags() {
        String result = KeyspaceWaitStrategy.mergeFlags("AE", "Kgx");
        assertTrue(result.contains("A"));
        assertTrue(result.contains("E"));
        assertTrue(result.contains("K"));
        assertTrue(result.contains("g"));
        assertTrue(result.contains("x"));
    }

    @Test
    void mergeFlags_shouldNotDuplicateFlags() {
        String result = KeyspaceWaitStrategy.mergeFlags("Kg", "Kgx");
        assertEquals(1, result.chars().filter(c -> c == 'K').count());
        assertEquals(1, result.chars().filter(c -> c == 'g').count());
        assertTrue(result.contains("x"));
    }

    @Test
    void hasAllFlags_shouldReturnTrueWhenAllPresent() {
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("Kgx", "Kgx"));
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("AKEgx", "Kgx"));
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("xgK", "Kgx"));
    }

    @Test
    void hasAllFlags_shouldReturnFalseWhenMissing() {
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("Kg", "Kgx"));
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("AE", "Kgx"));
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("", "Kgx"));
        assertFalse(KeyspaceWaitStrategy.hasAllFlags(null, "Kgx"));
    }

    @Test
    void getType_shouldReturnKeyspaceNotifications() {
        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        assertEquals(WaitStrategy.KEYSPACE_NOTIFICATIONS, strategy.getType());
    }

    @Test
    void mergeFlags_shouldHandleEmptyRequired() {
        String result = KeyspaceWaitStrategy.mergeFlags("AE", "");
        assertEquals("AE", result);
    }

    @Test
    void mergeFlags_shouldHandleSingleCharacter() {
        String result = KeyspaceWaitStrategy.mergeFlags("A", "K");
        assertTrue(result.contains("A"));
        assertTrue(result.contains("K"));
        assertEquals(2, result.length());
    }

    @Test
    void hasAllFlags_shouldHandleEmptyRequired() {
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("Kgx", ""));
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("", ""));
    }

    @Test
    void hasAllFlags_shouldBeCaseSensitive() {
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("kgx", "Kgx"));
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("KGX", "Kgx"));
    }

    @Test
    void close_shouldBeIdempotent() {
        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();

        assertDoesNotThrow(() -> {
            strategy.close();
            strategy.close();
            strategy.close();
        });
    }

    @Test
    void waitForRelease_shouldReturnTrueWhenNotInitialized() throws InterruptedException {
        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();

        // Without initialization, should fall back to immediate return
        boolean result = strategy.waitForRelease("test-lock", Duration.ofMillis(100));

        assertTrue(result);
    }

    @Test
    void initialize_withEmptyDriverList_shouldThrowIllegalArgument() {
        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();

        // Empty driver list should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> strategy.initialize(Collections.emptyList(), Duration.ofMillis(50)));
    }

    @Test
    void initialize_shouldConfigureKeyspaceNotifications() throws Exception {
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-redis");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("");

        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        List<RedisDriver> drivers = Arrays.asList(mockDriver);

        strategy.initialize(drivers, Duration.ofMillis(50));

        verify(mockDriver).configSet(eq("notify-keyspace-events"), anyString());
        strategy.close();
    }

    @Test
    void initialize_shouldSkipConfigIfAlreadySet() throws Exception {
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-redis");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("Kgx");

        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        List<RedisDriver> drivers = Arrays.asList(mockDriver);

        strategy.initialize(drivers, Duration.ofMillis(50));

        verify(mockDriver, never()).configSet(anyString(), anyString());
        strategy.close();
    }

    @Test
    void initialize_shouldThrowOnACLError() throws Exception {
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-redis");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("");
        doThrow(new RuntimeException("NOPERM: ACL denied")).when(mockDriver).configSet(anyString(), anyString());

        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        List<RedisDriver> drivers = Arrays.asList(mockDriver);

        RedlockException ex = assertThrows(RedlockException.class,
                () -> strategy.initialize(drivers, Duration.ofMillis(50)));

        assertTrue(ex.getMessage().contains("CONFIG SET"));
        strategy.close();
    }

    @Test
    void waitForRelease_shouldThrowWhenClosed() {
        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        strategy.close();

        assertThrows(IllegalStateException.class, () -> strategy.waitForRelease("test-lock", Duration.ofMillis(100)));
    }

    @Test
    void waitForRelease_shouldReturnTrueOnTimeout() throws Exception {
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-redis");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("Kgx");

        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        strategy.initialize(Arrays.asList(mockDriver), Duration.ofMillis(50));

        // Wait should timeout and return true to allow retry
        boolean result = strategy.waitForRelease("test-lock", Duration.ofMillis(50));
        assertTrue(result);

        strategy.close();
    }

    @Test
    void close_shouldWakeUpWaitingThreads() throws Exception {
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-redis");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("Kgx");

        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        strategy.initialize(Arrays.asList(mockDriver), Duration.ofMillis(50));

        // Start a waiting thread
        Thread waiter = new Thread(() -> {
            try {
                strategy.waitForRelease("test-lock", Duration.ofSeconds(30));
            } catch (Exception e) {
                // Expected
            }
        });
        waiter.start();

        // Give thread time to start waiting
        Thread.sleep(50);

        // Close should wake up the waiting thread
        strategy.close();

        waiter.join(1000);
        assertFalse(waiter.isAlive());
    }

    // ========== Concurrent Waiters Tests ==========

    @Test
    void concurrentWaiters_shouldAllWakeUpOnLockRelease() throws Exception {
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-redis");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("Kgx");

        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        strategy.initialize(Arrays.asList(mockDriver), Duration.ofMillis(50));

        int waiterCount = 5;
        java.util.concurrent.CountDownLatch allStarted = new java.util.concurrent.CountDownLatch(waiterCount);
        java.util.concurrent.CountDownLatch allDone = new java.util.concurrent.CountDownLatch(waiterCount);
        java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        String lockKey = "concurrent-test-lock";

        // Start multiple waiters on the same lock key
        for (int i = 0; i < waiterCount; i++) {
            new Thread(() -> {
                try {
                    allStarted.countDown();
                    strategy.waitForRelease(lockKey, Duration.ofSeconds(10));
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore
                } finally {
                    allDone.countDown();
                }
            }).start();
        }

        // Wait for all threads to start waiting
        assertTrue(allStarted.await(2, java.util.concurrent.TimeUnit.SECONDS));
        Thread.sleep(100); // Let them register in waitingLatches

        // Close to release all waiters
        strategy.close();

        // All waiters should complete
        assertTrue(allDone.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(waiterCount, completedCount.get());
    }

    @Test
    void concurrentWaiters_shouldShareSameLatch() throws Exception {
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-redis");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("Kgx");

        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        strategy.initialize(Arrays.asList(mockDriver), Duration.ofMillis(50));

        String lockKey = "shared-latch-test";
        java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch allDone = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // Two threads waiting on the same key should share the latch
        Thread t1 = new Thread(() -> {
            try {
                startGate.await();
                strategy.waitForRelease(lockKey, Duration.ofMillis(500));
                successCount.incrementAndGet();
            } catch (Exception e) {
                // Expected on close
            } finally {
                allDone.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                startGate.await();
                strategy.waitForRelease(lockKey, Duration.ofMillis(500));
                successCount.incrementAndGet();
            } catch (Exception e) {
                // Expected on close
            } finally {
                allDone.countDown();
            }
        });

        t1.start();
        t2.start();

        // Release both threads simultaneously
        startGate.countDown();

        // Wait for both to timeout or complete
        assertTrue(allDone.await(2, java.util.concurrent.TimeUnit.SECONDS));

        // Both should have completed (via timeout)
        assertEquals(2, successCount.get());

        strategy.close();
    }

    @Test
    void concurrentWaiters_rapidAcquireReleasePattern() throws Exception {
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-redis");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("Kgx");

        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        strategy.initialize(Arrays.asList(mockDriver), Duration.ofMillis(50));

        String lockKey = "rapid-test";
        int iterations = 20;
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(iterations);

        // Rapidly create and complete waitForRelease calls
        for (int i = 0; i < iterations; i++) {
            new Thread(() -> {
                try {
                    // Very short timeout to simulate rapid acquire/release
                    boolean result = strategy.waitForRelease(lockKey, Duration.ofMillis(10));
                    if (result) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    done.countDown();
                }
            }).start();
        }

        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
        // All should return true (even if via timeout)
        assertEquals(iterations, successCount.get());

        strategy.close();
    }

    @Test
    void concurrentWaiters_shouldHandleLatchRemovalRace() throws Exception {
        // This test specifically targets the race condition where one thread
        // removes the latch while another is still using it
        RedisDriver mockDriver = Mockito.mock(RedisDriver.class);
        when(mockDriver.getIdentifier()).thenReturn("test-redis");
        when(mockDriver.configGet("notify-keyspace-events")).thenReturn("Kgx");

        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        strategy.initialize(Arrays.asList(mockDriver), Duration.ofMillis(50));

        String lockKey = "latch-race-test";
        int threadCount = 10;
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threadCount);
        java.util.concurrent.atomic.AtomicInteger errorCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threadCount);

        // All threads start simultaneously to maximize contention
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            new Thread(() -> {
                try {
                    barrier.await(); // Synchronize start
                    // Mix of short and longer timeouts to create race conditions
                    Duration timeout = Duration.ofMillis(10 + (threadNum % 3) * 10);
                    strategy.waitForRelease(lockKey, timeout);
                } catch (InterruptedException | java.util.concurrent.BrokenBarrierException e) {
                    // Expected
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, errorCount.get(), "No errors should occur during concurrent latch access");

        strategy.close();
    }
}
