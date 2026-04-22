/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.report;

import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.report.BenchmarkResultsAggregator.AggregatedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates a Markdown report comparing benchmark results across implementations.
 */
public class MarkdownReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownReportGenerator.class);

    public String generate(BenchmarkConfiguration config, List<AggregatedResult> results) {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        sb.append("# Fair Lock Benchmark Results\n\n");
        sb.append("**Generated:** ").append(timestamp).append("\n\n");

        // Configuration Section
        sb.append("## Configuration\n\n");
        sb.append("| Parameter | Value |\n");
        sb.append("|-----------|-------|\n");
        sb.append(String.format("| Redis Nodes | %d |\n", config.getRedisNodeCount()));
        sb.append(String.format("| Clients per Implementation | %d |\n", config.getClientCount()));
        sb.append(String.format("| Benchmark Duration | %s |\n", formatDuration(config.getBenchmarkDuration().toMillis())));
        sb.append(String.format("| Work Simulation Time | %d ms |\n", config.getWorkSimulationTime().toMillis()));
        sb.append(String.format("| Lock Timeout | %d s |\n", config.getLockTimeout().getSeconds()));
        sb.append("\n");

        // Summary Comparison Table
        sb.append("## Summary Comparison\n\n");
        sb.append("| Metric | ");
        for (AggregatedResult r : results) {
            sb.append(r.implementationType).append(" | ");
        }
        sb.append("\n|--------|");
        for (int i = 0; i < results.size(); i++) sb.append("--------|");
        sb.append("\n");

        addRow(sb, "Total Ops/s", results, r -> String.format("%.2f", r.aggregateOpsPerSecond));
        addRow(sb, "Avg Ops/s/Client", results, r -> String.format("%.2f", r.avgOpsPerSecondPerClient));
        addRow(sb, "Successful Ops", results, r -> String.format("%,d", r.totalSuccessfulOps));
        addRow(sb, "Failed Ops", results, r -> String.format("%,d", r.totalFailedOps));
        addRow(sb, "Success Rate", results, r -> String.format("%.2f%%", r.getSuccessRate()));
        addRow(sb, "Avg Wait Time", results, r -> String.format("%.2f ms", r.avgWaitTimeMs));
        addRow(sb, "Correctness", results, r -> r.isCorrect() ? "PASS" : "FAIL");
        sb.append("\n");

        // Latency Percentiles
        sb.append("## Latency Percentiles (microseconds)\n\n");
        sb.append("| Percentile | ");
        for (AggregatedResult r : results) {
            sb.append(r.implementationType).append(" | ");
        }
        sb.append("\n|------------|");
        for (int i = 0; i < results.size(); i++) sb.append("--------|");
        sb.append("\n");

        String[] percentiles = {"p50", "p75", "p90", "p95", "p99", "p999", "max", "mean"};
        for (String p : percentiles) {
            sb.append("| ").append(p).append(" | ");
            for (AggregatedResult r : results) {
                Long value = r.aggregatedLatencyPercentiles.get(p);
                sb.append(value != null ? String.format("%,d", value) : "N/A").append(" | ");
            }
            sb.append("\n");
        }
        sb.append("\n");

        // Correctness Details
        sb.append("## Correctness Validation\n\n");
        for (AggregatedResult r : results) {
            sb.append("### ").append(r.implementationType).append("\n\n");
            sb.append(String.format("- **Status:** %s\n", r.isCorrect() ? "PASSED" : "FAILED"));
            sb.append(String.format("- **Correctness Violations:** %d\n", r.totalCorrectnessViolations));
            sb.append(String.format("- **FIFO Violations:** %d\n", r.totalFifoViolations));
            if (r.validationResult != null) {
                sb.append(String.format("- **Lock Events Analyzed:** %d\n", r.validationResult.getTotalEvents()));
            }
            sb.append("\n");
        }

        // Winner determination
        sb.append("## Analysis\n\n");
        AggregatedResult fastest = results.stream()
            .max((a, b) -> Double.compare(a.aggregateOpsPerSecond, b.aggregateOpsPerSecond))
            .orElse(null);
        if (fastest != null) {
            sb.append(String.format("**Highest Throughput:** %s with %.2f ops/s\n\n", 
                fastest.implementationType, fastest.aggregateOpsPerSecond));
        }

        AggregatedResult lowestLatency = results.stream()
            .min((a, b) -> Double.compare(a.avgWaitTimeMs, b.avgWaitTimeMs))
            .orElse(null);
        if (lowestLatency != null) {
            sb.append(String.format("**Lowest Latency:** %s with %.2f ms average wait time\n\n",
                lowestLatency.implementationType, lowestLatency.avgWaitTimeMs));
        }

        return sb.toString();
    }

    private void addRow(StringBuilder sb, String metric, List<AggregatedResult> results,
            java.util.function.Function<AggregatedResult, String> valueExtractor) {
        sb.append("| ").append(metric).append(" | ");
        for (AggregatedResult r : results) {
            sb.append(valueExtractor.apply(r)).append(" | ");
        }
        sb.append("\n");
    }

    private String formatDuration(long millis) {
        long minutes = millis / 60000;
        return minutes + " minutes";
    }

    public void writeToFile(String content, Path outputPath) throws IOException {
        Files.write(outputPath, content.getBytes(StandardCharsets.UTF_8));
        logger.info("Report written to: {}", outputPath);
    }
}

