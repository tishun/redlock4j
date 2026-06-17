/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PollingWaitStrategy.
 */
@Tag("unit")
class PollingWaitStrategyTest {

    @Test
    void getType_shouldReturnPolling() {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        assertEquals(WaitStrategy.POLLING, strategy.getType());
    }

    @Test
    void waitForRelease_shouldReturnAfterDelay() throws InterruptedException {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofMillis(50));

        long start = System.currentTimeMillis();
        boolean result = strategy.waitForRelease("test-lock", Duration.ofMillis(100));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result);
        assertTrue(elapsed >= 45, "Should have waited at least ~50ms, but only waited " + elapsed + "ms");
    }

    @Test
    void waitForRelease_shouldRespectTimeout() throws InterruptedException {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofMillis(200)); // Longer than timeout

        long start = System.currentTimeMillis();
        boolean result = strategy.waitForRelease("test-lock", Duration.ofMillis(50));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result); // Still returns true to signal caller to try
        assertTrue(elapsed <= 100, "Should have returned within timeout");
    }

    @Test
    void close_shouldPreventFurtherUse() {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofMillis(50));
        strategy.close();

        assertThrows(IllegalStateException.class, () -> strategy.waitForRelease("test-lock", Duration.ofMillis(100)));
    }

    @Test
    void initialize_shouldSetPollingInterval() throws InterruptedException {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofMillis(100));

        long start = System.currentTimeMillis();
        strategy.waitForRelease("test-lock", Duration.ofMillis(500));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed >= 90 && elapsed <= 150, "Should wait approximately the polling interval");
    }

    @Test
    void close_shouldBeIdempotent() {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofMillis(50));

        assertDoesNotThrow(() -> {
            strategy.close();
            strategy.close();
            strategy.close();
        });
    }

    @Test
    void waitForRelease_shouldHandleZeroTimeout() throws InterruptedException {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofMillis(50));

        long start = System.currentTimeMillis();
        boolean result = strategy.waitForRelease("test-lock", Duration.ZERO);
        long elapsed = System.currentTimeMillis() - start;

        // Zero timeout returns false (no wait) or quickly returns
        assertFalse(result, "Zero timeout should return false");
        assertTrue(elapsed <= 20, "Zero timeout should return quickly");
    }

    @Test
    void waitForRelease_shouldHandleNegativeTimeout() throws InterruptedException {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofMillis(50));

        long start = System.currentTimeMillis();
        boolean result = strategy.waitForRelease("test-lock", Duration.ofMillis(-100));
        long elapsed = System.currentTimeMillis() - start;

        // Negative timeout returns false (no wait)
        assertFalse(result, "Negative timeout should return false");
        assertTrue(elapsed <= 20, "Negative timeout should return quickly");
    }

    @Test
    void waitForRelease_withBackoffShouldGrowAndRespectCap() throws InterruptedException {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofMillis(20), Duration.ofMillis(100), 2.0, 0.0);

        // attempt 0 -> 20ms
        long t0 = System.currentTimeMillis();
        strategy.waitForRelease("k", Duration.ofMillis(500), 0);
        long e0 = System.currentTimeMillis() - t0;
        assertTrue(e0 >= 15 && e0 <= 60, "attempt=0 elapsed=" + e0);

        // attempt 3 -> 20*2^3 = 160 -> capped at 100ms
        long t3 = System.currentTimeMillis();
        strategy.waitForRelease("k", Duration.ofMillis(500), 3);
        long e3 = System.currentTimeMillis() - t3;
        assertTrue(e3 >= 90 && e3 <= 150, "attempt=3 elapsed=" + e3);
    }

    @Test
    void waitForRelease_defaultsPreserveLegacyBehavior() throws InterruptedException {
        // initialize via the legacy single-arg overload -> multiplier=1.0, jitter=0.0
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofMillis(50));

        long start = System.currentTimeMillis();
        strategy.waitForRelease("k", Duration.ofMillis(500), 4); // attempt should not grow delay
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 40 && elapsed <= 90, "elapsed=" + elapsed);
    }

    @Test
    void waitForRelease_shouldHandleInterruption() throws InterruptedException {
        PollingWaitStrategy strategy = new PollingWaitStrategy();
        strategy.initialize(Collections.emptyList(), Duration.ofSeconds(1)); // Long delay

        Thread testThread = Thread.currentThread();

        // Schedule an interrupt
        new Thread(() -> {
            try {
                Thread.sleep(50);
                testThread.interrupt();
            } catch (InterruptedException e) {
                // ignored
            }
        }).start();

        assertThrows(InterruptedException.class, () -> strategy.waitForRelease("test-lock", Duration.ofMillis(5000)));

        // Clear the interrupt flag
        Thread.interrupted();
    }
}
