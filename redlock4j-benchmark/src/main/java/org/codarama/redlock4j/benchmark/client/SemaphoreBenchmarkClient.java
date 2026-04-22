/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.benchmark.infrastructure.RedisResultsStore;

/**
 * Interface for Semaphore benchmark client implementations.
 */
public interface SemaphoreBenchmarkClient extends AutoCloseable {

    String getImplementationType();

    void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager, int permits);

    boolean tryAcquire(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException;

    void release();

    boolean isHealthy();

    BenchmarkResult runBenchmark(String clientId, BenchmarkConfiguration config, RedisResultsStore resultsStore);

    @Override
    void close();
}

