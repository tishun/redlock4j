/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.configuration;

import org.codarama.redlock4j.strategy.WaitStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Central configuration class for Redlock using builder pattern.
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class RedlockConfiguration {
    private final List<RedisNodeConfiguration> redisNodes;
    private final Duration defaultLockTimeout;
    private final Duration retryDelay;
    private final Duration maxRetryDelay;
    private final double retryDelayMultiplier;
    private final double retryDelayJitterRatio;
    private final int maxRetryAttempts;
    private final double clockDriftFactor;
    private final Duration lockAcquisitionTimeout;
    private final WaitStrategy waitStrategy;

    private RedlockConfiguration(Builder builder) {
        this.redisNodes = new ArrayList<>(builder.redisNodes);
        this.defaultLockTimeout = builder.defaultLockTimeout;
        this.retryDelay = builder.retryDelay;
        this.maxRetryDelay = builder.maxRetryDelay != null ? builder.maxRetryDelay : builder.retryDelay;
        this.retryDelayMultiplier = builder.retryDelayMultiplier;
        this.retryDelayJitterRatio = builder.retryDelayJitterRatio;
        this.maxRetryAttempts = builder.maxRetryAttempts;
        this.clockDriftFactor = builder.clockDriftFactor;
        this.lockAcquisitionTimeout = builder.lockAcquisitionTimeout;
        this.waitStrategy = builder.waitStrategy;
    }

    /**
     * Returns the list of configured Redis nodes.
     *
     * @return a copy of the Redis node configurations
     */
    public List<RedisNodeConfiguration> getRedisNodes() {
        return new ArrayList<>(redisNodes);
    }

    /**
     * Returns the default lock TTL.
     *
     * @return lock timeout duration
     */
    public Duration getDefaultLockTimeout() {
        return defaultLockTimeout;
    }

    /**
     * Returns the delay between retry attempts.
     *
     * @return retry delay duration
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * Returns the upper bound on the retry delay after exponential backoff growth.
     *
     * <p>
     * When {@link #getRetryDelayMultiplier()} is greater than 1.0, the effective delay grows with each retry attempt
     * but is capped at this value. Defaults to {@link #getRetryDelay()} (no growth).
     * </p>
     *
     * @return maximum retry delay
     */
    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    /**
     * Returns the multiplier applied per retry attempt for exponential backoff.
     *
     * <p>
     * Effective delay = min(maxRetryDelay, retryDelay * multiplier^attempt). A value of 1.0 disables growth.
     * </p>
     *
     * @return retry delay multiplier (>= 1.0)
     */
    public double getRetryDelayMultiplier() {
        return retryDelayMultiplier;
    }

    /**
     * Returns the jitter ratio applied to the computed retry delay.
     *
     * <p>
     * The actual sleep is sampled uniformly from [(1-r)*delay, (1+r)*delay]. A value of 0.0 disables jitter.
     * </p>
     *
     * @return jitter ratio in [0.0, 1.0]
     */
    public double getRetryDelayJitterRatio() {
        return retryDelayJitterRatio;
    }

    /**
     * Returns the maximum number of lock acquisition retry attempts.
     *
     * @return max retry attempts
     */
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    /**
     * Returns the clock drift compensation factor (0.0 to 1.0).
     *
     * @return clock drift factor
     */
    public double getClockDriftFactor() {
        return clockDriftFactor;
    }

    /**
     * Returns the maximum time to wait for lock acquisition.
     *
     * @return acquisition timeout duration
     */
    public Duration getLockAcquisitionTimeout() {
        return lockAcquisitionTimeout;
    }

    /**
     * Returns whether single-node mode is active. Single-node mode is automatically enabled when exactly one Redis node
     * is configured. In this mode, locks are acquired without distributed consensus, trading fault tolerance for
     * performance.
     *
     * @return true if exactly one Redis node is configured
     */
    public boolean isSingleNodeMode() {
        return redisNodes.size() == 1;
    }

    /**
     * Returns the wait strategy used when waiting for a lock to be released.
     *
     * <p>
     * The default is {@link WaitStrategy#KEYSPACE_NOTIFICATIONS}, which provides instant notification when a lock is
     * released. Use {@link WaitStrategy#POLLING} only if keyspace notifications are not available.
     * </p>
     *
     * @return the configured wait strategy
     */
    public WaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    /**
     * Returns the quorum required for lock acquisition. For single-node mode (1 node), returns 1. For multi-node mode
     * (3+ nodes), returns majority (N/2 + 1).
     *
     * @return the quorum size
     */
    public int getQuorum() {
        if (redisNodes.size() == 1) {
            return 1;
        }
        return redisNodes.size() / 2 + 1;
    }

    /**
     * Creates a new builder for RedlockConfiguration.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link RedlockConfiguration} instances.
     */
    public static class Builder {
        private final List<RedisNodeConfiguration> redisNodes = new ArrayList<>();
        private Duration defaultLockTimeout = Duration.ofSeconds(30);
        private Duration retryDelay = Duration.ofMillis(200);
        private Duration maxRetryDelay = null;
        private double retryDelayMultiplier = 1.0;
        private double retryDelayJitterRatio = 0.0;
        private int maxRetryAttempts = 3;
        private double clockDriftFactor = 0.01;
        private Duration lockAcquisitionTimeout = Duration.ofSeconds(10);
        private WaitStrategy waitStrategy = WaitStrategy.KEYSPACE_NOTIFICATIONS;

        /**
         * Adds a Redis node with full configuration.
         *
         * @param nodeConfig
         *            the node configuration
         * @return this builder
         */
        public Builder addRedisNode(RedisNodeConfiguration nodeConfig) {
            this.redisNodes.add(nodeConfig);
            return this;
        }

        /**
         * Adds a Redis node with host and port.
         *
         * @param host
         *            the hostname or IP
         * @param port
         *            the port number
         * @return this builder
         */
        public Builder addRedisNode(String host, int port) {
            return addRedisNode(RedisNodeConfiguration.builder().host(host).port(port).build());
        }

        /**
         * Adds a Redis node with host, port, and password.
         *
         * @param host
         *            the hostname or IP
         * @param port
         *            the port number
         * @param password
         *            the authentication password
         * @return this builder
         */
        public Builder addRedisNode(String host, int port, String password) {
            return addRedisNode(RedisNodeConfiguration.builder().host(host).port(port).password(password).build());
        }

        /**
         * Sets the default lock TTL. Default: 30 seconds.
         *
         * @param timeout
         *            the lock timeout duration
         * @return this builder
         */
        public Builder defaultLockTimeout(Duration timeout) {
            this.defaultLockTimeout = timeout;
            return this;
        }

        /**
         * Sets the delay between retry attempts. Default: 200ms.
         *
         * @param delay
         *            the retry delay duration
         * @return this builder
         */
        public Builder retryDelay(Duration delay) {
            this.retryDelay = delay;
            return this;
        }

        /**
         * Sets the upper bound on the retry delay after exponential backoff growth. Default: equal to
         * {@link #retryDelay(Duration)} (no growth).
         *
         * @param maxDelay
         *            the maximum retry delay duration
         * @return this builder
         */
        public Builder maxRetryDelay(Duration maxDelay) {
            this.maxRetryDelay = maxDelay;
            return this;
        }

        /**
         * Sets the multiplier applied per retry attempt for exponential backoff. Default: 1.0 (no growth).
         *
         * <p>
         * Effective delay = min(maxRetryDelay, retryDelay * multiplier^attempt).
         * </p>
         *
         * @param multiplier
         *            the multiplier (must be >= 1.0)
         * @return this builder
         */
        public Builder retryDelayMultiplier(double multiplier) {
            this.retryDelayMultiplier = multiplier;
            return this;
        }

        /**
         * Sets the jitter ratio applied to the computed retry delay. Default: 0.0 (no jitter).
         *
         * <p>
         * The actual sleep is sampled uniformly from [(1-r)*delay, (1+r)*delay].
         * </p>
         *
         * @param ratio
         *            the jitter ratio in [0.0, 1.0]
         * @return this builder
         */
        public Builder retryDelayJitterRatio(double ratio) {
            this.retryDelayJitterRatio = ratio;
            return this;
        }

        /**
         * Sets the maximum number of lock acquisition retries. Default: 3.
         *
         * @param maxRetryAttempts
         *            max retry attempts
         * @return this builder
         */
        public Builder maxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
            return this;
        }

        /**
         * Sets the clock drift compensation factor (0.0 to 1.0). Default: 0.01 (1%).
         *
         * @param clockDriftFactor
         *            the drift factor
         * @return this builder
         */
        public Builder clockDriftFactor(double clockDriftFactor) {
            this.clockDriftFactor = clockDriftFactor;
            return this;
        }

        /**
         * Sets the maximum time to wait for lock acquisition. Default: 10 seconds.
         *
         * @param timeout
         *            the acquisition timeout duration
         * @return this builder
         */
        public Builder lockAcquisitionTimeout(Duration timeout) {
            this.lockAcquisitionTimeout = timeout;
            return this;
        }

        /**
         * Sets the wait strategy to use when waiting for a lock to be released.
         *
         * <p>
         * The default is {@link WaitStrategy#KEYSPACE_NOTIFICATIONS}, which provides instant notification when a lock
         * is released. This requires RESP3 protocol and will auto-configure the Redis server.
         * </p>
         *
         * <p>
         * Use {@link WaitStrategy#POLLING} only if:
         * <ul>
         * <li>Your Redis deployment restricts CONFIG commands</li>
         * <li>Keyspace notifications are not available</li>
         * <li>You need to support legacy RESP2 clients (not recommended)</li>
         * </ul>
         *
         * @param strategy
         *            the wait strategy to use
         * @return this builder
         */
        public Builder waitStrategy(WaitStrategy strategy) {
            this.waitStrategy = strategy;
            return this;
        }

        /**
         * Shorthand for {@code waitStrategy(WaitStrategy.POLLING)}.
         *
         * <p>
         * Use this when keyspace notifications are not available in your environment.
         * </p>
         *
         * @return this builder
         */
        public Builder usePolling() {
            return waitStrategy(WaitStrategy.POLLING);
        }

        /**
         * Builds the configuration.
         *
         * <p>
         * Valid configurations:
         * </p>
         * <ul>
         * <li><b>1 node</b>: Single-node mode (optimized, no distributed consensus)</li>
         * <li><b>3+ nodes</b>: Multi-node Redlock (distributed consensus with quorum)</li>
         * </ul>
         *
         * <p>
         * <b>Note:</b> 2 nodes is not supported as it cannot form a proper quorum (N/2+1 = 2 means both nodes must
         * agree, providing no fault tolerance).
         * </p>
         *
         * @return the built configuration
         * @throws IllegalArgumentException
         *             if configuration is invalid
         */
        public RedlockConfiguration build() {
            if (redisNodes.isEmpty()) {
                throw new IllegalArgumentException("At least one Redis node must be configured");
            }
            if (redisNodes.size() == 2) {
                throw new IllegalArgumentException("2 nodes is not supported. Use 1 node for single-node mode, "
                        + "or 3+ nodes for distributed Redlock.");
            }
            if (defaultLockTimeout == null || defaultLockTimeout.isNegative() || defaultLockTimeout.isZero()) {
                throw new IllegalArgumentException("Default lock timeout must be positive");
            }
            if (retryDelay == null || retryDelay.isNegative()) {
                throw new IllegalArgumentException("Retry delay cannot be negative");
            }
            if (maxRetryDelay != null && (maxRetryDelay.isNegative() || maxRetryDelay.compareTo(retryDelay) < 0)) {
                throw new IllegalArgumentException("Max retry delay must be >= retry delay");
            }
            if (retryDelayMultiplier < 1.0) {
                throw new IllegalArgumentException("Retry delay multiplier must be >= 1.0");
            }
            if (retryDelayJitterRatio < 0.0 || retryDelayJitterRatio > 1.0) {
                throw new IllegalArgumentException("Retry delay jitter ratio must be between 0.0 and 1.0");
            }
            if (maxRetryAttempts < 0) {
                throw new IllegalArgumentException("Max retry attempts cannot be negative");
            }
            if (clockDriftFactor < 0 || clockDriftFactor > 1) {
                throw new IllegalArgumentException("Clock drift factor must be between 0 and 1");
            }
            return new RedlockConfiguration(this);
        }
    }
}
