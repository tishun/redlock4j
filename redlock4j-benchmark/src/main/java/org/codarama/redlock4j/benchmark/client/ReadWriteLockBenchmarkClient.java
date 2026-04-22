/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import org.codarama.redlock4j.benchmark.infrastructure.RedisResultsStore;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * Interface for ReadWriteLock benchmark client implementations.
 */
public interface ReadWriteLockBenchmarkClient extends AutoCloseable {

    String getImplementationType();

    void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager);

    ReadWriteLock getReadWriteLock(String resourceName);

    boolean isHealthy();

    BenchmarkResult runBenchmark(String clientId, BenchmarkConfiguration config, 
            RedisResultsStore resultsStore, boolean isWriter);

    @Override
    void close();
}

