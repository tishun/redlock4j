/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.scenario;

import org.codarama.redlock4j.benchmark.client.ReadWriteLockBenchmarkClient;
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
 * Runs a read-write lock benchmark with configurable reader/writer ratio.
 */
public class ReadWriteLockBenchmarkScenario {

    private static final Logger logger = LoggerFactory.getLogger(ReadWriteLockBenchmarkScenario.class);

    private final BenchmarkConfiguration config;
    private final RedisClusterManager clusterManager;
    private final RedisResultsStore resultsStore;
    private final int writerCount;
    private final int readerCount;

    public ReadWriteLockBenchmarkScenario(BenchmarkConfiguration config, RedisClusterManager clusterManager,
            RedisResultsStore resultsStore, int writerCount, int readerCount) {
        this.config = config;
        this.clusterManager = clusterManager;
        this.resultsStore = resultsStore;
        this.writerCount = writerCount;
        this.readerCount = readerCount;
    }

    public List<BenchmarkResult> run(Supplier<ReadWriteLockBenchmarkClient> clientSupplier) {
        String implType = clientSupplier.get().getImplementationType();
        int totalClients = writerCount + readerCount;
        logger.info("=== Starting RWLock benchmark for {} ({} writers, {} readers) ===", 
                implType, writerCount, readerCount);

        resultsStore.clear();

        List<ReadWriteLockBenchmarkClient> clients = new ArrayList<>();
        List<BenchmarkResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(totalClients);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(totalClients);

        try {
            for (int i = 0; i < totalClients; i++) {
                ReadWriteLockBenchmarkClient client = clientSupplier.get();
                client.initialize(config, clusterManager);
                clients.add(client);
            }

            // Start writers first, then readers
            for (int i = 0; i < totalClients; i++) {
                final int clientIndex = i;
                final ReadWriteLockBenchmarkClient client = clients.get(i);
                final boolean isWriter = i < writerCount;
                final String role = isWriter ? "writer" : "reader";
                final String clientId = implType + "-" + role + "-" + (isWriter ? i : (i - writerCount));

                executor.submit(() -> {
                    try {
                        startLatch.await();
                        BenchmarkResult result = client.runBenchmark(clientId, config, resultsStore, isWriter);
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

            logger.info("Warmup phase: {} seconds", config.getWarmupDuration().getSeconds());
            Thread.sleep(config.getWarmupDuration().toMillis());

            logger.info("Starting benchmark execution...");
            long startTime = System.currentTimeMillis();
            startLatch.countDown();

            while (!completeLatch.await(config.getReportingInterval().toMillis(), TimeUnit.MILLISECONDS)) {
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = config.getBenchmarkDuration().toMillis() - elapsed;
                logger.info("Progress: {} elapsed, {} remaining", formatDuration(elapsed), formatDuration(remaining));
            }

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("=== RWLock benchmark for {} completed in {} ===", implType, formatDuration(totalTime));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Benchmark interrupted");
        } finally {
            executor.shutdownNow();
            clients.forEach(ReadWriteLockBenchmarkClient::close);
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

