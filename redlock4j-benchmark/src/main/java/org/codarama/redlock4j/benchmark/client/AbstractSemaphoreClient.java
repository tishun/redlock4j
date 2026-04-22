/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.infrastructure.RedisResultsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for semaphore benchmark clients.
 */
public abstract class AbstractSemaphoreClient implements SemaphoreBenchmarkClient {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public BenchmarkResult runBenchmark(String clientId, BenchmarkConfiguration config, RedisResultsStore resultsStore) {
        BenchmarkResult result = new BenchmarkResult(clientId, getImplementationType());
        List<Long> waitTimes = new ArrayList<>();

        long endTime = System.currentTimeMillis() + config.getBenchmarkDuration().toMillis();
        long workTimeNanos = config.getWorkSimulationTime().toNanos();

        logger.info("Client {} ({}) starting semaphore benchmark", clientId, getImplementationType());

        while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
            long waitStartNanos = System.nanoTime();
            boolean acquired = false;

            try {
                acquired = tryAcquire(config.getLockAcquisitionTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            long waitEndNanos = System.nanoTime();
            long waitTimeNanos = waitEndNanos - waitStartNanos;

            if (acquired) {
                try {
                    long sequence = resultsStore.getNextSequence();
                    resultsStore.recordLockAcquisition(clientId, System.currentTimeMillis(), sequence);

                    long workStart = System.nanoTime();
                    simulateWork(workTimeNanos);
                    long holdTimeNanos = System.nanoTime() - workStart;

                    result.recordLockAcquisition(true, waitTimeNanos, holdTimeNanos);
                    waitTimes.add(waitTimeNanos / 1000);
                } finally {
                    resultsStore.recordLockRelease(clientId);
                    release();
                }
            } else {
                result.recordLockAcquisition(false, waitTimeNanos, 0);
            }
        }

        result.complete();
        result.setLatencyPercentiles(calculatePercentiles(waitTimes));
        resultsStore.storeResult(result);

        logger.info("Client {} completed: {}", clientId, result);
        return result;
    }

    protected void simulateWork(long durationNanos) {
        long endTime = System.nanoTime() + durationNanos;
        long counter = 0;
        while (System.nanoTime() < endTime) {
            counter++;
            if (counter % 1000 == 0) Thread.yield();
        }
    }

    private Map<String, Long> calculatePercentiles(List<Long> values) {
        Map<String, Long> percentiles = new HashMap<>();
        if (values.isEmpty()) return percentiles;
        Collections.sort(values);
        int size = values.size();
        percentiles.put("p50", values.get((int) (size * 0.50)));
        percentiles.put("p75", values.get((int) (size * 0.75)));
        percentiles.put("p90", values.get(Math.min((int) (size * 0.90), size - 1)));
        percentiles.put("p95", values.get(Math.min((int) (size * 0.95), size - 1)));
        percentiles.put("p99", values.get(Math.min((int) (size * 0.99), size - 1)));
        percentiles.put("max", values.get(size - 1));
        long sum = 0;
        for (Long v : values) sum += v;
        percentiles.put("mean", sum / size);
        return percentiles;
    }
}

