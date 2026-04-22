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
 * Main entry point for running fair lock benchmarks.
 * Compares Redisson, redlock4j-lettuce, and redlock4j-jedis implementations.
 */
public class FairLockBenchmarkMain {

    private static final Logger logger = LoggerFactory.getLogger(FairLockBenchmarkMain.class);

    public static void main(String[] args) {
        BenchmarkConfiguration config = parseArgs(args);
        logger.info("Starting Fair Lock Benchmark with configuration: {}", config);

        List<BenchmarkResultsAggregator.AggregatedResult> allResults = new ArrayList<>();
        BenchmarkResultsAggregator aggregator = new BenchmarkResultsAggregator();
        CorrectnessValidator validator = new CorrectnessValidator();

        try (RedisClusterManager clusterManager = new RedisClusterManager(config.getRedisNodeCount())) {
            clusterManager.start();

            RedisClusterManager.RedisNodeInfo resultsNode = clusterManager.getResultsNode();
            try (RedisResultsStore resultsStore = new RedisResultsStore(resultsNode.getHost(), resultsNode.getPort())) {

                FairLockBenchmarkScenario scenario = new FairLockBenchmarkScenario(config, clusterManager, resultsStore);

                // Run benchmark for each implementation
                // Single-node comparisons (fair comparison)
                logger.info("\n========== REDISSON (SINGLE NODE) BENCHMARK ==========\n");
                List<BenchmarkResult> redissonResults = scenario.run(RedissonFairLockClient::new);
                CorrectnessValidator.ValidationResult redissonValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(redissonResults, redissonValidation));

                logger.info("\n========== REDLOCK4J SINGLE-NODE MODE BENCHMARK ==========\n");
                List<BenchmarkResult> singleNodeResults = scenario.run(Redlock4jSingleNodeFairLockClient::new);
                CorrectnessValidator.ValidationResult singleNodeValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(singleNodeResults, singleNodeValidation));

                // Multi-node (3-node quorum) implementations
                logger.info("\n========== REDLOCK4J (LETTUCE, 3-NODE) BENCHMARK ==========\n");
                List<BenchmarkResult> lettuceResults = scenario.run(Redlock4jLettuceFairLockClient::new);
                CorrectnessValidator.ValidationResult lettuceValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(lettuceResults, lettuceValidation));

                logger.info("\n========== REDLOCK4J (JEDIS, 3-NODE) BENCHMARK ==========\n");
                List<BenchmarkResult> jedisResults = scenario.run(Redlock4jJedisFairLockClient::new);
                CorrectnessValidator.ValidationResult jedisValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(jedisResults, jedisValidation));
            }
        }

        // Generate and write report
        MarkdownReportGenerator reportGenerator = new MarkdownReportGenerator();
        String report = reportGenerator.generate(config, allResults);

        Path outputPath = Paths.get("benchmark-results.md");
        try {
            reportGenerator.writeToFile(report, outputPath);
            logger.info("\n========== BENCHMARK COMPLETE ==========");
            logger.info("Results written to: {}", outputPath.toAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to write report: {}", e.getMessage());
            System.out.println(report); // Print to console as fallback
        }

        // Print summary to console
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
                case "--work-time":
                    builder.workSimulationTime(Duration.ofMillis(Long.parseLong(args[++i])));
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
        System.out.println("Fair Lock Benchmark");
        System.out.println("Usage: java -jar redlock4j-benchmark.jar [options]");
        System.out.println("\nOptions:");
        System.out.println("  --nodes <n>       Number of Redis nodes (default: 3)");
        System.out.println("  --clients <n>     Number of concurrent clients per implementation (default: 10)");
        System.out.println("  --duration <min>  Benchmark duration in minutes (default: 30)");
        System.out.println("  --work-time <ms>  Simulated work time in milliseconds (default: 50)");
        System.out.println("  --warmup <sec>    Warmup duration in seconds (default: 60)");
        System.out.println("  --help            Show this help message");
    }
}

