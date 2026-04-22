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
import java.util.concurrent.TimeUnit;

/**
 * Semaphore client using redlock4j with single node.
 */
public class Redlock4jSingleNodeSemaphoreClient extends AbstractSemaphoreClient {

    private static final String IMPLEMENTATION_TYPE = "redlock4j-singlenode";
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
        // Single node is automatically detected - no flag needed
        RedisClusterManager.RedisNodeInfo primaryNode = clusterManager.getNodeInfos().get(0);

        RedlockConfiguration redlockConfig = RedlockConfiguration.builder()
                .addRedisNode(primaryNode.getHost(), primaryNode.getPort())
                .defaultLockTimeout(config.getLockTimeout())
                .lockAcquisitionTimeout(config.getLockAcquisitionTimeout())
                .retryDelay(Duration.ofMillis(50))
                .maxRetryAttempts(1000)
                .build();

        try {
            redlockManager = RedlockManager.withLettuce(redlockConfig);
            semaphore = redlockManager.createSemaphore(SEMAPHORE_NAME, permits);
            healthy = redlockManager.isHealthy();
            logger.info("Redlock4j singlenode semaphore initialized with {} permits, healthy={}", permits, healthy);
        } catch (Exception e) {
            logger.error("Failed to initialize Redlock4j singlenode semaphore: {}", e.getMessage());
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
            logger.info("Redlock4j singlenode semaphore client closed");
        }
        healthy = false;
    }
}

