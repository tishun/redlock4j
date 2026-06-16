/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.report.BenchmarkResultsAggregator.AggregatedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits machine-readable JSON output for a benchmark run alongside the markdown report.
 */
public class JsonReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(JsonReportGenerator.class);

    private final ObjectMapper mapper;

    public JsonReportGenerator() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String generate(String title, BenchmarkConfiguration config, List<AggregatedResult> results) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", title);
        root.put("generatedAt", Instant.now().toString());
        root.put("config", configToMap(config));

        List<Map<String, Object>> resultMaps = new ArrayList<>(results.size());
        for (AggregatedResult r : results) {
            resultMaps.add(resultToMap(r));
        }
        root.put("results", resultMaps);

        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize benchmark JSON report", e);
        }
    }

    public void writeToFile(String content, Path outputPath) throws IOException {
        Files.writeString(outputPath, content);
        logger.info("JSON report written to: {}", outputPath);
    }

    private Map<String, Object> configToMap(BenchmarkConfiguration c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("redisNodeCount", c.getRedisNodeCount());
        m.put("clientCount", c.getClientCount());
        m.put("benchmarkDurationMs", c.getBenchmarkDuration().toMillis());
        m.put("warmupDurationMs", c.getWarmupDuration().toMillis());
        m.put("workSimulationTimeMs", c.getWorkSimulationTime().toMillis());
        m.put("lockTimeoutMs", c.getLockTimeout().toMillis());
        m.put("lockAcquisitionTimeoutMs", c.getLockAcquisitionTimeout().toMillis());
        m.put("lockResourceName", c.getLockResourceName());
        m.put("multiLockResourceCount", c.getMultiLockResourceCount());
        return m;
    }

    private Map<String, Object> resultToMap(AggregatedResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("implementationType", r.implementationType);
        m.put("clientCount", r.clientCount);
        m.put("totalSuccessfulOps", r.totalSuccessfulOps);
        m.put("totalFailedOps", r.totalFailedOps);
        m.put("successRatePct", r.getSuccessRate());
        m.put("aggregateOpsPerSecond", r.aggregateOpsPerSecond);
        m.put("avgOpsPerSecondPerClient", r.avgOpsPerSecondPerClient);
        m.put("opsPerSecondPerClientStdev", r.opsPerSecondPerClientStdev);
        m.put("opsPerSecondPerClientCi95Half", r.opsPerSecondPerClientCi95Half);
        m.put("avgWaitTimeMs", r.avgWaitTimeMs);
        m.put("avgHoldTimeMs", r.avgHoldTimeMs);
        m.put("totalCorrectnessViolations", r.totalCorrectnessViolations);
        m.put("totalFifoViolations", r.totalFifoViolations);
        m.put("correct", r.isCorrect());
        m.put("latencyPercentilesMicros", r.aggregatedLatencyPercentiles);

        if (r.validationResult != null) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("totalEvents", r.validationResult.getTotalEvents());
            v.put("uniqueClients", r.validationResult.getUniqueClients());
            v.put("concurrentHolderViolations", r.validationResult.getConcurrentHolderViolationCount());
            v.put("fifoViolations", r.validationResult.getFifoViolationCount());
            v.put("correct", r.validationResult.isCorrect());
            m.put("validation", v);
        }

        return m;
    }
}
