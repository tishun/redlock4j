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
 * Main entry point for running CountDownLatch benchmarks.
 */
public class CountDownLatchBenchmarkMain {

    private static final Logger logger = LoggerFactory.getLogger(CountDownLatchBenchmarkMain.class);

    public static void main(String[] args) {
        BenchmarkConfiguration config = parseArgs(args);
        int latchCount = getLatchCount(args);

        logger.info("Starting CountDownLatch Benchmark: latch count={}", latchCount);

        List<BenchmarkResultsAggregator.AggregatedResult> allResults = new ArrayList<>();
        BenchmarkResultsAggregator aggregator = new BenchmarkResultsAggregator();

        try (RedisClusterManager clusterManager = new RedisClusterManager(config.getRedisNodeCount())) {
            clusterManager.start();

            CountDownLatchBenchmarkScenario scenario = new CountDownLatchBenchmarkScenario(
                    config, clusterManager, latchCount);

            logger.info("\n========== REDISSON COUNTDOWNLATCH ==========\n");
            BenchmarkResult redissonResult = scenario.run(RedissonCountDownLatchClient::new);
            allResults.add(aggregator.aggregateSingle(redissonResult));

            logger.info("\n========== REDLOCK4J COUNTDOWNLATCH (SINGLE-NODE) ==========\n");
            BenchmarkResult singleNodeResult = scenario.run(Redlock4jSingleNodeCountDownLatchClient::new);
            allResults.add(aggregator.aggregateSingle(singleNodeResult));

            logger.info("\n========== REDLOCK4J COUNTDOWNLATCH (3-NODE) ==========\n");
            BenchmarkResult multiNodeResult = scenario.run(Redlock4jCountDownLatchClient::new);
            allResults.add(aggregator.aggregateSingle(multiNodeResult));
        }

        MarkdownReportGenerator reportGenerator = new MarkdownReportGenerator();
        String report = reportGenerator.generate(config, allResults);

        Path outputPath = Paths.get("countdownlatch-benchmark-results.md");
        try {
            reportGenerator.writeToFile(report, outputPath);
            logger.info("\n========== BENCHMARK COMPLETE ==========");
            logger.info("Results written to: {}", outputPath.toAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to write report: {}", e.getMessage());
        }

        System.out.println("\n" + report);
    }

    private static BenchmarkConfiguration parseArgs(String[] args) {
        BenchmarkConfiguration.Builder builder = BenchmarkConfiguration.builder();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--nodes": builder.redisNodeCount(Integer.parseInt(args[++i])); break;
                case "--clients": builder.clientCount(Integer.parseInt(args[++i])); break;
                case "--duration": builder.benchmarkDuration(Duration.ofMinutes(Long.parseLong(args[++i]))); break;
                case "--warmup": builder.warmupDuration(Duration.ofSeconds(Long.parseLong(args[++i]))); break;
            }
        }
        return builder.build();
    }

    private static int getLatchCount(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--count".equals(args[i])) return Integer.parseInt(args[i + 1]);
        }
        return 5; // default
    }
}

