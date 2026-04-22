/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.infrastructure;

import java.time.Duration;

/**
 * Configuration for benchmark execution.
 */
public class BenchmarkConfiguration {

    private final int redisNodeCount;
    private final int clientCount;
    private final Duration benchmarkDuration;
    private final Duration workSimulationTime;
    private final Duration lockTimeout;
    private final Duration lockAcquisitionTimeout;
    private final String lockResourceName;
    private final Duration warmupDuration;
    private final Duration reportingInterval;
    private final int multiLockResourceCount;

    private BenchmarkConfiguration(Builder builder) {
        this.redisNodeCount = builder.redisNodeCount;
        this.clientCount = builder.clientCount;
        this.benchmarkDuration = builder.benchmarkDuration;
        this.workSimulationTime = builder.workSimulationTime;
        this.lockTimeout = builder.lockTimeout;
        this.lockAcquisitionTimeout = builder.lockAcquisitionTimeout;
        this.lockResourceName = builder.lockResourceName;
        this.warmupDuration = builder.warmupDuration;
        this.reportingInterval = builder.reportingInterval;
        this.multiLockResourceCount = builder.multiLockResourceCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getRedisNodeCount() { return redisNodeCount; }
    public int getClientCount() { return clientCount; }
    public Duration getBenchmarkDuration() { return benchmarkDuration; }
    public Duration getWorkSimulationTime() { return workSimulationTime; }
    public Duration getLockTimeout() { return lockTimeout; }
    public Duration getLockAcquisitionTimeout() { return lockAcquisitionTimeout; }
    public String getLockResourceName() { return lockResourceName; }
    public Duration getWarmupDuration() { return warmupDuration; }
    public Duration getReportingInterval() { return reportingInterval; }
    public int getMultiLockResourceCount() { return multiLockResourceCount; }

    public static class Builder {
        private int redisNodeCount = 3;
        private int clientCount = 10;
        private Duration benchmarkDuration = Duration.ofMinutes(30);
        private Duration workSimulationTime = Duration.ofMillis(50);
        private Duration lockTimeout = Duration.ofSeconds(30);
        private Duration lockAcquisitionTimeout = Duration.ofSeconds(60);
        private String lockResourceName = "benchmark-fair-lock";
        private Duration warmupDuration = Duration.ofMinutes(1);
        private Duration reportingInterval = Duration.ofSeconds(30);
        private int multiLockResourceCount = 5;

        public Builder redisNodeCount(int count) { this.redisNodeCount = count; return this; }
        public Builder clientCount(int count) { this.clientCount = count; return this; }
        public Builder benchmarkDuration(Duration duration) { this.benchmarkDuration = duration; return this; }
        public Builder workSimulationTime(Duration duration) { this.workSimulationTime = duration; return this; }
        public Builder lockTimeout(Duration timeout) { this.lockTimeout = timeout; return this; }
        public Builder lockAcquisitionTimeout(Duration timeout) { this.lockAcquisitionTimeout = timeout; return this; }
        public Builder lockResourceName(String name) { this.lockResourceName = name; return this; }
        public Builder warmupDuration(Duration duration) { this.warmupDuration = duration; return this; }
        public Builder reportingInterval(Duration interval) { this.reportingInterval = interval; return this; }
        public Builder multiLockResourceCount(int count) { this.multiLockResourceCount = count; return this; }

        public BenchmarkConfiguration build() { return new BenchmarkConfiguration(this); }
    }

    @Override
    public String toString() {
        return String.format(
            "BenchmarkConfiguration{nodes=%d, clients=%d, duration=%s, workTime=%s, lockTimeout=%s}",
            redisNodeCount, clientCount, benchmarkDuration, workSimulationTime, lockTimeout
        );
    }
}

