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
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Abstract base class for read-write lock benchmark clients.
 */
public abstract class AbstractReadWriteLockClient implements ReadWriteLockBenchmarkClient {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public BenchmarkResult runBenchmark(String clientId, BenchmarkConfiguration config,
            RedisResultsStore resultsStore, boolean isWriter) {
        String role = isWriter ? "writer" : "reader";
        BenchmarkResult result = new BenchmarkResult(clientId, getImplementationType() + "-" + role);
        List<Long> waitTimes = new ArrayList<>();

        ReadWriteLock rwLock = getReadWriteLock("benchmark-rwlock");
        Lock lock = isWriter ? rwLock.writeLock() : rwLock.readLock();

        long endTime = System.currentTimeMillis() + config.getBenchmarkDuration().toMillis();
        long workTimeNanos = config.getWorkSimulationTime().toNanos();

        logger.info("Client {} ({}) starting as {}", clientId, getImplementationType(), role);

        while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
            long waitStartNanos = System.nanoTime();
            boolean acquired = false;

            try {
                acquired = lock.tryLock(config.getLockAcquisitionTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            long waitEndNanos = System.nanoTime();
            long waitTimeNanos = waitEndNanos - waitStartNanos;

            if (acquired) {
                try {
                    if (isWriter) {
                        // Writers need exclusive access - record for validation
                        long sequence = resultsStore.getNextSequence();
                        resultsStore.recordLockAcquisition(clientId, System.currentTimeMillis(), sequence);
                    }

                    long workStart = System.nanoTime();
                    simulateWork(workTimeNanos);
                    long holdTimeNanos = System.nanoTime() - workStart;

                    result.recordLockAcquisition(true, waitTimeNanos, holdTimeNanos);
                    waitTimes.add(waitTimeNanos / 1000);
                } finally {
                    if (isWriter) {
                        resultsStore.recordLockRelease(clientId);
                    }
                    lock.unlock();
                }
            } else {
                result.recordLockAcquisition(false, waitTimeNanos, 0);
            }
        }

        result.complete();
        result.setLatencyPercentiles(calculatePercentiles(waitTimes));
        resultsStore.storeResult(result);

        logger.info("Client {} ({}) completed: {}", clientId, role, result);
        return result;
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

