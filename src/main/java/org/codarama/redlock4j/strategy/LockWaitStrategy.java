/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.driver.RedisDriver;

import java.time.Duration;
import java.util.List;

/**
 * Strategy interface for waiting on lock release.
 *
 * <p>
 * When a lock is contended (held by another client), the wait strategy determines how redlock4j waits for the lock to
 * become available. Different strategies offer different tradeoffs between latency and resource usage.
 * </p>
 *
 * <p>
 * Implementations must be thread-safe as they may be shared across multiple lock instances.
 * </p>
 *
 * @see WaitStrategy
 * @see KeyspaceWaitStrategy
 * @see PollingWaitStrategy
 * @since 1.0
 * @author Tihomir Mateev
 */
public interface LockWaitStrategy extends AutoCloseable {

    /**
     * Initializes the wait strategy with the given Redis drivers.
     *
     * <p>
     * This method is called once when the {@link org.codarama.redlock4j.RedlockManager} is created. Implementations
     * should:
     * <ul>
     * <li>Verify that the Redis connections meet their requirements (e.g., RESP3 protocol)</li>
     * <li>Configure the Redis server if necessary (e.g., keyspace notifications)</li>
     * <li>Set up any required resources (e.g., pub/sub subscriptions)</li>
     * </ul>
     *
     * @param drivers
     *            the Redis drivers to use
     * @param retryDelay
     *            the configured retry delay (used by polling strategy)
     * @throws org.codarama.redlock4j.RedlockException
     *             if initialization fails
     */
    void initialize(List<RedisDriver> drivers, Duration retryDelay);

    /**
     * Waits for a lock to be released on the specified key.
     *
     * <p>
     * This method blocks until one of the following occurs:
     * <ul>
     * <li>The lock is released (DEL event)</li>
     * <li>The lock expires (EXPIRED event)</li>
     * <li>The timeout is reached</li>
     * <li>The thread is interrupted</li>
     * </ul>
     *
     * <p>
     * After this method returns, the caller should attempt to acquire the lock. This method does not guarantee that the
     * lock is available, only that a release event was detected or the timeout was reached.
     * </p>
     *
     * @param lockKey
     *            the key of the lock to wait for
     * @param timeout
     *            maximum time to wait
     * @return true if a release event was detected, false if timeout was reached
     * @throws InterruptedException
     *             if the current thread is interrupted while waiting
     */
    boolean waitForRelease(String lockKey, Duration timeout) throws InterruptedException;

    /**
     * Returns the type of wait strategy.
     *
     * @return the wait strategy type
     */
    WaitStrategy getType();

    /**
     * Closes this strategy and releases any resources.
     *
     * <p>
     * After this method is called, the strategy should not be used.
     * </p>
     */
    @Override
    void close();
}
