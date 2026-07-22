/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.report;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkResult;
import org.codarama.redlock4j.benchmark.scenario.CorrectnessValidator.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates benchmark results across all clients for reporting.
 */
public class BenchmarkResultsAggregator {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkResultsAggregator.class);

    /**
     * Aggregated results for a single implementation.
     */
    public static class AggregatedResult {
        public final String implementationType;
        public final int clientCount;
        public final long totalSuccessfulOps;
        public final long totalFailedOps;
        public final double aggregateOpsPerSecond;
        public final double avgOpsPerSecondPerClient;
        public final double opsPerSecondPerClientStdev;
        public final double opsPerSecondPerClientCi95Half;
        public final double avgWaitTimeMs;
        public final double avgHoldTimeMs;
        public final long totalCorrectnessViolations;
        public final long totalFifoViolations;
        public final Map<String, Long> aggregatedLatencyPercentiles;
        public final ValidationResult validationResult;

        public AggregatedResult(String implementationType, int clientCount, long totalSuccessfulOps,
                long totalFailedOps, double aggregateOpsPerSecond, double avgOpsPerSecondPerClient,
                double opsPerSecondPerClientStdev, double opsPerSecondPerClientCi95Half,
                double avgWaitTimeMs, double avgHoldTimeMs, long totalCorrectnessViolations,
                long totalFifoViolations, Map<String, Long> aggregatedLatencyPercentiles,
                ValidationResult validationResult) {
            this.implementationType = implementationType;
            this.clientCount = clientCount;
            this.totalSuccessfulOps = totalSuccessfulOps;
            this.totalFailedOps = totalFailedOps;
            this.aggregateOpsPerSecond = aggregateOpsPerSecond;
            this.avgOpsPerSecondPerClient = avgOpsPerSecondPerClient;
            this.opsPerSecondPerClientStdev = opsPerSecondPerClientStdev;
            this.opsPerSecondPerClientCi95Half = opsPerSecondPerClientCi95Half;
            this.avgWaitTimeMs = avgWaitTimeMs;
            this.avgHoldTimeMs = avgHoldTimeMs;
            this.totalCorrectnessViolations = totalCorrectnessViolations;
            this.totalFifoViolations = totalFifoViolations;
            this.aggregatedLatencyPercentiles = aggregatedLatencyPercentiles;
            this.validationResult = validationResult;
        }

        public boolean isCorrect() {
            return totalCorrectnessViolations == 0 && totalFifoViolations == 0 &&
                   (validationResult == null || validationResult.isCorrect());
        }

        public double getSuccessRate() {
            long total = totalSuccessfulOps + totalFailedOps;
            return total > 0 ? (totalSuccessfulOps * 100.0) / total : 0;
        }
    }

    /**
     * Aggregates results from multiple clients into a single result per implementation.
     */
    public AggregatedResult aggregate(List<BenchmarkResult> results, ValidationResult validationResult) {
        if (results.isEmpty()) {
            throw new IllegalArgumentException("No results to aggregate");
        }

        String implType = results.get(0).getImplementationType();
        int clientCount = results.size();

        long totalSuccessful = results.stream().mapToLong(BenchmarkResult::getSuccessfulLockAcquisitions).sum();
        long totalFailed = results.stream().mapToLong(BenchmarkResult::getFailedLockAcquisitions).sum();
        double totalOps = results.stream().mapToDouble(BenchmarkResult::getOpsPerSecond).sum();
        double avgOps = totalOps / clientCount;
        double avgWait = results.stream().mapToDouble(BenchmarkResult::getAverageWaitTimeMs).average().orElse(0);
        double avgHold = results.stream().mapToDouble(BenchmarkResult::getAverageHoldTimeMs).average().orElse(0);
        long violations = results.stream().mapToLong(BenchmarkResult::getCorrectnessViolations).sum();
        long fifoViols = results.stream().mapToLong(BenchmarkResult::getFifoViolations).sum();

        double stdev = sampleStdev(results, avgOps);
        double ci95Half = clientCount > 1 ? 1.96 * stdev / Math.sqrt(clientCount) : 0.0;

        // Aggregate latency percentiles (average across clients)
        Map<String, Long> aggregatedPercentiles = aggregatePercentiles(results);

        logger.info("Aggregated results for {}: {} clients, {} ops/s, {} violations",
            implType, clientCount, String.format("%.2f", totalOps), violations);

        return new AggregatedResult(implType, clientCount, totalSuccessful, totalFailed,
            totalOps, avgOps, stdev, ci95Half, avgWait, avgHold, violations, fifoViols,
            aggregatedPercentiles, validationResult);
    }

    private double sampleStdev(List<BenchmarkResult> results, double mean) {
        int n = results.size();
        if (n < 2) return 0.0;
        double sumSq = 0;
        for (BenchmarkResult r : results) {
            double d = r.getOpsPerSecond() - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (n - 1));
    }

    /**
     * Aggregates results grouped by implementation type. Use when a single benchmark run produces
     * multiple roles per implementation (e.g. RWLock readers and writers) that must be reported separately.
     */
    public List<AggregatedResult> aggregateByImplementationType(List<BenchmarkResult> results,
            ValidationResult validationResult) {
        if (results.isEmpty()) {
            throw new IllegalArgumentException("No results to aggregate");
        }

        Map<String, List<BenchmarkResult>> grouped = results.stream()
                .collect(Collectors.groupingBy(BenchmarkResult::getImplementationType,
                        LinkedHashMap::new, Collectors.toList()));

        List<AggregatedResult> aggregated = new ArrayList<>(grouped.size());
        for (List<BenchmarkResult> group : grouped.values()) {
            aggregated.add(aggregate(group, validationResult));
        }
        return aggregated;
    }

    /**
     * Aggregates a single result (for scenarios with one coordinator).
     */
    public AggregatedResult aggregateSingle(BenchmarkResult result) {
        return new AggregatedResult(
                result.getImplementationType(),
                1,
                result.getSuccessfulLockAcquisitions(),
                result.getFailedLockAcquisitions(),
                result.getOpsPerSecond(),
                result.getOpsPerSecond(),
                0.0,
                0.0,
                result.getAverageWaitTimeMs(),
                result.getAverageHoldTimeMs(),
                result.getCorrectnessViolations(),
                result.getFifoViolations(),
                result.getLatencyPercentiles(),
                null
        );
    }

    private Map<String, Long> aggregatePercentiles(List<BenchmarkResult> results) {
        Map<String, Long> aggregated = new HashMap<>();
        List<String> percentileKeys = Arrays.asList("p50", "p75", "p90", "p95", "p99", "p999", "max", "mean");

        for (String key : percentileKeys) {
            long sum = 0;
            int count = 0;
            for (BenchmarkResult r : results) {
                Long value = r.getLatencyPercentiles().get(key);
                if (value != null) {
                    sum += value;
                    count++;
                }
            }
            if (count > 0) {
                aggregated.put(key, sum / count);
            }
        }
        return aggregated;
    }
}

