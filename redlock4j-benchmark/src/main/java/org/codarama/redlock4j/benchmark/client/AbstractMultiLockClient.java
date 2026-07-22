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
import java.util.concurrent.locks.Lock;

/**
 * Abstract base class for multi-lock benchmark clients.
 */
public abstract class AbstractMultiLockClient implements MultiLockBenchmarkClient {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public BenchmarkResult runBenchmark(String clientId, BenchmarkConfiguration config, RedisResultsStore resultsStore) {
        BenchmarkResult result = new BenchmarkResult(clientId, getImplementationType());
        List<Long> waitTimes = new ArrayList<>();

        // Generate resource names for multi-lock (e.g., account:1, account:2, account:3)
        List<String> resourceNames = generateResourceNames(config.getMultiLockResourceCount());
        Lock multiLock = getMultiLock(resourceNames);

        long now = System.currentTimeMillis();
        long warmupEnd = now + config.getWarmupDuration().toMillis();
        long endTime = warmupEnd + config.getBenchmarkDuration().toMillis();
        long workTimeNanos = config.getWorkSimulationTime().toNanos();
        boolean inWarmup = config.getWarmupDuration().toMillis() > 0;

        logger.info("Client {} ({}) starting multi-lock benchmark with {} resources (warmup {}s + measure {}s)",
                clientId, getImplementationType(), resourceNames.size(),
                config.getWarmupDuration().getSeconds(), config.getBenchmarkDuration().getSeconds());

        while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
            if (inWarmup && System.currentTimeMillis() >= warmupEnd) {
                inWarmup = false;
                waitTimes.clear();
                result.reset();
                logger.info("Client {} warmup complete, beginning measurement", clientId);
            }
            long waitStartNanos = System.nanoTime();
            boolean acquired = false;

            try {
                acquired = multiLock.tryLock(config.getLockAcquisitionTimeout().toMillis(), TimeUnit.MILLISECONDS);
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

                    String currentHolder = resultsStore.getCurrentLockHolder();
                    if (currentHolder != null && !currentHolder.equals(clientId)) {
                        result.recordCorrectnessViolation();
                        logger.error("CORRECTNESS VIOLATION: {} holds lock but {} also acquired it!",
                                currentHolder, clientId);
                    }

                    long workStart = System.nanoTime();
                    simulateWork(workTimeNanos);
                    long holdTimeNanos = System.nanoTime() - workStart;

                    result.recordLockAcquisition(true, waitTimeNanos, holdTimeNanos);
                    waitTimes.add(waitTimeNanos / 1000);
                } finally {
                    resultsStore.recordLockRelease(clientId);
                    multiLock.unlock();
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

    protected List<String> generateResourceNames(int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add("resource:" + i);
        }
        return names;
    }

    protected void simulateWork(long durationNanos) {
        long endTime = System.nanoTime() + durationNanos;
        long counter = 0;
        while (System.nanoTime() < endTime) {
            counter++;
            if (counter % 1000 == 0) {
                Thread.yield();
            }
        }
    }

    private Map<String, Long> calculatePercentiles(List<Long> values) {
        Map<String, Long> percentiles = new HashMap<>();
        if (values.isEmpty()) {
            return percentiles;
        }

        Collections.sort(values);
        int size = values.size();

        percentiles.put("p50", values.get((int) (size * 0.50)));
        percentiles.put("p75", values.get((int) (size * 0.75)));
        percentiles.put("p90", values.get(Math.min((int) (size * 0.90), size - 1)));
        percentiles.put("p95", values.get(Math.min((int) (size * 0.95), size - 1)));
        percentiles.put("p99", values.get(Math.min((int) (size * 0.99), size - 1)));
        percentiles.put("p999", values.get(Math.min((int) (size * 0.999), size - 1)));
        percentiles.put("max", values.get(size - 1));

        long sum = 0;
        for (Long v : values) {
            sum += v;
        }
        percentiles.put("mean", sum / size);

        return percentiles;
    }
}

