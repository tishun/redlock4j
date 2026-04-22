/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark;

import org.codarama.redlock4j.benchmark.client.*;
import org.codarama.redlock4j.benchmark.infrastructure.*;
import org.codarama.redlock4j.benchmark.report.*;
import org.codarama.redlock4j.benchmark.scenario.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for running multi-lock benchmarks.
 * Compares Redisson RMultiLock vs redlock4j MultiLock.
 */
public class MultiLockBenchmarkMain {

    private static final Logger logger = LoggerFactory.getLogger(MultiLockBenchmarkMain.class);

    public static void main(String[] args) {
        BenchmarkConfiguration config = parseArgs(args);
        logger.info("Starting MultiLock Benchmark with configuration: {}", config);

        List<BenchmarkResultsAggregator.AggregatedResult> allResults = new ArrayList<>();
        BenchmarkResultsAggregator aggregator = new BenchmarkResultsAggregator();
        CorrectnessValidator validator = new CorrectnessValidator();

        try (RedisClusterManager clusterManager = new RedisClusterManager(config.getRedisNodeCount())) {
            clusterManager.start();

            RedisClusterManager.RedisNodeInfo resultsNode = clusterManager.getResultsNode();
            try (RedisResultsStore resultsStore = new RedisResultsStore(resultsNode.getHost(), resultsNode.getPort())) {

                MultiLockBenchmarkScenario scenario = new MultiLockBenchmarkScenario(config, clusterManager, resultsStore);

                // Single-node comparisons (fair comparison)
                logger.info("\n========== REDISSON MULTILOCK (SINGLE NODE) ==========\n");
                List<BenchmarkResult> redissonResults = scenario.run(RedissonMultiLockClient::new);
                CorrectnessValidator.ValidationResult redissonValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(redissonResults, redissonValidation));

                logger.info("\n========== REDLOCK4J MULTILOCK SINGLE-NODE MODE ==========\n");
                List<BenchmarkResult> singleNodeResults = scenario.run(Redlock4jSingleNodeMultiLockClient::new);
                CorrectnessValidator.ValidationResult singleNodeValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(singleNodeResults, singleNodeValidation));

                // Multi-node (3-node quorum)
                logger.info("\n========== REDLOCK4J MULTILOCK (3-NODE) ==========\n");
                List<BenchmarkResult> multiNodeResults = scenario.run(Redlock4jMultiLockClient::new);
                CorrectnessValidator.ValidationResult multiNodeValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(multiNodeResults, multiNodeValidation));
            }
        }

        // Generate report
        MarkdownReportGenerator reportGenerator = new MarkdownReportGenerator();
        String report = reportGenerator.generate(config, allResults);

        Path outputPath = Paths.get("multilock-benchmark-results.md");
        try {
            reportGenerator.writeToFile(report, outputPath);
            logger.info("\n========== BENCHMARK COMPLETE ==========");
            logger.info("Results written to: {}", outputPath.toAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to write report: {}", e.getMessage());
            System.out.println(report);
        }

        System.out.println("\n" + report);
    }

    private static BenchmarkConfiguration parseArgs(String[] args) {
        BenchmarkConfiguration.Builder builder = BenchmarkConfiguration.builder();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--nodes":
                    builder.redisNodeCount(Integer.parseInt(args[++i]));
                    break;
                case "--clients":
                    builder.clientCount(Integer.parseInt(args[++i]));
                    break;
                case "--duration":
                    builder.benchmarkDuration(Duration.ofMinutes(Long.parseLong(args[++i])));
                    break;
                case "--resources":
                    builder.multiLockResourceCount(Integer.parseInt(args[++i]));
                    break;
                case "--warmup":
                    builder.warmupDuration(Duration.ofSeconds(Long.parseLong(args[++i])));
                    break;
                case "--help":
                    printHelp();
                    System.exit(0);
                    break;
            }
        }

        return builder.build();
    }

    private static void printHelp() {
        System.out.println("MultiLock Benchmark");
        System.out.println("Usage: java -cp ... MultiLockBenchmarkMain [options]");
        System.out.println("\nOptions:");
        System.out.println("  --nodes <n>       Number of Redis nodes (default: 3)");
        System.out.println("  --clients <n>     Number of concurrent clients (default: 10)");
        System.out.println("  --duration <min>  Benchmark duration in minutes (default: 30)");
        System.out.println("  --resources <n>   Number of resources per multi-lock (default: 5)");
        System.out.println("  --warmup <sec>    Warmup duration in seconds (default: 60)");
        System.out.println("  --help            Show this help message");
    }
}

