/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;

import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Distributed lock client using Spring Integration's RedisLockRegistry.
 */
public class SpringIntegrationDistributedLockClient extends AbstractDistributedLockClient {

    private static final String IMPLEMENTATION_TYPE = "spring-integration";
    private static final String REGISTRY_KEY = "benchmark-locks";

    private LettuceConnectionFactory connectionFactory;
    private RedisLockRegistry lockRegistry;
    private boolean healthy = false;

    @Override
    public String getImplementationType() {
        return IMPLEMENTATION_TYPE;
    }

    @Override
    public void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager) {
        List<RedisClusterManager.RedisNodeInfo> nodes = clusterManager.getNodeInfos();
        RedisClusterManager.RedisNodeInfo primaryNode = nodes.get(0);

        try {
            // Configure Spring Data Redis connection
            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
            redisConfig.setHostName(primaryNode.getHost());
            redisConfig.setPort(primaryNode.getPort());

            // Create Lettuce connection factory
            connectionFactory = new LettuceConnectionFactory(redisConfig);
            connectionFactory.afterPropertiesSet();

            // Create the lock registry with expiration time matching the lock timeout
            lockRegistry = new RedisLockRegistry(
                    connectionFactory,
                    REGISTRY_KEY,
                    config.getLockTimeout().toMillis()
            );

            healthy = true;
            logger.info("Spring Integration RedisLockRegistry initialized, connected to {}",
                    primaryNode.getAddress());
        } catch (Exception e) {
            logger.error("Failed to initialize Spring Integration client: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public Lock getLock(String resourceName) {
        if (lockRegistry == null) {
            throw new IllegalStateException("Client not initialized");
        }
        return lockRegistry.obtain(resourceName);
    }

    @Override
    public boolean isHealthy() {
        return healthy && lockRegistry != null && connectionFactory != null;
    }

    @Override
    public void close() {
        if (lockRegistry != null) {
            try {
                lockRegistry.destroy();
            } catch (Exception e) {
                logger.warn("Error destroying lock registry: {}", e.getMessage());
            }
            lockRegistry = null;
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
            connectionFactory = null;
        }
        healthy = false;
        logger.info("Spring Integration client closed");
    }
}
