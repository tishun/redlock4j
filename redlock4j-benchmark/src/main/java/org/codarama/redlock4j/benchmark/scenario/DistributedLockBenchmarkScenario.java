/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.scenario;

import org.codarama.redlock4j.benchmark.client.DistributedLockBenchmarkClient;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.benchmark.infrastructure.RedisResultsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Runs a distributed lock benchmark scenario with multiple concurrent clients.
 */
public class DistributedLockBenchmarkScenario {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockBenchmarkScenario.class);

    private final BenchmarkConfiguration config;
    private final RedisClusterManager clusterManager;
    private final RedisResultsStore resultsStore;

    public DistributedLockBenchmarkScenario(BenchmarkConfiguration config, RedisClusterManager clusterManager,
            RedisResultsStore resultsStore) {
        this.config = config;
        this.clusterManager = clusterManager;
        this.resultsStore = resultsStore;
    }

    public List<BenchmarkResult> run(Supplier<DistributedLockBenchmarkClient> clientSupplier) {
        String implType = clientSupplier.get().getImplementationType();
        logger.info("=== Starting distributed lock benchmark for {} with {} clients ===", 
                implType, config.getClientCount());

        resultsStore.clear();

        List<DistributedLockBenchmarkClient> clients = new ArrayList<>();
        List<BenchmarkResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(config.getClientCount());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(config.getClientCount());

        try {
            for (int i = 0; i < config.getClientCount(); i++) {
                DistributedLockBenchmarkClient client = clientSupplier.get();
                client.initialize(config, clusterManager);
                clients.add(client);
            }

            for (int i = 0; i < config.getClientCount(); i++) {
                final int clientIndex = i;
                final DistributedLockBenchmarkClient client = clients.get(i);
                final String clientId = implType + "-client-" + clientIndex;

                executor.submit(() -> {
                    try {
                        startLatch.await();
                        BenchmarkResult result = client.runBenchmark(clientId, config, resultsStore);
                        synchronized (results) {
                            results.add(result);
                        }
                    } catch (Exception e) {
                        logger.error("Client {} failed: {}", clientId, e.getMessage(), e);
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            logger.info("Starting benchmark execution (warmup {}s + measure {}s, clients do warmup-discard internally)...",
                    config.getWarmupDuration().getSeconds(), config.getBenchmarkDuration().getSeconds());
            long startTime = System.currentTimeMillis();
            startLatch.countDown();

            long totalDurationMs = config.getWarmupDuration().toMillis() + config.getBenchmarkDuration().toMillis();
            while (!completeLatch.await(config.getReportingInterval().toMillis(), TimeUnit.MILLISECONDS)) {
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = totalDurationMs - elapsed;
                logger.info("Progress: {} elapsed, {} remaining", formatDuration(elapsed), formatDuration(remaining));
            }

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("=== Distributed lock benchmark for {} completed in {} ===", implType, formatDuration(totalTime));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Benchmark interrupted");
        } finally {
            executor.shutdownNow();
            clients.forEach(DistributedLockBenchmarkClient::close);
        }

        return results;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%dm %ds", minutes, seconds);
    }
}

