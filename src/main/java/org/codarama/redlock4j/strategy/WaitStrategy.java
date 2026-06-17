/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

/**
 * Defines the strategy used to wait for lock release when a lock is contended.
 *
 * <p>
 * The wait strategy determines how redlock4j waits for a lock to become available when another client holds it. The
 * choice of strategy affects both latency and resource usage.
 * </p>
 *
 * @see LockWaitStrategy
 * @since 1.0
 * @author Tihomir Mateev
 */
public enum WaitStrategy {

    /**
     * Uses Redis Keyspace Notifications to receive instant notification when a lock is released.
     *
     * <p>
     * This is the <b>default and recommended</b> strategy. It provides:
     * <ul>
     * <li>Instant notification when lock is released (1-5ms latency vs 50-100ms with polling)</li>
     * <li>Automatic detection of lock expiration (TTL)</li>
     * <li>Lower CPU usage (no busy-waiting)</li>
     * </ul>
     *
     * <p>
     * <b>Requirements:</b>
     * <ul>
     * <li>RESP3 protocol (automatically verified)</li>
     * <li>Redis 6.0+ (recommended)</li>
     * </ul>
     *
     * <p>
     * When using this strategy, redlock4j automatically configures the Redis server with:
     * 
     * <pre>
     * CONFIG SET notify-keyspace-events "Kgx"
     * </pre>
     * </p>
     */
    KEYSPACE_NOTIFICATIONS,

    /**
     * Uses periodic polling to check if a lock has been released.
     *
     * <p>
     * This is a <b>fallback strategy</b> for environments where:
     * <ul>
     * <li>Redis CONFIG commands are restricted (e.g., managed Redis services)</li>
     * <li>Keyspace notifications are not available or disabled</li>
     * <li>Legacy RESP2 clients must be supported (not recommended)</li>
     * </ul>
     *
     * <p>
     * The polling interval is configured via {@code retryDelay} in
     * {@link org.codarama.redlock4j.configuration.RedlockConfiguration}. Exponential backoff is also supported via
     * {@code maxRetryDelay}, {@code retryDelayMultiplier} and {@code retryDelayJitterRatio} \u2014 these default to
     * passive values that preserve a fixed-interval poll.
     * </p>
     */
    POLLING
}
