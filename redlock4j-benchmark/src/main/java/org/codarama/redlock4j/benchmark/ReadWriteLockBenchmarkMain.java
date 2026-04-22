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
 * Main entry point for running read-write lock benchmarks.
 * Compares Redisson RReadWriteLock vs redlock4j RedlockReadWriteLock.
 */
public class ReadWriteLockBenchmarkMain {

    private static final Logger logger = LoggerFactory.getLogger(ReadWriteLockBenchmarkMain.class);

    public static void main(String[] args) {
        BenchmarkConfiguration config = parseArgs(args);
        int writers = getWriterCount(args);
        int readers = getReaderCount(args);
        
        logger.info("Starting ReadWriteLock Benchmark: {} writers, {} readers", writers, readers);

        List<BenchmarkResultsAggregator.AggregatedResult> allResults = new ArrayList<>();
        BenchmarkResultsAggregator aggregator = new BenchmarkResultsAggregator();
        CorrectnessValidator validator = new CorrectnessValidator();

        try (RedisClusterManager clusterManager = new RedisClusterManager(config.getRedisNodeCount())) {
            clusterManager.start();

            RedisClusterManager.RedisNodeInfo resultsNode = clusterManager.getResultsNode();
            try (RedisResultsStore resultsStore = new RedisResultsStore(resultsNode.getHost(), resultsNode.getPort())) {

                ReadWriteLockBenchmarkScenario scenario = new ReadWriteLockBenchmarkScenario(
                        config, clusterManager, resultsStore, writers, readers);

                logger.info("\n========== REDISSON READWRITELOCK (SINGLE NODE) ==========\n");
                List<BenchmarkResult> redissonResults = scenario.run(RedissonReadWriteLockClient::new);
                CorrectnessValidator.ValidationResult redissonValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(redissonResults, redissonValidation));

                logger.info("\n========== REDLOCK4J READWRITELOCK SINGLE-NODE MODE ==========\n");
                List<BenchmarkResult> singleNodeResults = scenario.run(Redlock4jSingleNodeReadWriteLockClient::new);
                CorrectnessValidator.ValidationResult singleNodeValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(singleNodeResults, singleNodeValidation));

                logger.info("\n========== REDLOCK4J READWRITELOCK (3-NODE) ==========\n");
                List<BenchmarkResult> multiNodeResults = scenario.run(Redlock4jReadWriteLockClient::new);
                CorrectnessValidator.ValidationResult multiNodeValidation = validator.validate(resultsStore);
                allResults.add(aggregator.aggregate(multiNodeResults, multiNodeValidation));
            }
        }

        MarkdownReportGenerator reportGenerator = new MarkdownReportGenerator();
        String report = reportGenerator.generate(config, allResults);

        Path outputPath = Paths.get("rwlock-benchmark-results.md");
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
                case "--nodes": builder.redisNodeCount(Integer.parseInt(args[++i])); break;
                case "--duration": builder.benchmarkDuration(Duration.ofMinutes(Long.parseLong(args[++i]))); break;
                case "--warmup": builder.warmupDuration(Duration.ofSeconds(Long.parseLong(args[++i]))); break;
            }
        }
        return builder.build();
    }

    private static int getWriterCount(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--writers".equals(args[i])) return Integer.parseInt(args[i + 1]);
        }
        return 2; // default
    }

    private static int getReaderCount(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--readers".equals(args[i])) return Integer.parseInt(args[i + 1]);
        }
        return 8; // default
    }
}

