/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.LockResult;
import org.codarama.redlock4j.driver.RedisDriver;

import java.util.function.Function;

/**
 * Strategy interface for lock execution across Redis nodes.
 * 
 * <p>
 * Implementations handle the differences between single-node and multi-node (Redlock) lock operations:
 * </p>
 * <ul>
 * <li>{@link SingleNodeStrategy}: Optimized for single Redis instance, no consensus overhead</li>
 * <li>{@link MultiNodeStrategy}: Distributed consensus with quorum-based acquisition</li>
 * </ul>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public interface LockExecutionStrategy {

    /**
     * Attempts to acquire a lock.
     * <ul>
     * <li>SingleNode: Direct SET NX on one node</li>
     * <li>MultiNode: Quorum-based acquisition across nodes</li>
     * </ul>
     *
     * @param key
     *            the lock key
     * @param value
     *            the unique lock value (used for safe release)
     * @param timeoutMs
     *            the lock timeout in milliseconds
     * @return the lock result containing acquisition status and validity time
     */
    LockResult acquireLock(String key, String value, long timeoutMs);

    /**
     * Releases a lock.
     * <ul>
     * <li>SingleNode: Direct DEL on one node</li>
     * <li>MultiNode: Release on all nodes</li>
     * </ul>
     *
     * @param key
     *            the lock key
     * @param value
     *            the lock value (must match for safe release)
     */
    void releaseLock(String key, String value);

    /**
     * Extends lock validity.
     * <ul>
     * <li>SingleNode: Direct SET IFEQ on one node</li>
     * <li>MultiNode: Quorum-based extension</li>
     * </ul>
     *
     * @param key
     *            the lock key
     * @param currentValue
     *            the current lock value
     * @param newTimeoutMs
     *            the new timeout in milliseconds
     * @return true if extension succeeded
     */
    boolean extendLock(String key, String currentValue, long newTimeoutMs);

    /**
     * Calculates remaining validity time after acquisition.
     * <ul>
     * <li>SingleNode: timeout - elapsed (no drift compensation)</li>
     * <li>MultiNode: timeout - elapsed - driftTime</li>
     * </ul>
     *
     * @param timeoutMs
     *            the original lock timeout
     * @param elapsedMs
     *            the time elapsed during acquisition
     * @return the remaining validity time in milliseconds
     */
    long calculateValidityTime(long timeoutMs, long elapsedMs);

    /**
     * Executes an operation on Redis nodes and returns success count.
     * <ul>
     * <li>SingleNode: Executes on single node, returns 1 or 0</li>
     * <li>MultiNode: Executes on all nodes, returns count of successful operations</li>
     * </ul>
     *
     * @param operation
     *            the operation to execute on each driver
     * @return the number of nodes where the operation succeeded
     */
    int executeOnNodes(Function<RedisDriver, Boolean> operation);

    /**
     * Checks if the given success count meets the required threshold.
     * <ul>
     * <li>SingleNode: success if count >= 1</li>
     * <li>MultiNode: success if count >= quorum</li>
     * </ul>
     *
     * @param successCount
     *            the number of successful node operations
     * @return true if the success count meets the threshold
     */
    boolean isSuccessful(int successCount);

    /**
     * Returns whether this strategy operates in single-node mode.
     *
     * @return true if single-node mode
     */
    boolean isSingleNodeMode();
}
