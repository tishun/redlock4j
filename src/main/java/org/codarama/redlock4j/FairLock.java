/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j;

import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.strategy.LockWaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * A distributed fair lock implementation that ensures FIFO (First-In-First-Out) ordering for lock acquisition. This
 * lock uses Redis sorted sets to maintain a queue of waiters, ensuring that threads acquire the lock in the order they
 * requested it.
 *
 * <p>
 * The fair lock provides stronger ordering guarantees than the standard Redlock but may have slightly lower throughput
 * due to the additional coordination required.
 * </p>
 *
 * <p>
 * <b>Implementation Details:</b>
 * </p>
 * <ul>
 * <li>Uses Redis sorted sets with timestamps to maintain FIFO order</li>
 * <li>Each waiter is assigned a unique token and timestamp</li>
 * <li>Only the waiter with the lowest timestamp can acquire the lock</li>
 * <li>Automatic cleanup of expired waiters</li>
 * </ul>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class FairLock extends AbstractRedlock implements Lock {
    private static final Logger logger = LoggerFactory.getLogger(FairLock.class);

    private final String lockKey;
    private final String queueKey;

    // Thread-local storage for lock state
    private final ThreadLocal<LockState> lockState = new ThreadLocal<>();

    private static class LockState extends BaseLockState {
        final String queueToken;

        LockState(String lockValue, String queueToken, Instant acquisitionTime, Duration validityDuration) {
            super(lockValue, acquisitionTime, validityDuration);
            this.queueToken = queueToken;
        }
    }

    public FairLock(String lockKey, List<RedisDriver> redisDrivers, RedlockConfiguration config,
            LockWaitStrategy waitStrategy) {
        super(redisDrivers, config, waitStrategy);
        this.lockKey = lockKey;
        this.queueKey = lockKey + ":queue";
    }

    @Override
    public void lock() {
        try {
            if (!tryLock(config.getLockAcquisitionTimeout())) {
                throw new RedlockException("Failed to acquire fair lock within timeout: " + lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedlockException("Interrupted while acquiring fair lock: " + lockKey, e);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (!tryLock(config.getLockAcquisitionTimeout())) {
            throw new RedlockException("Failed to acquire fair lock within timeout: " + lockKey);
        }
    }

    @Override
    public boolean tryLock() {
        try {
            return tryLock(Duration.ZERO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean tryLock(Duration timeout) throws InterruptedException {
        // Check if current thread already holds the lock (reentrancy)
        LockState currentState = lockState.get();
        if (currentState != null && currentState.isValid()) {
            currentState.incrementHoldCount();
            logger.debug("Reentrant fair lock acquisition for {} (hold count: {})", lockKey, currentState.holdCount);
            return true;
        }

        Instant deadline = Instant.now().plus(timeout);
        String queueToken = generateLockValue();
        long timestamp = System.currentTimeMillis(); // Redis ZADD requires numeric score

        try {
            // Add ourselves to the queue
            addToQueue(queueToken, timestamp);

            int attempt = 0;
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    removeFromQueue(queueToken);
                    throw new InterruptedException();
                }

                // Check if we've exceeded the timeout
                Duration remaining = Duration.between(Instant.now(), deadline);
                if (!timeout.isZero() && remaining.isNegative()) {
                    logger.debug("Fair lock acquisition timeout exceeded for {} after {} attempts", lockKey, attempt);
                    removeFromQueue(queueToken);
                    return false;
                }

                // Check if we're at the front of the queue
                if (isAtFrontOfQueue(queueToken)) {
                    // Try to acquire the lock
                    LockResult result = attemptLock();
                    if (result.isAcquired()) {
                        lockState.set(new LockState(result.getLockValue(), queueToken, Instant.now(),
                                Duration.ofMillis(result.getValidityTimeMs())));
                        logger.debug("Successfully acquired fair lock {} on attempt {}", lockKey, attempt + 1);
                        return true;
                    }
                }

                // For zero timeout (tryLock()), only try once
                if (timeout.isZero()) {
                    logger.debug("Fair lock {} not available for immediate acquisition", lockKey);
                    removeFromQueue(queueToken);
                    return false;
                }

                // Wait before retrying
                remaining = Duration.between(Instant.now(), deadline);
                if (!remaining.isNegative()) {
                    waitForLockRelease(lockKey, remaining.toMillis());
                }
                attempt++;
            }
        } catch (InterruptedException e) {
            removeFromQueue(queueToken);
            throw e;
        }
    }

    @Override
    public void unlock() {
        LockState state = lockState.get();
        if (state == null) {
            logger.warn("Attempting to unlock {} but no lock state found for current thread", lockKey);
            return;
        }

        if (!state.isValid()) {
            logger.warn("Fair lock {} has expired, cannot safely unlock", lockKey);
            lockState.remove();
            removeFromQueue(state.queueToken);
            return;
        }

        // Handle reentrancy
        int remainingHolds = state.decrementHoldCount();
        if (remainingHolds > 0) {
            logger.debug("Reentrant unlock for {} (remaining holds: {})", lockKey, remainingHolds);
            return;
        }

        // Final unlock
        releaseLock(state.lockValue);
        removeFromQueue(state.queueToken);
        lockState.remove();
        logger.debug("Successfully released fair lock {}", lockKey);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Conditions are not supported by distributed fair locks");
    }

    /**
     * Adds a token to the queue with the given timestamp. Uses Redis sorted sets (ZADD) to maintain FIFO ordering.
     */
    private void addToQueue(String token, long timestamp) {
        // Use execution strategy to add to queue on appropriate nodes
        int successfulNodes = executionStrategy.executeOnNodes(driver -> {
            try {
                return driver.zAdd(queueKey, timestamp, token);
            } catch (Exception e) {
                logger.debug("Failed to add to queue on {}: {}", driver.getIdentifier(), e.getMessage());
                return false;
            }
        });

        // Clean up expired entries (older than lock timeout)
        Duration expirationAge = config.getDefaultLockTimeout().multipliedBy(2);
        Instant expirationThreshold = Instant.now().minus(expirationAge);
        cleanupExpiredQueueEntries(expirationThreshold.toEpochMilli());

        logger.debug("Added token {} to queue {} with timestamp {} on {} nodes", token, queueKey, timestamp,
                successfulNodes);
    }

    /**
     * Removes a token from the queue. Uses Redis sorted sets (ZREM) to remove the token.
     */
    private void removeFromQueue(String token) {
        // Use execution strategy to remove from queue on appropriate nodes
        int successfulNodes = executionStrategy.executeOnNodes(driver -> {
            try {
                return driver.zRem(queueKey, token);
            } catch (Exception e) {
                logger.debug("Failed to remove from queue on {}: {}", driver.getIdentifier(), e.getMessage());
                return false;
            }
        });

        logger.debug("Removed token {} from queue {} on {} nodes", token, queueKey, successfulNodes);
    }

    /**
     * Checks if the given token is at the front of the queue. Uses Redis sorted sets (ZRANGE) to get the first element.
     */
    private boolean isAtFrontOfQueue(String token) {
        // Use execution strategy to check if token is at front on appropriate nodes
        int votesForFront = executionStrategy.executeOnNodes(driver -> {
            try {
                // Get the first element in the sorted set (lowest score/timestamp)
                List<String> firstElements = driver.zRange(queueKey, 0, 0);
                return !firstElements.isEmpty() && token.equals(firstElements.get(0));
            } catch (Exception e) {
                logger.debug("Failed to check queue position on {}: {}", driver.getIdentifier(), e.getMessage());
                return false;
            }
        });

        // Use strategy to determine if we have enough votes
        boolean atFront = executionStrategy.isSuccessful(votesForFront);
        logger.debug("Token {} is {} at front of queue (votes: {})", token, atFront ? "" : "NOT", votesForFront);

        return atFront;
    }

    /**
     * Cleans up expired entries from the queue. Removes entries with timestamps older than the threshold.
     */
    private void cleanupExpiredQueueEntries(long expirationThreshold) {
        // Use execution strategy to cleanup on appropriate nodes
        executionStrategy.executeOnNodes(driver -> {
            try {
                // Remove all entries with score (timestamp) less than threshold
                long removed = driver.zRemRangeByScore(queueKey, 0, expirationThreshold);
                if (removed > 0) {
                    logger.debug("Cleaned up {} expired queue entries on {}", removed, driver.getIdentifier());
                }
                return true;
            } catch (Exception e) {
                logger.debug("Failed to cleanup queue on {}: {}", driver.getIdentifier(), e.getMessage());
                return false;
            }
        });
    }

    private LockResult attemptLock() {
        String lockValue = generateLockValue();
        // Delegate to execution strategy (SingleNode or MultiNode)
        return executionStrategy.acquireLock(lockKey, lockValue, config.getDefaultLockTimeout().toMillis());
    }

    private void releaseLock(String lockValue) {
        // Delegate to execution strategy (SingleNode or MultiNode)
        executionStrategy.releaseLock(lockKey, lockValue);
    }

    /**
     * Checks if the current thread holds this lock.
     */
    public boolean isHeldByCurrentThread() {
        return isLockStateValid(lockState.get());
    }

    /**
     * Gets the remaining validity time of the lock.
     *
     * @return remaining validity time, or {@link Duration#ZERO} if not held or expired
     */
    public Duration getRemainingValidityTime() {
        return Duration.ofMillis(calculateRemainingValidityTime(lockState.get()));
    }

    /**
     * Gets the hold count for this lock.
     */
    public int getHoldCount() {
        LockState state = lockState.get();
        return isLockStateValid(state) ? state.getHoldCount() : 0;
    }
}
