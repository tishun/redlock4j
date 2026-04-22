/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.benchmark.infrastructure.RedisResultsStore;

import java.util.concurrent.locks.Lock;

/**
 * Interface for distributed lock benchmark client implementations.
 */
public interface DistributedLockBenchmarkClient extends AutoCloseable {

    String getImplementationType();

    void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager);

    Lock getLock(String resourceName);

    boolean isHealthy();

    BenchmarkResult runBenchmark(String clientId, BenchmarkConfiguration config, RedisResultsStore resultsStore);

    @Override
    void close();
}

