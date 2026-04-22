/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;

import java.util.concurrent.TimeUnit;

/**
 * Interface for CountDownLatch benchmark client implementations.
 */
public interface CountDownLatchBenchmarkClient extends AutoCloseable {

    String getImplementationType();

    void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager);

    /** Creates a new latch with the given count */
    void createLatch(String name, int count);

    /** Decrements the count */
    void countDown(String name);

    /** Waits for count to reach zero */
    boolean await(String name, long timeout, TimeUnit unit) throws InterruptedException;

    /** Gets current count */
    long getCount(String name);

    boolean isHealthy();

    @Override
    void close();
}

