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
import java.util.concurrent.locks.ReadWriteLock;

/**
 * ReadWriteLock client using redlock4j with Lettuce driver (3-node mode).
 */
public class Redlock4jReadWriteLockClient extends AbstractReadWriteLockClient {

    private static final String IMPLEMENTATION_TYPE = "redlock4j-rwlock";

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
                .maxRetryDelay(Duration.ofMillis(500))
                .retryDelayMultiplier(2.0)
                .retryDelayJitterRatio(0.5)
                .maxRetryAttempts(1000);

        for (RedisClusterManager.RedisNodeInfo node : nodes) {
            configBuilder.addRedisNode(node.getHost(), node.getPort());
        }

        try {
            redlockManager = RedlockManager.withLettuce(configBuilder.build());
            healthy = redlockManager.isHealthy();
            logger.info("Redlock4j ReadWriteLock client initialized with {} nodes, healthy={}",
                    nodes.size(), healthy);
        } catch (Exception e) {
            logger.error("Failed to initialize Redlock4j ReadWriteLock client: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public ReadWriteLock getReadWriteLock(String resourceName) {
        if (redlockManager == null) {
            throw new IllegalStateException("Client not initialized");
        }
        return redlockManager.createReadWriteLock(resourceName);
    }

    @Override
    public boolean isHealthy() {
        return healthy && redlockManager != null && redlockManager.isHealthy();
    }

    @Override
    public void close() {
        if (redlockManager != null) {
            redlockManager.close();
            logger.info("Redlock4j ReadWriteLock client closed");
        }
        healthy = false;
    }
}

