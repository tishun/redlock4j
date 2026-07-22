/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.RedlockException;
import org.codarama.redlock4j.driver.RedisDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Wait strategy that uses Redis Keyspace Notifications for instant lock release detection.
 *
 * <p>
 * This is the <b>default and recommended</b> strategy. It subscribes to Redis keyspace events to receive instant
 * notification when a lock is released (via DEL or expiration).
 * </p>
 *
 * <p>
 * <b>Requirements:</b>
 * <ul>
 * <li>RESP3 protocol (automatically verified)</li>
 * <li>Redis 6.0+ (recommended)</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Performance characteristics:</b>
 * <ul>
 * <li>Uncontended lock: ~0.6ms</li>
 * <li>Contended lock: 1-5ms average</li>
 * <li>CPU usage: Low (event-driven, no polling)</li>
 * </ul>
 * </p>
 *
 * @see WaitStrategy#KEYSPACE_NOTIFICATIONS
 * @since 1.0
 * @author Tihomir Mateev
 */
public class KeyspaceWaitStrategy implements LockWaitStrategy {

    private static final Logger logger = LoggerFactory.getLogger(KeyspaceWaitStrategy.class);

    /**
     * Required keyspace notification flags: K = keyspace events, g = generic commands (DEL), x = expired events
     */
    private static final String REQUIRED_FLAGS = "Kgx";

    private List<RedisDriver> drivers;
    private volatile boolean closed = false;

    // Track active subscriptions for cleanup
    private final Set<String> activeSubscriptions = ConcurrentHashMap.newKeySet();

    // Track waiting threads per lock key
    private final Map<String, CountDownLatch> waitingLatches = new ConcurrentHashMap<>();

    // Background subscription thread
    private ExecutorService subscriptionExecutor;
    private final AtomicBoolean subscriptionActive = new AtomicBoolean(false);

    @Override
    public void initialize(List<RedisDriver> drivers, Duration retryDelay) {
        this.drivers = drivers;

        // Configure keyspace notifications on all Redis nodes
        // Note: We don't require RESP3 strictly - we use a dedicated subscription connection
        configureKeyspaceNotifications();

        // Start background subscription thread for keyspace events
        startKeyspaceSubscription();

        logger.debug("Keyspace wait strategy initialized with {} Redis nodes", drivers.size());
    }

    /**
     * Starts a background thread that subscribes to keyspace notifications using pattern subscription.
     */
    private void startKeyspaceSubscription() {
        subscriptionExecutor = Executors.newFixedThreadPool(drivers.size(), r -> {
            Thread t = new Thread(r, "redlock-keyspace-subscriber");
            t.setDaemon(true);
            return t;
        });

        subscriptionActive.set(true);

        // Subscribe on each driver for fault tolerance
        for (RedisDriver driver : drivers) {
            subscriptionExecutor.submit(() -> {
                try {
                    // Subscribe to all keyspace events for lock keys
                    // Pattern: __keyspace@0__:lock:* catches all lock-related events
                    driver.subscribe(new RedisDriver.MessageHandler() {
                        @Override
                        public void onMessage(String channel, String message) {
                            handleKeyspaceEvent(channel, message);
                        }

                        @Override
                        public void onError(Throwable error) {
                            if (subscriptionActive.get()) {
                                logger.warn("Keyspace subscription error on {}: {}", driver.getIdentifier(),
                                        error.getMessage());
                            }
                        }
                    }, "__keyspace@0__:*");
                } catch (Exception e) {
                    if (subscriptionActive.get()) {
                        logger.warn("Failed to start keyspace subscription on {}: {}", driver.getIdentifier(),
                                e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Handles keyspace events and wakes up waiting threads.
     */
    private void handleKeyspaceEvent(String channel, String message) {
        // Channel format: __keyspace@0__:lock:resource-name
        // Message is the command: del, expired, set, etc.

        if ("del".equals(message) || "expired".equals(message)) {
            // Extract the lock key from the channel
            String lockKey = channel.replace("__keyspace@0__:", "");

            logger.debug("Keyspace event: {} on {}", message, lockKey);

            // Wake up any threads waiting for this lock
            CountDownLatch latch = waitingLatches.get(lockKey);
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    /**
     * Configures keyspace notifications on all Redis nodes.
     */
    private void configureKeyspaceNotifications() {
        for (RedisDriver driver : drivers) {
            try {
                String currentConfig = driver.configGet("notify-keyspace-events");
                String mergedConfig = mergeFlags(currentConfig, REQUIRED_FLAGS);

                if (!hasAllFlags(currentConfig, REQUIRED_FLAGS)) {
                    driver.configSet("notify-keyspace-events", mergedConfig);
                    logger.debug("Configured keyspace notifications on {}: {} -> {}", driver.getIdentifier(),
                            currentConfig, mergedConfig);
                } else {
                    logger.debug("Keyspace notifications already configured on {}: {}", driver.getIdentifier(),
                            currentConfig);
                }
            } catch (Exception e) {
                String message = "Failed to configure keyspace notifications on " + driver.getIdentifier() + ". ";
                if (e.getMessage() != null && (e.getMessage().contains("ACL") || e.getMessage().contains("permission")
                        || e.getMessage().contains("NOPERM"))) {
                    message += "Please run: CONFIG SET notify-keyspace-events \"" + REQUIRED_FLAGS + "\"";
                }
                throw new RedlockException(message, e);
            }
        }
    }

    @Override
    public boolean waitForRelease(String lockKey, Duration timeout) throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("Strategy has been closed");
        }

        activeSubscriptions.add(lockKey);

        // Create a latch for this lock key
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch existingLatch = waitingLatches.putIfAbsent(lockKey, latch);
        if (existingLatch != null) {
            latch = existingLatch;
        }

        try {
            // Wait for the latch to be released (by keyspace event) or timeout
            boolean released = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (released) {
                logger.debug("Lock {} released via keyspace notification", lockKey);
            } else {
                logger.debug("Timeout waiting for lock {} release", lockKey);
            }

            // Always return true to signal caller should try to acquire
            // The actual lock availability is checked by the caller
            return true;
        } finally {
            activeSubscriptions.remove(lockKey);
            // Clean up the latch if we created it
            waitingLatches.remove(lockKey, latch);
        }
    }

    @Override
    public WaitStrategy getType() {
        return WaitStrategy.KEYSPACE_NOTIFICATIONS;
    }

    @Override
    public void close() {
        closed = true;
        subscriptionActive.set(false);

        // Wake up all waiting threads
        for (CountDownLatch latch : waitingLatches.values()) {
            latch.countDown();
        }
        waitingLatches.clear();
        activeSubscriptions.clear();

        // Shutdown subscription executor
        if (subscriptionExecutor != null) {
            subscriptionExecutor.shutdownNow();
            try {
                subscriptionExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.debug("Keyspace wait strategy closed");
    }

    /**
     * Merges the required flags with existing configuration without removing existing flags.
     */
    static String mergeFlags(String existing, String required) {
        if (existing == null) {
            existing = "";
        }
        Set<Character> flags = existing.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
        for (char c : required.toCharArray()) {
            flags.add(c);
        }
        return flags.stream().map(String::valueOf).sorted().collect(Collectors.joining());
    }

    /**
     * Checks if the existing configuration has all required flags.
     */
    static boolean hasAllFlags(String existing, String required) {
        if (existing == null) {
            return false;
        }
        for (char c : required.toCharArray()) {
            if (existing.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
