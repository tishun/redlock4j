/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * Fair lock client implementation using Redisson's RFairLock.
 */
public class RedissonFairLockClient extends AbstractFairLockClient {

    private static final String IMPLEMENTATION_TYPE = "redisson";
    
    private RedissonClient redissonClient;
    private boolean healthy = false;

    @Override
    public String getImplementationType() {
        return IMPLEMENTATION_TYPE;
    }

    @Override
    public void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager) {
        List<RedisClusterManager.RedisNodeInfo> nodes = clusterManager.getNodeInfos();
        
        Config redissonConfig = new Config();
        
        // Use single server mode with first node for simplicity
        // Redisson's RFairLock doesn't require multiple nodes like Redlock
        RedisClusterManager.RedisNodeInfo primaryNode = nodes.get(0);
        redissonConfig.useSingleServer()
                .setAddress(primaryNode.getRedisUrl())
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(20);

        // Configure lock watchdog timeout
        redissonConfig.setLockWatchdogTimeout(config.getLockTimeout().toMillis());

        try {
            redissonClient = Redisson.create(redissonConfig);
            healthy = true;
            logger.info("Redisson client initialized, connected to {}", primaryNode.getAddress());
        } catch (Exception e) {
            logger.error("Failed to initialize Redisson client: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public Lock getFairLock(String resourceName) {
        if (redissonClient == null) {
            throw new IllegalStateException("Client not initialized");
        }
        RLock fairLock = redissonClient.getFairLock(resourceName);
        return new RedissonLockWrapper(fairLock);
    }

    @Override
    public boolean isHealthy() {
        return healthy && redissonClient != null && !redissonClient.isShutdown();
    }

    @Override
    public void close() {
        if (redissonClient != null && !redissonClient.isShutdown()) {
            redissonClient.shutdown();
            logger.info("Redisson client closed");
        }
        healthy = false;
    }

    /**
     * Wrapper to adapt RLock to java.util.concurrent.locks.Lock interface.
     */
    private static class RedissonLockWrapper implements Lock {
        private final RLock rLock;

        RedissonLockWrapper(RLock rLock) {
            this.rLock = rLock;
        }

        @Override
        public void lock() {
            rLock.lock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            rLock.lockInterruptibly();
        }

        @Override
        public boolean tryLock() {
            return rLock.tryLock();
        }

        @Override
        public boolean tryLock(long time, java.util.concurrent.TimeUnit unit) throws InterruptedException {
            return rLock.tryLock(time, unit);
        }

        @Override
        public void unlock() {
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }

        @Override
        public java.util.concurrent.locks.Condition newCondition() {
            throw new UnsupportedOperationException("Conditions not supported");
        }
    }
}

