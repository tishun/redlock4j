/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */

/**
 * Runnable example applications demonstrating Redlock4j usage.
 *
 * <p>
 * <strong>These are NOT unit tests.</strong> They are executable main classes designed to be run manually against a
 * real Redis cluster for demonstration purposes.
 * </p>
 *
 * <p>
 * To run an example:
 * </p>
 * 
 * <pre>
 * # Start 3 Redis instances first:
 * redis-server --port 6379
 * redis-server --port 6380
 * redis-server --port 6381
 *
 * # Then run the example:
 * mvn exec:java -Dexec.mainClass="org.codarama.redlock4j.examples.RedlockUsageExample"
 * </pre>
 *
 * @see org.codarama.redlock4j.examples.RedlockUsageExample Basic usage
 * @see org.codarama.redlock4j.examples.AdvancedLockingExample Advanced primitives
 * @see org.codarama.redlock4j.examples.AsyncRxUsageExample Async/Rx usage
 */
package org.codarama.redlock4j.examples;
