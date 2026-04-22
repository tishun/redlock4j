/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.scenario;

import org.codarama.redlock4j.benchmark.client.FairLockBenchmarkClient;
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
 * Runs a fair lock benchmark scenario with multiple concurrent clients.
 */
public class FairLockBenchmarkScenario {

    private static final Logger logger = LoggerFactory.getLogger(FairLockBenchmarkScenario.class);

    private final BenchmarkConfiguration config;
    private final RedisClusterManager clusterManager;
    private final RedisResultsStore resultsStore;

    public FairLockBenchmarkScenario(BenchmarkConfiguration config, RedisClusterManager clusterManager,
            RedisResultsStore resultsStore) {
        this.config = config;
        this.clusterManager = clusterManager;
        this.resultsStore = resultsStore;
    }

    /**
     * Runs benchmark for a specific client implementation.
     * 
     * @param clientSupplier Supplier that creates new client instances
     * @return List of results from all clients
     */
    public List<BenchmarkResult> run(Supplier<FairLockBenchmarkClient> clientSupplier) {
        String implType = clientSupplier.get().getImplementationType();
        logger.info("=== Starting benchmark for {} with {} clients ===", implType, config.getClientCount());

        // Clear previous results
        resultsStore.clear();

        List<FairLockBenchmarkClient> clients = new ArrayList<>();
        List<BenchmarkResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(config.getClientCount());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(config.getClientCount());

        try {
            // Initialize all clients
            for (int i = 0; i < config.getClientCount(); i++) {
                FairLockBenchmarkClient client = clientSupplier.get();
                client.initialize(config, clusterManager);
                clients.add(client);

                if (!client.isHealthy()) {
                    logger.warn("Client {} not healthy after initialization", i);
                }
            }

            // Submit benchmark tasks
            for (int i = 0; i < config.getClientCount(); i++) {
                final int clientIndex = i;
                final FairLockBenchmarkClient client = clients.get(i);
                final String clientId = implType + "-client-" + clientIndex;

                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all clients to be ready
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

            // Warmup phase
            logger.info("Warmup phase: {} seconds", config.getWarmupDuration().getSeconds());
            Thread.sleep(config.getWarmupDuration().toMillis());

            // Start all clients simultaneously
            logger.info("Starting benchmark execution...");
            long startTime = System.currentTimeMillis();
            startLatch.countDown();

            // Wait for completion with progress logging
            while (!completeLatch.await(config.getReportingInterval().toMillis(), TimeUnit.MILLISECONDS)) {
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = config.getBenchmarkDuration().toMillis() - elapsed;
                logger.info("Progress: {} elapsed, {} remaining", formatDuration(elapsed), formatDuration(remaining));
            }

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("=== Benchmark for {} completed in {} ===", implType, formatDuration(totalTime));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Benchmark interrupted");
        } finally {
            executor.shutdownNow();
            clients.forEach(FairLockBenchmarkClient::close);
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

