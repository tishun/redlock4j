/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BackoffCalculator}.
 */
@Tag("unit")
class BackoffCalculatorTest {

    private static final Duration BASE = Duration.ofMillis(50);
    private static final Duration CAP = Duration.ofMillis(500);

    @Test
    void multiplierOneReturnsBaseDelayForAnyAttempt() {
        for (int attempt = 0; attempt < 10; attempt++) {
            Duration d = BackoffCalculator.compute(BASE, CAP, 1.0, 0.0, attempt);
            assertEquals(BASE, d, "attempt=" + attempt);
        }
    }

    @Test
    void zeroAttemptReturnsBaseDelay() {
        Duration d = BackoffCalculator.compute(BASE, CAP, 2.0, 0.0, 0);
        assertEquals(BASE, d);
    }

    @Test
    void exponentialGrowthRespectsCap() {
        Duration a1 = BackoffCalculator.compute(BASE, CAP, 2.0, 0.0, 1);
        Duration a2 = BackoffCalculator.compute(BASE, CAP, 2.0, 0.0, 2);
        Duration a3 = BackoffCalculator.compute(BASE, CAP, 2.0, 0.0, 3);
        Duration a10 = BackoffCalculator.compute(BASE, CAP, 2.0, 0.0, 10);
        assertEquals(100, a1.toMillis());
        assertEquals(200, a2.toMillis());
        assertEquals(400, a3.toMillis());
        assertEquals(CAP, a10);
    }

    @Test
    void jitterStaysWithinBounds() {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < 200; i++) {
            long ms = BackoffCalculator.compute(BASE, CAP, 1.0, 0.5, 0).toMillis();
            min = Math.min(min, ms);
            max = Math.max(max, ms);
        }
        assertTrue(min >= 25, "min=" + min);
        assertTrue(max <= 75, "max=" + max);
    }

    @Test
    void zeroJitterIsDeterministic() {
        Duration d1 = BackoffCalculator.compute(BASE, CAP, 1.0, 0.0, 5);
        Duration d2 = BackoffCalculator.compute(BASE, CAP, 1.0, 0.0, 5);
        assertEquals(d1, d2);
    }
}
