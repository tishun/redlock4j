/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.benchmark.infrastructure.RedisResultsStore;

import java.io.Closeable;
import java.util.concurrent.locks.Lock;

/**
 * Common interface for all fair lock benchmark client implementations.
 * Each implementation wraps a specific locking library (Redisson, redlock4j-lettuce, redlock4j-jedis).
 */
public interface FairLockBenchmarkClient extends Closeable {

    /**
     * Returns the implementation type name (e.g., "redisson", "redlock4j-lettuce", "redlock4j-jedis").
     */
    String getImplementationType();

    /**
     * Initializes the client with the given configuration and Redis cluster.
     */
    void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager);

    /**
     * Gets the fair lock instance for the configured resource name.
     */
    Lock getFairLock(String resourceName);

    /**
     * Runs the benchmark loop for the specified duration.
     * This method should be called by multiple threads to simulate concurrent clients.
     *
     * @param clientId Unique identifier for this client
     * @param config Benchmark configuration
     * @param resultsStore Store for recording results
     * @return Benchmark results for this client
     */
    BenchmarkResult runBenchmark(String clientId, BenchmarkConfiguration config, RedisResultsStore resultsStore);

    /**
     * Returns whether this client is properly connected and ready.
     */
    boolean isHealthy();

    /**
     * Clean up resources.
     */
    @Override
    void close();
}

