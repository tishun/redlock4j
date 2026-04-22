/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Distributed lock client using Redisson's RLock.
 */
public class RedissonDistributedLockClient extends AbstractDistributedLockClient {

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
            logger.info("Redisson distributed lock client initialized, connected to {}", primaryNode.getAddress());
        } catch (Exception e) {
            logger.error("Failed to initialize Redisson client: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public Lock getLock(String resourceName) {
        if (redissonClient == null) {
            throw new IllegalStateException("Client not initialized");
        }
        return redissonClient.getLock(resourceName);
    }

    @Override
    public boolean isHealthy() {
        return healthy && redissonClient != null && !redissonClient.isShutdown();
    }

    @Override
    public void close() {
        if (redissonClient != null && !redissonClient.isShutdown()) {
            redissonClient.shutdown();
            logger.info("Redisson distributed lock client closed");
        }
        healthy = false;
    }
}

