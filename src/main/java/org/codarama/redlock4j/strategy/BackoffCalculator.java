/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Computes exponential-backoff retry delays with optional jitter.
 *
 * <p>
 * Used by {@link PollingWaitStrategy} and by the {@link org.codarama.redlock4j.Redlock} retry-loop fallback path when
 * no wait strategy is configured.
 * </p>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public final class BackoffCalculator {

    private BackoffCalculator() {
    }

    /**
     * Computes the delay for a given attempt index.
     *
     * <p>
     * Effective delay = min(maxRetryDelay, retryDelay * multiplier^attempt). When {@code jitterRatio} is greater than
     * zero, the returned value is sampled uniformly from {@code [(1-r)*delay, (1+r)*delay]}.
     * </p>
     *
     * @param retryDelay
     *            the base delay
     * @param maxRetryDelay
     *            the upper bound (must not be null; defaults to {@code retryDelay} for no growth)
     * @param multiplier
     *            multiplier per attempt (1.0 disables growth)
     * @param jitterRatio
     *            jitter ratio in [0.0, 1.0]
     * @param attempt
     *            0-based attempt index
     * @return the computed delay
     */
    public static Duration compute(Duration retryDelay, Duration maxRetryDelay, double multiplier, double jitterRatio,
            int attempt) {
        long baseMs = retryDelay.toMillis();
        long capMs = maxRetryDelay.toMillis();

        double grown = baseMs;
        if (multiplier > 1.0 && attempt > 0) {
            grown = baseMs * Math.pow(multiplier, attempt);
        }
        long delayMs = (long) Math.min(grown, (double) capMs);

        if (jitterRatio > 0.0 && delayMs > 0) {
            long low = (long) Math.floor(delayMs * (1.0 - jitterRatio));
            long high = (long) Math.ceil(delayMs * (1.0 + jitterRatio));
            if (low < 0) {
                low = 0;
            }
            if (high <= low) {
                high = low + 1;
            }
            delayMs = ThreadLocalRandom.current().nextLong(low, high);
        }

        return Duration.ofMillis(delayMs);
    }
}
