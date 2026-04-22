/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.client;

import com.himadieiev.redpulsar.core.locks.Mutex;
import com.himadieiev.redpulsar.jedis.locks.LockFactory;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.codarama.redlock4j.benchmark.infrastructure.BenchmarkConfiguration;
import org.codarama.redlock4j.benchmark.infrastructure.RedisClusterManager;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Distributed lock client using RedPulsar's Mutex (implements the Redlock algorithm).
 * 
 * RedPulsar is a Kotlin-based distributed locking library that implements the Redlock algorithm
 * with support for both Jedis and Lettuce clients.
 */
public class RedPulsarDistributedLockClient extends AbstractDistributedLockClient {

    private static final String IMPLEMENTATION_TYPE = "redpulsar";

    private List<UnifiedJedis> jedisClients;
    private Mutex mutex;
    private Duration lockTimeout;
    private boolean healthy = false;

    @Override
    public String getImplementationType() {
        return IMPLEMENTATION_TYPE;
    }

    @Override
    public void initialize(BenchmarkConfiguration config, RedisClusterManager clusterManager) {
        List<RedisClusterManager.RedisNodeInfo> nodes = clusterManager.getNodeInfos();
        this.lockTimeout = config.getLockTimeout();

        try {
            jedisClients = new ArrayList<>();
            GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(20);
            poolConfig.setMinIdle(5);

            for (RedisClusterManager.RedisNodeInfo node : nodes) {
                JedisPooled client = new JedisPooled(poolConfig, node.getHost(), node.getPort(), 5000);
                jedisClients.add(client);
            }

            // Create Mutex using the Redlock algorithm with multiple nodes
            mutex = LockFactory.createMutex(
                    jedisClients,
                    Duration.ofMillis(50),  // retryDelay
                    3                       // retryCount
            );

            healthy = true;
            logger.info("RedPulsar distributed lock client initialized with {} Redis nodes", nodes.size());
        } catch (Exception e) {
            logger.error("Failed to initialize RedPulsar client: {}", e.getMessage());
            healthy = false;
        }
    }

    @Override
    public Lock getLock(String resourceName) {
        if (mutex == null) {
            throw new IllegalStateException("Client not initialized");
        }
        return new RedPulsarLockAdapter(mutex, resourceName, lockTimeout);
    }

    @Override
    public boolean isHealthy() {
        return healthy && mutex != null && jedisClients != null && !jedisClients.isEmpty();
    }

    @Override
    public void close() {
        if (jedisClients != null) {
            for (UnifiedJedis client : jedisClients) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Error closing Jedis client: {}", e.getMessage());
                }
            }
            jedisClients.clear();
        }
        logger.info("RedPulsar distributed lock client closed");
        healthy = false;
    }

    /**
     * Adapter to make RedPulsar's Mutex work with java.util.concurrent.locks.Lock interface.
     */
    private static class RedPulsarLockAdapter implements Lock {
        private final Mutex mutex;
        private final String resourceName;
        private final Duration lockTimeout;

        RedPulsarLockAdapter(Mutex mutex, String resourceName, Duration lockTimeout) {
            this.mutex = mutex;
            this.resourceName = resourceName;
            this.lockTimeout = lockTimeout;
        }

        @Override
        public void lock() {
            while (!tryLock()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            while (!tryLock()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                Thread.sleep(10);
            }
        }

        @Override
        public boolean tryLock() {
            return mutex.lock(resourceName, lockTimeout);
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(time);
            while (System.nanoTime() < deadline) {
                if (tryLock()) {
                    return true;
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                Thread.sleep(5);
            }
            return false;
        }

        @Override
        public void unlock() {
            mutex.unlock(resourceName);
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("Conditions not supported by RedPulsar");
        }
    }
}
