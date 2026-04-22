/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.benchmark.infrastructure.RedisResultsStore;

import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Interface for MultiLock benchmark client implementations.
 * Tests atomic acquisition of multiple resources.
 */
public interface MultiLockBenchmarkClient extends AutoCloseable {

    /**
     * Returns the implementation type name.
     */
    String getImplementationType();

    /**
     * Initializes the client with the given configuration.
     */
    void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager);

    /**
     * Gets a multi-lock for the specified resource names.
     */
    Lock getMultiLock(List<String> resourceNames);

    /**
     * Checks if the client is healthy and ready.
     */
    boolean isHealthy();

    /**
     * Runs the benchmark and returns results.
     */
    BenchmarkResult runBenchmark(String clientId, BenchmarkConfiguration config, RedisResultsStore resultsStore);

    @Override
    void close();
}

