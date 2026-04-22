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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * MultiLock client implementation using Redisson's RMultiLock.
 */
public class RedissonMultiLockClient extends AbstractMultiLockClient {

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
        RedisClusterManager.RedisNodeInfo primaryNode = nodes.get(0);
        redissonConfig.useSingleServer()
                .setAddress(primaryNode.getRedisUrl())
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(20);

        redissonConfig.setLockWatchdogTimeout(config.getLockTimeout().toMillis());

        try {
            redissonClient = Redisson.create(redissonConfig);
            healthy = true;
            logger.info("Redisson MultiLock client initialized, connected to {}", primaryNode.getAddress());
        } catch (Exception e) {
            logger.error("Failed to initialize Redisson MultiLock client: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public Lock getMultiLock(List<String> resourceNames) {
        if (redissonClient == null) {
            throw new IllegalStateException("Client not initialized");
        }
        RLock[] locks = resourceNames.stream()
                .map(redissonClient::getLock)
                .toArray(RLock[]::new);
        return new RedissonMultiLockWrapper(redissonClient.getMultiLock(locks));
    }

    @Override
    public boolean isHealthy() {
        return healthy && redissonClient != null && !redissonClient.isShutdown();
    }

    @Override
    public void close() {
        if (redissonClient != null && !redissonClient.isShutdown()) {
            redissonClient.shutdown();
            logger.info("Redisson MultiLock client closed");
        }
        healthy = false;
    }

    private static class RedissonMultiLockWrapper implements Lock {
        private final RLock multiLock;

        RedissonMultiLockWrapper(RLock multiLock) {
            this.multiLock = multiLock;
        }

        @Override
        public void lock() {
            multiLock.lock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            multiLock.lockInterruptibly();
        }

        @Override
        public boolean tryLock() {
            return multiLock.tryLock();
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return multiLock.tryLock(time, unit);
        }

        @Override
        public void unlock() {
            if (multiLock.isHeldByCurrentThread()) {
                multiLock.unlock();
            }
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("Conditions not supported");
        }
    }
}

