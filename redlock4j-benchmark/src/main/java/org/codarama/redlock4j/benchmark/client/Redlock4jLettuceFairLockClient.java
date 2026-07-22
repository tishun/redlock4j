/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Fair lock client implementation using redlock4j with Lettuce driver.
 */
public class Redlock4jLettuceFairLockClient extends AbstractFairLockClient {

    private static final String IMPLEMENTATION_TYPE = "redlock4j-lettuce";
    
    private RedlockManager redlockManager;
    private boolean healthy = false;

    @Override
    public String getImplementationType() {
        return IMPLEMENTATION_TYPE;
    }

    @Override
    public void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager) {
        List<RedisClusterManager.RedisNodeInfo> nodes = clusterManager.getNodeInfos();
        
        RedlockConfiguration.Builder configBuilder = RedlockConfiguration.builder()
                .defaultLockTimeout(config.getLockTimeout())
                .lockAcquisitionTimeout(config.getLockAcquisitionTimeout())
                .retryDelay(Duration.ofMillis(50))
                .maxRetryAttempts(1000) // High retry count for long benchmarks
                .usePolling(); // FairLock recommended; avoids keyspace-notification overhead

        for (RedisClusterManager.RedisNodeInfo node : nodes) {
            configBuilder.addRedisNode(node.getHost(), node.getPort());
        }

        RedlockConfiguration redlockConfig = configBuilder.build();

        try {
            redlockManager = RedlockManager.withLettuce(redlockConfig);
            healthy = redlockManager.isHealthy();
            logger.info("Redlock4j (Lettuce) client initialized with {} nodes, healthy={}", 
                    nodes.size(), healthy);
        } catch (Exception e) {
            logger.error("Failed to initialize Redlock4j (Lettuce) client: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public Lock getFairLock(String resourceName) {
        if (redlockManager == null) {
            throw new IllegalStateException("Client not initialized");
        }
        return redlockManager.createFairLock(resourceName);
    }

    @Override
    public boolean isHealthy() {
        return healthy && redlockManager != null && redlockManager.isHealthy();
    }

    @Override
    public void close() {
        if (redlockManager != null) {
            redlockManager.close();
            logger.info("Redlock4j (Lettuce) client closed");
        }
        healthy = false;
    }
}

