/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.RedlockCountDownLatch;
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * CountDownLatch client using redlock4j's RedlockCountDownLatch.
 */
public class Redlock4jCountDownLatchClient implements CountDownLatchBenchmarkClient {

    private static final String IMPLEMENTATION_TYPE = "redlock4j";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private RedlockManager redlockManager;
    private final Map<String, RedlockCountDownLatch> latches = new ConcurrentHashMap<>();
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
                .maxRetryAttempts(1000);

        for (RedisClusterManager.RedisNodeInfo node : nodes) {
            configBuilder.addRedisNode(node.getHost(), node.getPort());
        }

        try {
            redlockManager = RedlockManager.withLettuce(configBuilder.build());
            healthy = redlockManager.isHealthy();
            logger.info("Redlock4j CountDownLatch client initialized, healthy={}", healthy);
        } catch (Exception e) {
            logger.error("Failed to initialize Redlock4j CountDownLatch: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public void createLatch(String name, int count) {
        RedlockCountDownLatch latch = redlockManager.createCountDownLatch(name, count);
        latches.put(name, latch);
    }

    @Override
    public void countDown(String name) {
        RedlockCountDownLatch latch = latches.get(name);
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public boolean await(String name, long timeout, TimeUnit unit) throws InterruptedException {
        RedlockCountDownLatch latch = latches.get(name);
        return latch != null && latch.await(Duration.of(timeout, unit.toChronoUnit()));
    }

    @Override
    public long getCount(String name) {
        RedlockCountDownLatch latch = latches.get(name);
        return latch != null ? latch.getCount() : -1;
    }

    @Override
    public boolean isHealthy() {
        return healthy && redlockManager != null && redlockManager.isHealthy();
    }

    @Override
    public void close() {
        latches.clear();
        if (redlockManager != null) {
            redlockManager.close();
            logger.info("Redlock4j CountDownLatch client closed");
        }
        healthy = false;
    }
}

