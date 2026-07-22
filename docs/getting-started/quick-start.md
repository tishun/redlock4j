# Quick Start

This guide will help you get started with Redlock4j in just a few minutes.

## Basic Setup

### 1. Configure the Redis Nodes

Redlock4j does not take pre-built connection pools or clients. Instead, you
declare each Redis node as a host/port pair in a `RedlockConfiguration`. For a
full Redlock deployment you should use at least 3 independent Redis instances.

```java
import org.codarama.redlock4j.configuration.RedlockConfiguration;

import java.time.Duration;

RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1.example.com", 6379)
    .addRedisNode("redis2.example.com", 6379)
    .addRedisNode("redis3.example.com", 6379)
    .defaultLockTimeout(Duration.ofSeconds(30))
    .retryDelay(Duration.ofMillis(200))
    .maxRetryAttempts(3)
    .build();
```

!!! info "Number of nodes"
    A single node runs in single-node mode, and 3 or more nodes run in quorum
    mode. Configuring exactly 2 nodes throws an `IllegalArgumentException` at
    `build()`, since a quorum cannot be formed.

### 2. Create a RedlockManager

The `RedlockManager` owns the connections to the configured nodes. It is
`AutoCloseable`, so create it inside a try-with-resources block and let it
manage the underlying pools for you. Use `withJedis(config)` for the Jedis
driver, or `withLettuce(config)` for Lettuce.

```java
import org.codarama.redlock4j.RedlockManager;

try (RedlockManager manager = RedlockManager.withJedis(config)) {
    // create and use locks here
}
```

### 3. Acquire and Release Locks

`createLock(name)` returns a standard `java.util.concurrent.locks.Lock`. The
lock TTL and retry behaviour come from the configuration, so `lock()` and
`unlock()` take no arguments.

```java
import java.util.concurrent.locks.Lock;

Lock lock = manager.createLock("my-resource");

lock.lock();
try {
    // Lock acquired successfully - perform your critical section here
    System.out.println("Lock acquired! Performing critical operation...");
    performCriticalOperation();
} finally {
    // Always release the lock
    lock.unlock();
    System.out.println("Lock released");
}
```

If you would rather not block indefinitely, use `tryLock` with a timeout:

```java
import java.util.concurrent.locks.Lock;

Lock lock = manager.createLock("my-resource");

if (lock.tryLock(Duration.ofSeconds(5))) {
    try {
        System.out.println("Lock acquired! Performing critical operation...");
        performCriticalOperation();
    } finally {
        lock.unlock();
    }
} else {
    // Could not acquire the lock within the timeout
    System.out.println("Could not acquire lock");
}
```

## Complete Example

Here's a complete working example:

```java
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

import java.time.Duration;
import java.util.concurrent.locks.Lock;

public class RedlockExample {
    public static void main(String[] args) {
        // Configure the Redis nodes (host/port pairs)
        RedlockConfiguration config = RedlockConfiguration.builder()
            .addRedisNode("localhost", 6379)
            .addRedisNode("localhost", 6380)
            .addRedisNode("localhost", 6381)
            .defaultLockTimeout(Duration.ofSeconds(10))
            .retryDelay(Duration.ofMillis(200))
            .maxRetryAttempts(3)
            .build();

        // RedlockManager is AutoCloseable - it owns the node connections
        try (RedlockManager manager = RedlockManager.withJedis(config)) {
            Lock lock = manager.createLock("shared-resource");

            if (lock.tryLock(Duration.ofSeconds(5))) {
                try {
                    // Critical section
                    System.out.println("Processing shared resource...");
                    Thread.sleep(2000); // Simulate work
                    System.out.println("Done processing");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println("Another process is using the resource");
            }
        }
    }
}
```

## Using Lettuce Instead of Jedis

The configuration is identical - nodes are always host/port pairs. To use the
Lettuce driver, create the manager with `withLettuce(config)` instead of
`withJedis(config)`:

```java
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

import java.util.concurrent.locks.Lock;

try (RedlockManager manager = RedlockManager.withLettuce(config)) {
    Lock lock = manager.createLock("my-resource");
    // Use the same lock/unlock pattern as above
}
```

## Important Notes

!!! warning "Lock TTL"
    The lock TTL is derived from `defaultLockTimeout` in the configuration.
    Make sure it is longer than your critical section execution time. If the
    lock expires while you're still processing, another client might acquire
    the lock.

!!! tip "Always Unlock"
    Always release locks in a `finally` block to ensure they're released even if an exception occurs.

!!! info "Minimum Redis Instances"
    For a full Redlock deployment, use at least 3 independent Redis instances
    to ensure proper fault tolerance. A single node is supported for
    development and standalone use.

## Next Steps

- [Basic Usage](../guide/basic-usage.md) - Explore more usage patterns
- [Advanced Locking](../guide/advanced-locking.md) - Learn about advanced features

