/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.RedlockSemaphore;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Semaphore client using redlock4j's RedlockSemaphore.
 */
public class Redlock4jSemaphoreClient extends AbstractSemaphoreClient {

    private static final String IMPLEMENTATION_TYPE = "redlock4j";
    private static final String SEMAPHORE_NAME = "benchmark-semaphore";

    private RedlockManager redlockManager;
    private RedlockSemaphore semaphore;
    private boolean healthy = false;

    @Override
    public String getImplementationType() {
        return IMPLEMENTATION_TYPE;
    }

    @Override
    public void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager, int permits) {
        List<RedisClusterManager.RedisNodeInfo> nodes = clusterManager.getNodeInfos();

        RedlockConfiguration.Builder configBuilder = RedlockConfiguration.builder()
                .defaultLockTimeout(config.getLockTimeout())
                .lockAcquisitionTimeout(config.getLockAcquisitionTimeout())
                .retryDelay(Duration.ofMillis(50))
                .maxRetryAttempts(1000);

        for (RedisClusterManager.RedisNodeInfo node : nodes) {
            configBuilder.addRedisNode(node.getHost(), node.getPort());
        }

        try {
            redlockManager = RedlockManager.withLettuce(configBuilder.build());
            semaphore = redlockManager.createSemaphore(SEMAPHORE_NAME, permits);
            healthy = redlockManager.isHealthy();
            logger.info("Redlock4j semaphore client initialized with {} permits, healthy={}", permits, healthy);
        } catch (Exception e) {
            logger.error("Failed to initialize Redlock4j semaphore: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return semaphore.tryAcquire(1, Duration.of(timeout, unit.toChronoUnit()));
    }

    @Override
    public void release() {
        semaphore.release();
    }

    @Override
    public boolean isHealthy() {
        return healthy && redlockManager != null && redlockManager.isHealthy();
    }

    @Override
    public void close() {
        if (redlockManager != null) {
            redlockManager.close();
            logger.info("Redlock4j semaphore client closed");
        }
        healthy = false;
    }
}

