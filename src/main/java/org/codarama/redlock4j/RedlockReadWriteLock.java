/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j;

import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.strategy.LockExecutionStrategy;
import org.codarama.redlock4j.strategy.LockExecutionStrategyFactory;
import org.codarama.redlock4j.strategy.LockWaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * A distributed read-write lock implementation that allows multiple concurrent readers or a single exclusive writer.
 * This is useful for scenarios where reads are frequent and writes are infrequent.
 * 
 * <p>
 * <b>Key Features:</b>
 * </p>
 * <ul>
 * <li>Multiple readers can hold the lock simultaneously</li>
 * <li>Writers have exclusive access (no readers or other writers)</li>
 * <li>Readers are blocked while a writer holds the lock</li>
 * <li>Writers are blocked while any readers or writers hold the lock</li>
 * </ul>
 * 
 * <p>
 * <b>Implementation Details:</b>
 * </p>
 * <ul>
 * <li>Uses Redis counters to track the number of active readers</li>
 * <li>Uses a separate key for the write lock</li>
 * <li>Readers increment/decrement the reader count atomically</li>
 * <li>Writers must wait for reader count to reach zero</li>
 * </ul>
 * 
 * <p>
 * <b>Example Usage:</b>
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     RedlockReadWriteLock rwLock = new RedlockReadWriteLock("resource", redisDrivers, config);
 * 
 *     // Reading
 *     rwLock.readLock().lock();
 *     try {
 *         // Multiple threads can read simultaneously
 *         readData();
 *     } finally {
 *         rwLock.readLock().unlock();
 *     }
 * 
 *     // Writing
 *     rwLock.writeLock().lock();
 *     try {
 *         // Exclusive access for writing
 *         writeData();
 *     } finally {
 *         rwLock.writeLock().unlock();
 *     }
 * }
 * </pre>
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class RedlockReadWriteLock implements ReadWriteLock {
    private static final Logger logger = LoggerFactory.getLogger(RedlockReadWriteLock.class);

    private final String resourceKey;
    private final ReadLock readLock;
    private final WriteLock writeLock;

    /**
     * Creates a new distributed read-write lock.
     *
     * @param resourceKey
     *            the key identifying the shared resource
     * @param redisDrivers
     *            the Redis drivers to use
     * @param config
     *            the Redlock configuration
     * @param waitStrategy
     *            the wait strategy for lock contention
     */
    public RedlockReadWriteLock(String resourceKey, List<RedisDriver> redisDrivers, RedlockConfiguration config,
            LockWaitStrategy waitStrategy) {
        this.resourceKey = resourceKey;
        this.readLock = new ReadLock(resourceKey, redisDrivers, config, waitStrategy);
        this.writeLock = new WriteLock(resourceKey, redisDrivers, config, waitStrategy);
    }

    /**
     * Returns the lock used for reading.
     *
     * @return the read lock
     */
    @Override
    public Lock readLock() {
        return readLock;
    }

    /**
     * Returns the lock used for writing.
     *
     * @return the write lock
     */
    @Override
    public Lock writeLock() {
        return writeLock;
    }

    /**
     * Read lock implementation that allows multiple concurrent readers.
     */
    public static class ReadLock implements Lock {
        private static final Logger logger = LoggerFactory.getLogger(ReadLock.class);

        private final String readCountKey;
        private final String writeLockKey;
        private final List<RedisDriver> redisDrivers;
        private final RedlockConfiguration config;
        private final SecureRandom secureRandom;
        private final LockWaitStrategy waitStrategy;
        private final LockExecutionStrategy executionStrategy;

        private final ThreadLocal<LockState> lockState = new ThreadLocal<>();

        private static class LockState {
            final String lockValue;
            final Instant acquisitionTime;
            final Duration validityDuration;
            int holdCount;

            LockState(String lockValue, Instant acquisitionTime, Duration validityDuration) {
                this.lockValue = lockValue;
                this.acquisitionTime = acquisitionTime;
                this.validityDuration = validityDuration;
                this.holdCount = 1;
            }

            boolean isValid() {
                return Instant.now().isBefore(getExpiryTime());
            }

            Instant getExpiryTime() {
                return acquisitionTime.plus(validityDuration);
            }

            void incrementHoldCount() {
                holdCount++;
            }

            int decrementHoldCount() {
                return --holdCount;
            }
        }

        ReadLock(String resourceKey, List<RedisDriver> redisDrivers, RedlockConfiguration config,
                LockWaitStrategy waitStrategy) {
            this.readCountKey = resourceKey + ":readers";
            this.writeLockKey = resourceKey + ":write";
            this.redisDrivers = redisDrivers;
            this.config = config;
            this.secureRandom = new SecureRandom();
            this.waitStrategy = waitStrategy;
            this.executionStrategy = LockExecutionStrategyFactory.create(redisDrivers, config);
        }

        @Override
        public void lock() {
            try {
                if (!tryLock(config.getLockAcquisitionTimeout())) {
                    throw new RedlockException("Failed to acquire read lock within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RedlockException("Interrupted while acquiring read lock", e);
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            // Honor the Lock.lockInterruptibly() contract: respond to a pending interrupt before doing any work
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (!tryLock(config.getLockAcquisitionTimeout())) {
                throw new RedlockException("Failed to acquire read lock within timeout");
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
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return tryLock(Duration.ofNanos(unit.toNanos(time)));
        }

        /**
         * Attempts to acquire the read lock within the given timeout.
         *
         * @param timeout
         *            the maximum time to wait for the lock
         * @return true if the lock was acquired, false if the timeout elapsed
         * @throws InterruptedException
         *             if the current thread is interrupted
         */
        public boolean tryLock(Duration timeout) throws InterruptedException {
            // Check if current thread already holds the lock (reentrancy)
            LockState currentState = lockState.get();
            if (currentState != null && currentState.isValid()) {
                currentState.incrementHoldCount();
                logger.debug("Reentrant read lock acquisition (hold count: {})", currentState.holdCount);
                return true;
            }

            Instant deadline = Instant.now().plus(timeout);

            for (int attempt = 0; attempt <= config.getMaxRetryAttempts(); attempt++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                // Check if there's an active writer
                if (!isWriteLockHeld()) {
                    // Try to increment reader count
                    String lockValue = generateLockValue();
                    if (incrementReaderCount(lockValue)) {
                        lockState.set(new LockState(lockValue, Instant.now(), config.getDefaultLockTimeout()));
                        logger.debug("Successfully acquired read lock on attempt {}", attempt + 1);
                        return true;
                    }
                }

                // Check if we've exceeded the timeout
                Duration remaining = Duration.between(Instant.now(), deadline);
                if (!timeout.isZero() && remaining.isNegative()) {
                    logger.debug("Read lock acquisition timeout exceeded");
                    break;
                }

                // Wait before retrying
                if (attempt < config.getMaxRetryAttempts()) {
                    waitForLockRelease(remaining.toMillis(), attempt);
                }
            }

            return false;
        }

        private void waitForLockRelease(long remainingTimeoutMs, int attempt) throws InterruptedException {
            if (waitStrategy != null) {
                waitStrategy.waitForRelease(writeLockKey, Duration.ofMillis(Math.max(remainingTimeoutMs, 1)), attempt);
            } else {
                Duration delay = org.codarama.redlock4j.strategy.BackoffCalculator.compute(config.getRetryDelay(),
                        config.getMaxRetryDelay(), config.getRetryDelayMultiplier(), config.getRetryDelayJitterRatio(),
                        attempt);
                long sleepMs = Math.min(delay.toMillis(), Math.max(remainingTimeoutMs, 1));
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                }
            }
        }

        @Override
        public void unlock() {
            LockState state = lockState.get();
            if (state == null) {
                logger.warn("Attempting to unlock read lock but no lock state found");
                return;
            }

            // Handle reentrancy
            int remainingHolds = state.decrementHoldCount();
            if (remainingHolds > 0) {
                logger.debug("Reentrant unlock for read lock (remaining holds: {})", remainingHolds);
                return;
            }

            // Final unlock - decrement reader count
            decrementReaderCount(state.lockValue);
            lockState.remove();
            logger.debug("Successfully released read lock");
        }

        private boolean isWriteLockHeld() {
            // Check if write lock exists on nodes using GET
            // Use execution strategy to check on appropriate nodes
            int nodesWithoutWriteLock = executionStrategy.executeOnNodes(driver -> {
                try {
                    String value = driver.get(writeLockKey);
                    return value == null; // Return true if no write lock
                } catch (Exception e) {
                    logger.debug("Failed to check write lock on {}: {}", driver.getIdentifier(), e.getMessage());
                    return false;
                }
            });
            // If enough nodes don't have the write lock, it's not held
            return !executionStrategy.isSuccessful(nodesWithoutWriteLock);
        }

        private boolean incrementReaderCount(String lockValue) {
            // Use Redis INCR to atomically increment the reader count
            // Use execution strategy to increment on appropriate nodes
            long lockTimeoutMs = config.getDefaultLockTimeout().toMillis();
            int successfulNodes = executionStrategy.executeOnNodes(driver -> {
                try {
                    // Increment the reader count atomically
                    long count = driver.incr(readCountKey);

                    // Set expiration on the counter key to prevent leaks
                    if (count == 1) {
                        // First reader, set expiration
                        driver.setex(readCountKey, String.valueOf(count), lockTimeoutMs * 2);
                    }

                    // Store the lock value for this reader
                    driver.setex(readCountKey + ":" + lockValue, "1", lockTimeoutMs);

                    logger.debug("Incremented reader count to {} on {}", count, driver.getIdentifier());
                    return true;
                } catch (Exception e) {
                    logger.debug("Failed to increment reader count on {}: {}", driver.getIdentifier(), e.getMessage());
                    return false;
                }
            });

            return executionStrategy.isSuccessful(successfulNodes);
        }

        private void decrementReaderCount(String lockValue) {
            // Use Redis DECR to atomically decrement the reader count
            // Use execution strategy to decrement on appropriate nodes
            executionStrategy.executeOnNodes(driver -> {
                try {
                    // Decrement the reader count atomically
                    long count = driver.decr(readCountKey);

                    // Delete the lock value for this reader
                    driver.del(readCountKey + ":" + lockValue);

                    // If count reaches 0, clean up the counter key
                    if (count <= 0) {
                        driver.del(readCountKey);
                    }

                    logger.debug("Decremented reader count to {} on {}", count, driver.getIdentifier());
                    return true;
                } catch (Exception e) {
                    logger.warn("Failed to decrement reader count on {}: {}", driver.getIdentifier(), e.getMessage());
                    return false;
                }
            });
        }

        private String generateLockValue() {
            byte[] bytes = new byte[20];
            secureRandom.nextBytes(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("Conditions are not supported");
        }
    }

    /**
     * Write lock implementation that provides exclusive access. Writers must wait for all readers to finish before
     * acquiring the lock.
     */
    public static class WriteLock implements Lock {
        private static final Logger logger = LoggerFactory.getLogger(WriteLock.class);

        private final Redlock underlyingLock;
        private final String readCountKey;
        private final List<RedisDriver> redisDrivers;
        private final RedlockConfiguration config;
        private final LockWaitStrategy waitStrategy;
        private final LockExecutionStrategy executionStrategy;

        WriteLock(String resourceKey, List<RedisDriver> redisDrivers, RedlockConfiguration config,
                LockWaitStrategy waitStrategy) {
            this.underlyingLock = new Redlock(resourceKey + ":write", redisDrivers, config, waitStrategy);
            this.readCountKey = resourceKey + ":readers";
            this.redisDrivers = redisDrivers;
            this.config = config;
            this.waitStrategy = waitStrategy;
            this.executionStrategy = LockExecutionStrategyFactory.create(redisDrivers, config);
        }

        @Override
        public void lock() {
            // Wait for readers to finish before acquiring write lock
            waitForReadersToFinish();
            underlyingLock.lock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            // Honor the Lock.lockInterruptibly() contract: respond to a pending interrupt before doing any work
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            // Wait for readers to finish before acquiring write lock
            waitForReadersToFinish();
            underlyingLock.lockInterruptibly();
        }

        @Override
        public boolean tryLock() {
            // Check if there are active readers
            if (hasActiveReaders()) {
                return false;
            }
            return underlyingLock.tryLock();
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return tryLock(Duration.ofNanos(unit.toNanos(time)));
        }

        /**
         * Attempts to acquire the write lock within the given timeout.
         *
         * @param timeout
         *            the maximum time to wait for the lock
         * @return true if the lock was acquired, false if the timeout elapsed
         * @throws InterruptedException
         *             if the current thread is interrupted
         */
        public boolean tryLock(Duration timeout) throws InterruptedException {
            Instant deadline = Instant.now().plus(timeout);

            // Wait for readers to finish with timeout
            while (hasActiveReaders()) {
                Duration remaining = Duration.between(Instant.now(), deadline);
                if (!timeout.isZero() && remaining.isNegative()) {
                    logger.debug("Timeout waiting for readers to finish");
                    return false;
                }
                Thread.sleep(config.getRetryDelay().toMillis());
            }

            // Try to acquire write lock with remaining time
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isNegative() || remaining.isZero()) {
                return underlyingLock.tryLock();
            }
            return underlyingLock.tryLock(remaining);
        }

        @Override
        public void unlock() {
            underlyingLock.unlock();
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("Conditions are not supported");
        }

        /**
         * Waits for all active readers to finish.
         */
        private void waitForReadersToFinish() {
            while (hasActiveReaders()) {
                try {
                    Thread.sleep(config.getRetryDelay().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RedlockException("Interrupted while waiting for readers", e);
                }
            }
        }

        /**
         * Checks if there are active readers using atomic GET operation.
         */
        private boolean hasActiveReaders() {
            // Use execution strategy to check reader count on appropriate nodes
            int nodesWithoutReaders = executionStrategy.executeOnNodes(driver -> {
                try {
                    String countStr = driver.get(readCountKey);
                    return countStr == null || Long.parseLong(countStr) <= 0;
                } catch (Exception e) {
                    logger.debug("Failed to check reader count on {}: {}", driver.getIdentifier(), e.getMessage());
                    return false;
                }
            });

            // If enough nodes have no readers, we can proceed
            return !executionStrategy.isSuccessful(nodesWithoutReaders);
        }
    }
}
