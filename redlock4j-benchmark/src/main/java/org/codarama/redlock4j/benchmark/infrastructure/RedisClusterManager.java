/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages a cluster of Redis containers for benchmark testing.
 * Provides connection details for all Redis instances.
 */
public class RedisClusterManager implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(RedisClusterManager.class);
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_PORT = 6379;

    private final List<GenericContainer<?>> containers;
    private final int nodeCount;
    private boolean started = false;

    public RedisClusterManager(int nodeCount) {
        this.nodeCount = nodeCount;
        this.containers = new ArrayList<>(nodeCount);
        
        for (int i = 0; i < nodeCount; i++) {
            GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(REDIS_PORT)
                    .withCommand("redis-server", "--appendonly", "yes");
            containers.add(container);
        }
    }

    public void start() {
        if (started) {
            return;
        }
        logger.info("Starting {} Redis containers...", nodeCount);
        containers.parallelStream().forEach(GenericContainer::start);
        started = true;
        logger.info("All Redis containers started successfully");
        
        for (int i = 0; i < containers.size(); i++) {
            GenericContainer<?> c = containers.get(i);
            logger.info("  Redis {} -> {}:{}", i + 1, c.getHost(), c.getMappedPort(REDIS_PORT));
        }
    }

    public List<RedisNodeInfo> getNodeInfos() {
        if (!started) {
            throw new IllegalStateException("Cluster not started");
        }
        return containers.stream()
                .map(c -> new RedisNodeInfo(c.getHost(), c.getMappedPort(REDIS_PORT)))
                .collect(Collectors.toList());
    }

    public RedisNodeInfo getResultsNode() {
        if (!started || containers.isEmpty()) {
            throw new IllegalStateException("Cluster not started or empty");
        }
        GenericContainer<?> c = containers.get(0);
        return new RedisNodeInfo(c.getHost(), c.getMappedPort(REDIS_PORT));
    }

    public int getNodeCount() {
        return nodeCount;
    }

    @Override
    public void close() {
        logger.info("Stopping Redis containers...");
        containers.forEach(GenericContainer::stop);
        started = false;
    }

    public static class RedisNodeInfo {
        private final String host;
        private final int port;

        public RedisNodeInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getAddress() {
            return host + ":" + port;
        }

        public String getRedisUrl() {
            return "redis://" + host + ":" + port;
        }
    }
}

