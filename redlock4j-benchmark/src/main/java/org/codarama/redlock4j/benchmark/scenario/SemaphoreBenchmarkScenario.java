/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.scenario;

import org.codarama.redlock4j.benchmark.client.SemaphoreBenchmarkClient;
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
 * Runs a semaphore benchmark scenario with multiple concurrent clients.
 */
public class SemaphoreBenchmarkScenario {

    private static final Logger logger = LoggerFactory.getLogger(SemaphoreBenchmarkScenario.class);

    private final BenchmarkConfiguration config;
    private final RedisClusterManager clusterManager;
    private final RedisResultsStore resultsStore;
    private final int permits;

    public SemaphoreBenchmarkScenario(BenchmarkConfiguration config, RedisClusterManager clusterManager,
            RedisResultsStore resultsStore, int permits) {
        this.config = config;
        this.clusterManager = clusterManager;
        this.resultsStore = resultsStore;
        this.permits = permits;
    }

    public List<BenchmarkResult> run(Supplier<SemaphoreBenchmarkClient> clientSupplier) {
        String implType = clientSupplier.get().getImplementationType();
        logger.info("=== Starting semaphore benchmark for {} ({} clients, {} permits) ===",
                implType, config.getClientCount(), permits);

        resultsStore.clear();

        List<SemaphoreBenchmarkClient> clients = new ArrayList<>();
        List<BenchmarkResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(config.getClientCount());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(config.getClientCount());

        try {
            for (int i = 0; i < config.getClientCount(); i++) {
                SemaphoreBenchmarkClient client = clientSupplier.get();
                client.initialize(config, clusterManager, permits);
                clients.add(client);
            }

            for (int i = 0; i < config.getClientCount(); i++) {
                final int clientIndex = i;
                final SemaphoreBenchmarkClient client = clients.get(i);
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

            logger.info("Warmup phase: {} seconds", config.getWarmupDuration().getSeconds());
            Thread.sleep(config.getWarmupDuration().toMillis());

            logger.info("Starting benchmark...");
            long startTime = System.currentTimeMillis();
            startLatch.countDown();

            while (!completeLatch.await(config.getReportingInterval().toMillis(), TimeUnit.MILLISECONDS)) {
                long elapsed = System.currentTimeMillis() - startTime;
                logger.info("Progress: {}s elapsed", elapsed / 1000);
            }

            logger.info("=== Semaphore benchmark for {} completed ===", implType);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
            clients.forEach(SemaphoreBenchmarkClient::close);
        }

        return results;
    }
}

