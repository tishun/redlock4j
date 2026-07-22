/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.infrastructure;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds benchmark results for a single client.
 */
public class BenchmarkResult {

    private final String clientId;
    private final String implementationType;
    private volatile Instant startTime;
    private volatile Instant endTime;

    private final AtomicLong successfulLockAcquisitions = new AtomicLong(0);
    private final AtomicLong failedLockAcquisitions = new AtomicLong(0);
    private final AtomicLong totalLockHoldTimeNanos = new AtomicLong(0);
    private final AtomicLong totalLockWaitTimeNanos = new AtomicLong(0);
    private final AtomicLong correctnessViolations = new AtomicLong(0);
    private final AtomicLong fifoViolations = new AtomicLong(0);

    private final Map<String, Long> latencyPercentiles = new ConcurrentHashMap<>();

    public BenchmarkResult(String clientId, String implementationType) {
        this.clientId = clientId;
        this.implementationType = implementationType;
        this.startTime = Instant.now();
    }

    /**
     * Resets all accumulated counters and restarts the measurement clock.
     * Intended for use at the warmup/measurement boundary so only post-warmup
     * operations contribute to the reported metrics.
     */
    public void reset() {
        successfulLockAcquisitions.set(0);
        failedLockAcquisitions.set(0);
        totalLockHoldTimeNanos.set(0);
        totalLockWaitTimeNanos.set(0);
        correctnessViolations.set(0);
        fifoViolations.set(0);
        latencyPercentiles.clear();
        this.endTime = null;
        this.startTime = Instant.now();
    }

    public void recordLockAcquisition(boolean success, long waitTimeNanos, long holdTimeNanos) {
        if (success) {
            successfulLockAcquisitions.incrementAndGet();
            totalLockWaitTimeNanos.addAndGet(waitTimeNanos);
            totalLockHoldTimeNanos.addAndGet(holdTimeNanos);
        } else {
            failedLockAcquisitions.incrementAndGet();
        }
    }

    public void recordCorrectnessViolation() { correctnessViolations.incrementAndGet(); }
    public void recordFifoViolation() { fifoViolations.incrementAndGet(); }
    public void complete() { this.endTime = Instant.now(); }
    public void setLatencyPercentiles(Map<String, Long> percentiles) { this.latencyPercentiles.putAll(percentiles); }

    // Getters
    public String getClientId() { return clientId; }
    public String getImplementationType() { return implementationType; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public long getSuccessfulLockAcquisitions() { return successfulLockAcquisitions.get(); }
    public long getFailedLockAcquisitions() { return failedLockAcquisitions.get(); }
    public long getTotalLockHoldTimeNanos() { return totalLockHoldTimeNanos.get(); }
    public long getTotalLockWaitTimeNanos() { return totalLockWaitTimeNanos.get(); }
    public long getCorrectnessViolations() { return correctnessViolations.get(); }
    public long getFifoViolations() { return fifoViolations.get(); }
    public Map<String, Long> getLatencyPercentiles() { return latencyPercentiles; }

    public double getOpsPerSecond() {
        if (endTime == null) return 0;
        long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        if (durationMs == 0) return 0;
        return (successfulLockAcquisitions.get() * 1000.0) / durationMs;
    }

    public double getAverageWaitTimeMs() {
        long total = successfulLockAcquisitions.get();
        if (total == 0) return 0;
        return (totalLockWaitTimeNanos.get() / 1_000_000.0) / total;
    }

    public double getAverageHoldTimeMs() {
        long total = successfulLockAcquisitions.get();
        if (total == 0) return 0;
        return (totalLockHoldTimeNanos.get() / 1_000_000.0) / total;
    }

    public boolean isCorrect() {
        return correctnessViolations.get() == 0 && fifoViolations.get() == 0;
    }

    @Override
    public String toString() {
        return String.format(
            "BenchmarkResult{client=%s, impl=%s, ops=%.2f/s, success=%d, failed=%d, violations=%d}",
            clientId, implementationType, getOpsPerSecond(), 
            successfulLockAcquisitions.get(), failedLockAcquisitions.get(), correctnessViolations.get()
        );
    }
}

