/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.configuration;

/**
 * Configuration for a single Redis node in the Redlock cluster.
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class RedisNodeConfiguration {
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int connectionTimeoutMs;
    private final int socketTimeoutMs;

    private RedisNodeConfiguration(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.password = builder.password;
        this.database = builder.database;
        this.connectionTimeoutMs = builder.connectionTimeoutMs;
        this.socketTimeoutMs = builder.socketTimeoutMs;
    }

    /**
     * Returns the Redis server hostname or IP address.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the Redis server port.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the Redis authentication password, or null if not set.
     *
     * @return the password, or null
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the Redis database index.
     *
     * @return the database index
     */
    public int getDatabase() {
        return database;
    }

    /**
     * Returns the connection timeout in milliseconds.
     *
     * @return connection timeout in ms
     */
    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Returns the socket read/write timeout in milliseconds.
     *
     * @return socket timeout in ms
     */
    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    /**
     * Creates a new builder for RedisNodeConfiguration.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link RedisNodeConfiguration} instances.
     */
    public static class Builder {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private int connectionTimeoutMs = 2000;
        private int socketTimeoutMs = 2000;

        /**
         * Sets the Redis server hostname or IP address. Default: "localhost".
         *
         * @param host
         *            the hostname or IP
         * @return this builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the Redis server port. Default: 6379.
         *
         * @param port
         *            the port number (1-65535)
         * @return this builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the Redis authentication password. Default: null (no auth).
         *
         * @param password
         *            the password
         * @return this builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the Redis database index. Default: 0.
         *
         * @param database
         *            the database index
         * @return this builder
         */
        public Builder database(int database) {
            this.database = database;
            return this;
        }

        /**
         * Sets the connection timeout in milliseconds. Default: 2000ms.
         *
         * @param connectionTimeoutMs
         *            timeout in milliseconds
         * @return this builder
         */
        public Builder connectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }

        /**
         * Sets the socket read/write timeout in milliseconds. Default: 2000ms.
         *
         * @param socketTimeoutMs
         *            timeout in milliseconds
         * @return this builder
         */
        public Builder socketTimeoutMs(int socketTimeoutMs) {
            this.socketTimeoutMs = socketTimeoutMs;
            return this;
        }

        /**
         * Builds the RedisNodeConfiguration.
         *
         * @return the configured instance
         * @throws IllegalArgumentException
         *             if host is null/empty or port is invalid
         */
        public RedisNodeConfiguration build() {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalArgumentException("Host cannot be null or empty");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return new RedisNodeConfiguration(this);
        }
    }

    @Override
    public String toString() {
        return "RedisNodeConfiguration{" + "host='" + host + '\'' + ", port=" + port + ", database=" + database
                + ", connectionTimeoutMs=" + connectionTimeoutMs + ", socketTimeoutMs=" + socketTimeoutMs + '}';
    }
}
