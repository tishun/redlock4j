/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.redisson.Redisson;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * CountDownLatch client using Redisson's RCountDownLatch.
 */
public class RedissonCountDownLatchClient implements CountDownLatchBenchmarkClient {

    private static final String IMPLEMENTATION_TYPE = "redisson";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private RedissonClient redissonClient;
    private final Map<String, RCountDownLatch> latches = new ConcurrentHashMap<>();
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

        try {
            redissonClient = Redisson.create(redissonConfig);
            healthy = true;
            logger.info("Redisson CountDownLatch client initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize Redisson CountDownLatch: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public void createLatch(String name, int count) {
        RCountDownLatch latch = redissonClient.getCountDownLatch(name);
        latch.trySetCount(count);
        latches.put(name, latch);
    }

    @Override
    public void countDown(String name) {
        RCountDownLatch latch = latches.get(name);
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public boolean await(String name, long timeout, TimeUnit unit) throws InterruptedException {
        RCountDownLatch latch = latches.get(name);
        return latch != null && latch.await(timeout, unit);
    }

    @Override
    public long getCount(String name) {
        RCountDownLatch latch = latches.get(name);
        return latch != null ? latch.getCount() : -1;
    }

    @Override
    public boolean isHealthy() {
        return healthy && redissonClient != null && !redissonClient.isShutdown();
    }

    @Override
    public void close() {
        latches.clear();
        if (redissonClient != null && !redissonClient.isShutdown()) {
            redissonClient.shutdown();
            logger.info("Redisson CountDownLatch client closed");
        }
        healthy = false;
    }
}

