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
 * MultiLock client using redlock4j with single node for fair comparison with Redisson.
 */
public class Redlock4jSingleNodeMultiLockClient extends AbstractMultiLockClient {

    private static final String IMPLEMENTATION_TYPE = "redlock4j-singlenode";

    private RedlockManager redlockManager;
    private boolean healthy = false;

    @Override
    public String getImplementationType() {
        return IMPLEMENTATION_TYPE;
    }

    @Override
    public void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager) {
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
            healthy = redlockManager.isHealthy();
            logger.info("Redlock4j SingleNode MultiLock client initialized with node {}, healthy={}",
                    primaryNode.getAddress(), healthy);
        } catch (Exception e) {
            logger.error("Failed to initialize Redlock4j SingleNode MultiLock client: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public Lock getMultiLock(List<String> resourceNames) {
        if (redlockManager == null) {
            throw new IllegalStateException("Client not initialized");
        }
        return redlockManager.createMultiLock(resourceNames);
    }

    @Override
    public boolean isHealthy() {
        return healthy && redlockManager != null && redlockManager.isHealthy();
    }

    @Override
    public void close() {
        if (redlockManager != null) {
            redlockManager.close();
            logger.info("Redlock4j SingleNode MultiLock client closed");
        }
        healthy = false;
    }
}

