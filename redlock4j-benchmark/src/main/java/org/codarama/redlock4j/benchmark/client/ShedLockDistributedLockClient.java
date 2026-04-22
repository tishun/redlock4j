/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.lettuce.LettuceLockProvider;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.benchmark.infrastructure.RedisResultsStore;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Distributed lock client using ShedLock's LettuceLockProvider.
 * 
 * Note: ShedLock is designed primarily for scheduled task locking, not general-purpose
 * distributed locks. This adapter wraps it to fit the benchmark interface.
 */
public class ShedLockDistributedLockClient extends AbstractDistributedLockClient {

    private static final String IMPLEMENTATION_TYPE = "shedlock-lettuce";

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private LockProvider lockProvider;
    private Duration lockTimeout;
    private boolean healthy = false;

    @Override
    public String getImplementationType() {
        return IMPLEMENTATION_TYPE;
    }

    @Override
    public void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager) {
        List<RedisClusterManager.RedisNodeInfo> nodes = clusterManager.getNodeInfos();
        RedisClusterManager.RedisNodeInfo primaryNode = nodes.get(0);

        this.lockTimeout = config.getLockTimeout();

        try {
            RedisURI redisUri = RedisURI.builder()
                    .withHost(primaryNode.getHost())
                    .withPort(primaryNode.getPort())
                    .build();
            
            redisClient = RedisClient.create(redisUri);
            connection = redisClient.connect();
            lockProvider = new LettuceLockProvider(connection);
            
            healthy = true;
            logger.info("ShedLock distributed lock client initialized, connected to {}", primaryNode.getAddress());
        } catch (Exception e) {
            logger.error("Failed to initialize ShedLock client: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public Lock getLock(String resourceName) {
        if (lockProvider == null) {
            throw new IllegalStateException("Client not initialized");
        }
        return new ShedLockAdapter(lockProvider, resourceName, lockTimeout);
    }

    @Override
    public boolean isHealthy() {
        return healthy && connection != null && connection.isOpen();
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
        logger.info("ShedLock distributed lock client closed");
        healthy = false;
    }

    /**
     * Adapter to make ShedLock's LockProvider work with java.util.concurrent.locks.Lock interface.
     */
    private static class ShedLockAdapter implements Lock {
        private final LockProvider lockProvider;
        private final String lockName;
        private final Duration lockTimeout;
        private SimpleLock currentLock;

        ShedLockAdapter(LockProvider lockProvider, String lockName, Duration lockTimeout) {
            this.lockProvider = lockProvider;
            this.lockName = lockName;
            this.lockTimeout = lockTimeout;
        }

        @Override
        public void lock() {
            while (!tryLock()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            while (!tryLock()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                Thread.sleep(10);
            }
        }

        @Override
        public boolean tryLock() {
            LockConfiguration config = new LockConfiguration(
                    Instant.now(),
                    lockName,
                    lockTimeout,
                    Duration.ZERO
            );
            Optional<SimpleLock> lock = lockProvider.lock(config);
            if (lock.isPresent()) {
                currentLock = lock.get();
                return true;
            }
            return false;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(time);
            while (System.nanoTime() < deadline) {
                if (tryLock()) {
                    return true;
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                Thread.sleep(5);
            }
            return false;
        }

        @Override
        public void unlock() {
            if (currentLock != null) {
                currentLock.unlock();
                currentLock = null;
            }
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("Conditions not supported by ShedLock");
        }
    }
}
