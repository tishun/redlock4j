/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores benchmark results in Redis for aggregation and analysis.
 */
public class RedisResultsStore implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(RedisResultsStore.class);
    private static final String RESULTS_KEY_PREFIX = "benchmark:results:";
    private static final String LOCK_EVENTS_KEY = "benchmark:lock_events";
    private static final String LOCK_HOLDER_KEY = "benchmark:current_holder";
    private static final String LOCK_SEQUENCE_KEY = "benchmark:lock_sequence";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public RedisResultsStore(String host, int port) {
        this.jedisPool = new JedisPool(host, port);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public void storeResult(BenchmarkResult result) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = RESULTS_KEY_PREFIX + result.getClientId();
            String json = objectMapper.writeValueAsString(new ResultSnapshot(result));
            jedis.set(key, json);
            logger.debug("Stored result for client {}", result.getClientId());
        } catch (Exception e) {
            logger.error("Failed to store result: {}", e.getMessage());
        }
    }

    public void recordLockAcquisition(String clientId, long timestamp, long sequenceNumber) {
        try (Jedis jedis = jedisPool.getResource()) {
            String event = String.format("%d:%s:%d", timestamp, clientId, sequenceNumber);
            jedis.rpush(LOCK_EVENTS_KEY, event);
            jedis.set(LOCK_HOLDER_KEY, clientId);
        }
    }

    public void recordLockRelease(String clientId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(LOCK_HOLDER_KEY);
        }
    }

    public String getCurrentLockHolder() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(LOCK_HOLDER_KEY);
        }
    }

    public long getNextSequence() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.incr(LOCK_SEQUENCE_KEY);
        }
    }

    public List<String> getLockEvents() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.lrange(LOCK_EVENTS_KEY, 0, -1);
        }
    }

    public List<ResultSnapshot> getAllResults() {
        List<ResultSnapshot> results = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(RESULTS_KEY_PREFIX + "*");
            for (String key : keys) {
                String json = jedis.get(key);
                if (json != null) {
                    results.add(objectMapper.readValue(json, ResultSnapshot.class));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve results: {}", e.getMessage());
        }
        return results;
    }

    public void clear() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys("benchmark:*");
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
            }
        }
    }

    @Override
    public void close() {
        jedisPool.close();
    }

    public static class ResultSnapshot {
        public String clientId;
        public String implementationType;
        public long successfulOps;
        public long failedOps;
        public double opsPerSecond;
        public double avgWaitTimeMs;
        public double avgHoldTimeMs;
        public long correctnessViolations;
        public long fifoViolations;
        public Map<String, Long> latencyPercentiles;

        public ResultSnapshot() {}

        public ResultSnapshot(BenchmarkResult result) {
            this.clientId = result.getClientId();
            this.implementationType = result.getImplementationType();
            this.successfulOps = result.getSuccessfulLockAcquisitions();
            this.failedOps = result.getFailedLockAcquisitions();
            this.opsPerSecond = result.getOpsPerSecond();
            this.avgWaitTimeMs = result.getAverageWaitTimeMs();
            this.avgHoldTimeMs = result.getAverageHoldTimeMs();
            this.correctnessViolations = result.getCorrectnessViolations();
            this.fifoViolations = result.getFifoViolations();
            this.latencyPercentiles = result.getLatencyPercentiles();
        }
    }
}

