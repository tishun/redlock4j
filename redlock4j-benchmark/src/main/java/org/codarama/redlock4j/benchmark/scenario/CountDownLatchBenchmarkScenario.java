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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        long now = System.currentTimeMillis();
        long warmupEnd = now + config.getWarmupDuration().toMillis();
        long endTime = warmupEnd + config.getBenchmarkDuration().toMillis();
        AtomicInteger completedLatches = new AtomicInteger(0);
        boolean[] inWarmup = { config.getWarmupDuration().toMillis() > 0 };

        ExecutorService executor = Executors.newFixedThreadPool(workers + 1);

        try {
            while (System.currentTimeMillis() < endTime) {
                if (inWarmup[0] && System.currentTimeMillis() >= warmupEnd) {
                    inWarmup[0] = false;
                    latencies.clear();
                    result.reset();
                    completedLatches.set(0);
                    logger.info("CountDownLatch warmup complete for {}, beginning measurement", implType);
                }
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
        result.setLatencyPercentiles(calculatePercentiles(latencies));
        logger.info("=== CountDownLatch benchmark for {} completed: {} latches ===", implType, completedLatches.get());
        return result;
    }

    private Map<String, Long> calculatePercentiles(List<Long> values) {
        Map<String, Long> percentiles = new HashMap<>();
        if (values.isEmpty()) return percentiles;

        Collections.sort(values);
        int size = values.size();

        percentiles.put("p50", values.get((int) (size * 0.50)));
        percentiles.put("p75", values.get((int) (size * 0.75)));
        percentiles.put("p90", values.get(Math.min((int) (size * 0.90), size - 1)));
        percentiles.put("p95", values.get(Math.min((int) (size * 0.95), size - 1)));
        percentiles.put("p99", values.get(Math.min((int) (size * 0.99), size - 1)));
        percentiles.put("max", values.get(size - 1));

        long sum = 0;
        for (Long v : values) sum += v;
        percentiles.put("mean", sum / size);

        return percentiles;
    }
}

