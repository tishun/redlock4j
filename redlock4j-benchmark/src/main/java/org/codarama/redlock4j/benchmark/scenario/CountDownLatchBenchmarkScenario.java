/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.scenario;

import org.codarama.redlock4j.benchmark.client.CountDownLatchBenchmarkClient;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Benchmark scenario for CountDownLatch - measures coordination throughput.
 */
public class CountDownLatchBenchmarkScenario {

    private static final Logger logger = LoggerFactory.getLogger(CountDownLatchBenchmarkScenario.class);

    private final BenchmarkConfiguration config;
    private final RedisClusterManager clusterManager;
    private final int latchCount;

    public CountDownLatchBenchmarkScenario(BenchmarkConfiguration config, RedisClusterManager clusterManager,
            int latchCount) {
        this.config = config;
        this.clusterManager = clusterManager;
        this.latchCount = latchCount;
    }

    public BenchmarkResult run(Supplier<CountDownLatchBenchmarkClient> clientSupplier) {
        String implType = clientSupplier.get().getImplementationType();
        logger.info("=== Starting CountDownLatch benchmark for {} (latch count: {}) ===", implType, latchCount);

        CountDownLatchBenchmarkClient client = clientSupplier.get();
        client.initialize(config, clusterManager);

        BenchmarkResult result = new BenchmarkResult("coordinator", implType);
        List<Long> latencies = new ArrayList<>();

        int workers = config.getClientCount();
        long endTime = System.currentTimeMillis() + config.getBenchmarkDuration().toMillis();
        AtomicInteger completedLatches = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(workers + 1);

        try {
            while (System.currentTimeMillis() < endTime) {
                String latchName = "latch-" + completedLatches.get();
                long startTime = System.nanoTime();

                // Create latch
                client.createLatch(latchName, latchCount);

                // Start countdown workers
                CountDownLatch localLatch = new CountDownLatch(latchCount);
                for (int i = 0; i < latchCount; i++) {
                    executor.submit(() -> {
                        try {
                            Thread.sleep(10); // Simulate work
                            client.countDown(latchName);
                            localLatch.countDown();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }

                // Wait for completion
                boolean completed = client.await(latchName, 5, TimeUnit.SECONDS);
                long latencyNanos = System.nanoTime() - startTime;

                if (completed) {
                    result.recordLockAcquisition(true, latencyNanos, 0);
                    latencies.add(latencyNanos / 1000);
                    completedLatches.incrementAndGet();
                } else {
                    result.recordLockAcquisition(false, latencyNanos, 0);
                }

                localLatch.await(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
            client.close();
        }

        result.complete();
        logger.info("=== CountDownLatch benchmark for {} completed: {} latches ===", implType, completedLatches.get());
        return result;
    }
}

