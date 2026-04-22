/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.redisson.Redisson;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Semaphore client using Redisson's RSemaphore.
 */
public class RedissonSemaphoreClient extends AbstractSemaphoreClient {

    private static final String IMPLEMENTATION_TYPE = "redisson";
    private static final String SEMAPHORE_NAME = "benchmark-semaphore";

    private RedissonClient redissonClient;
    private RSemaphore semaphore;
    private boolean healthy = false;

    @Override
    public String getImplementationType() {
        return IMPLEMENTATION_TYPE;
    }

    @Override
    public void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager, int permits) {
        List<RedisClusterManager.RedisNodeInfo> nodes = clusterManager.getNodeInfos();

        Config redissonConfig = new Config();
        RedisClusterManager.RedisNodeInfo primaryNode = nodes.get(0);
        redissonConfig.useSingleServer()
                .setAddress(primaryNode.getRedisUrl())
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(20);

        try {
            redissonClient = Redisson.create(redissonConfig);
            semaphore = redissonClient.getSemaphore(SEMAPHORE_NAME);
            semaphore.trySetPermits(permits);
            healthy = true;
            logger.info("Redisson semaphore client initialized with {} permits", permits);
        } catch (Exception e) {
            logger.error("Failed to initialize Redisson semaphore: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return semaphore.tryAcquire(timeout, unit);
    }

    @Override
    public void release() {
        semaphore.release();
    }

    @Override
    public boolean isHealthy() {
        return healthy && redissonClient != null && !redissonClient.isShutdown();
    }

    @Override
    public void close() {
        if (redissonClient != null && !redissonClient.isShutdown()) {
            redissonClient.shutdown();
            logger.info("Redisson semaphore client closed");
        }
        healthy = false;
    }
}

