<p align="center">
  <img src="logo-large.svg" alt="Redlock4j logo" width="480">
</p>

[![CI](https://github.com/Codarama/redlock4j/actions/workflows/ci.yml/badge.svg)](https://github.com/Codarama/redlock4j/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/Codarama/redlock4j/graph/badge.svg?token=EK4LMLJ533)](https://codecov.io/gh/Codarama/redlock4j)
[![Maven Central](https://img.shields.io/maven-central/v/org.codarama/redlock4j?versionSuffix=RELEASE)](https://maven-badges.herokuapp.com/maven-central/org.codarama/redlock4j)
[![Javadocs](https://www.javadoc.io/badge/org.codarama/redlock4j.svg)](https://javadoc.io/doc/org.codarama/redlock4j)
[![Java](https://img.shields.io/badge/Java-8%2B-blue.svg)](https://openjdk.java.net/)
[![Guide](https://img.shields.io/badge/mkdocs-guide-526CFE?logo=materialformkdocs&logoColor=white)](https://codarama.github.io/redlock4j/)

A robust Java implementation of the [Redlock distributed locking algorithm](https://redis.io/topics/distlock) for Redis.

## Overview

Redlock4j provides a reliable distributed locking mechanism using Redis, implementing the Redlock algorithm proposed by Redis creator Antirez. It ensures mutual exclusion across distributed systems with high availability and fault tolerance.

## Key Features

- **Flexible Deployment** - Works as a [full Redlock implementation](https://redlock4j.codarama.org/guide/configuration/#single-node-mode) with multiple Redis nodes for high availability, or as a [standalone distributed lock](https://redlock4j.codarama.org/guide/configuration/#single-node-mode) with a single Redis node
- **Multiple Redis Drivers** - Integrated support for [Jedis](https://github.com/redis/jedis) and [Lettuce](https://github.com/redis/lettuce), extensible to other drivers
- **[Keyspace Notifications](https://redis.io/docs/latest/develop/use/keyspace-notifications/)** - Uses Redis pub/sub for instant lock release detection (20-50x faster than polling under contention)
- **Multi-interface API** - Supports standard `java.util.concurrent.locks.Lock` interface, as well as [async](https://redlock4j.codarama.org/guide/async-reactive/) and [reactive](https://redlock4j.codarama.org/guide/async-reactive/) APIs
- **Advanced Locking Primitives** - [Fair locks](https://redlock4j.codarama.org/guide/lock-types/#fair-lock), [multi-locks](https://redlock4j.codarama.org/guide/lock-types/#multi-lock), [read-write locks](https://redlock4j.codarama.org/guide/lock-types/#read-write-lock), [semaphores](https://redlock4j.codarama.org/guide/lock-types/#semaphore), and [countdown latches](https://redlock4j.codarama.org/guide/lock-types/#countdown-latch)
- **Lock Extension** - Extend lock validity time without releasing and re-acquiring
- **Atomic CAS/CAD Detection** - Auto-detects and uses native [Redis 8.4+ CAS/CAD commands](https://redis.io/docs/latest/operate/oss_and_stack/stack-with-enterprise/release-notes/redisce/redisos-8.4-release-notes/) when available
- **Java 8+** - Compatible with Java 8 and higher, tested against Java 8, 11, 17, and 21

## Quick Example

```java
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;

import java.time.Duration;
import java.util.concurrent.locks.Lock;

// Configure the Redis nodes (host/port) and lock behaviour
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1.example.com", 6379)
    .addRedisNode("redis2.example.com", 6379)
    .addRedisNode("redis3.example.com", 6379)
    .defaultLockTimeout(Duration.ofSeconds(30))
    .retryDelay(Duration.ofMillis(200))
    .maxRetryAttempts(3)
    .build();

// RedlockManager is AutoCloseable - use try-with-resources
try (RedlockManager manager = RedlockManager.withJedis(config)) {
    Lock lock = manager.createLock("my-resource");
    lock.lock();
    try {
        // Critical section - your protected code here
        performCriticalOperation();
    } finally {
        // Always unlock in a finally block
        lock.unlock();
    }
}
```

## Why Redlock4j?

### Distributed Lock Guarantees

Redlock4j provides the following safety and liveness guarantees:

1. **Mutual Exclusion** - At most one client can hold a lock at any given time
2. **Deadlock Free** - Eventually it's always possible to acquire a lock, even if the client that locked a resource crashes
3. **Fault Tolerance** - As long as the majority of Redis nodes are up, clients can acquire and release locks

### Use Cases

- **Distributed Task Scheduling** - Ensure only one instance processes a scheduled task
- **Resource Access Control** - Coordinate access to shared resources across services
- **Leader Election** - Implement leader election in distributed systems
- **Rate Limiting** - Implement distributed rate limiting
- **Cache Invalidation** - Coordinate cache updates across multiple instances

## Getting Started

Check out the [Installation Guide](getting-started/installation.md) to add Redlock4j to your project, or jump straight to the [Quick Start](getting-started/quick-start.md) to see it in action.

## License

Redlock4j is released under the [MIT License](https://opensource.org/licenses/MIT).

