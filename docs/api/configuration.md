# Configuration API Reference

Detailed reference for configuring Redlock4j.

## RedlockConfiguration

Main configuration class built using the builder pattern.

### Builder Methods

```java
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1.example.com", 6379)
    .addRedisNode("redis2.example.com", 6379)
    .addRedisNode("redis3.example.com", 6379)
    .defaultLockTimeout(Duration.ofSeconds(30))
    .retryDelay(Duration.ofMillis(200))
    .maxRetryAttempts(3)
    .clockDriftFactor(0.01)
    .lockAcquisitionTimeout(Duration.ofSeconds(10))
    .build();
```

### Configuration Properties

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `addRedisNode(host, port)` | String, int | - | Adds a Redis node |
| `addRedisNode(host, port, password)` | String, int, String | - | Adds a Redis node with password |
| `defaultLockTimeout(Duration)` | Duration | 30s | Default lock TTL |
| `retryDelay(Duration)` | Duration | 200ms | Delay between retry attempts |
| `maxRetryAttempts(int)` | int | 3 | Maximum retry attempts |
| `clockDriftFactor(double)` | double | 0.01 | Clock drift compensation |
| `lockAcquisitionTimeout(Duration)` | Duration | 10s | Max time to wait for lock |
| `usePolling()` | - | - | Use polling instead of keyspace notifications |
| `waitStrategy(WaitStrategy)` | WaitStrategy | KEYSPACE_NOTIFICATIONS | Wait strategy |

---

## Adding Redis Nodes

### Simple Node

```java
.addRedisNode("localhost", 6379)
```

### Node with Password

```java
.addRedisNode("redis.example.com", 6379, "secretpassword")
```

### Advanced Node Configuration

```java
RedisNodeConfiguration node = RedisNodeConfiguration.builder()
    .host("redis.example.com")
    .port(6379)
    .password("secretpassword")
    .connectionTimeoutMs(5000)
    .socketTimeoutMs(5000)
    .build();

config.addRedisNode(node);
```

---

## Wait Strategy

Controls how locks wait for release when contended.

### Keyspace Notifications (Default)

```java
// Default - instant wake-up via Redis pub/sub
RedlockConfiguration.builder()
    .addRedisNode("localhost", 6379)
    .build();
```

- **Pros:** ~3x faster wake-up latency, lower CPU
- **Cons:** Requires RESP3, FairLock performance issues

### Polling

```java
// Use polling for FairLock or compatibility
RedlockConfiguration.builder()
    .addRedisNode("localhost", 6379)
    .usePolling()
    .retryDelay(Duration.ofMillis(10))  // Poll interval
    .build();
```

- **Pros:** Works everywhere, better FairLock performance
- **Cons:** Higher latency, higher CPU

!!! warning "FairLock"
    Always use `.usePolling()` when using FairLock.

---

## Node Configuration

Valid deployment modes:

| Nodes | Mode | Description |
|:-----:|------|-------------|
| 1 | Single-node | Optimized, no distributed consensus |
| 2 | **Invalid** | Cannot form quorum |
| 3+ | Multi-node | Distributed consensus with quorum |

### Single Node (Development)

```java
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("localhost", 6379)
    .defaultLockTimeout(Duration.ofSeconds(10))
    .maxRetryAttempts(1)
    .build();
```

### Multi-Node (Production)

```java
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1.prod", 6379)
    .addRedisNode("redis2.prod", 6379)
    .addRedisNode("redis3.prod", 6379)
    .defaultLockTimeout(Duration.ofSeconds(30))
    .retryDelay(Duration.ofMillis(200))
    .maxRetryAttempts(5)
    .clockDriftFactor(0.01)
    .lockAcquisitionTimeout(Duration.ofSeconds(10))
    .build();
```

---

## Complete Example

```java
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import java.time.Duration;

public class ConfigurationExample {

    public static RedlockManager createManager() {
        RedlockConfiguration config = RedlockConfiguration.builder()
            // Redis nodes (use odd number: 3 or 5)
            .addRedisNode("redis1.example.com", 6379, "password")
            .addRedisNode("redis2.example.com", 6379, "password")
            .addRedisNode("redis3.example.com", 6379, "password")

            // Lock timing
            .defaultLockTimeout(Duration.ofSeconds(30))
            .lockAcquisitionTimeout(Duration.ofSeconds(10))

            // Retry behavior
            .retryDelay(Duration.ofMillis(200))
            .maxRetryAttempts(5)

            // Clock drift (1% is safe for NTP-synced servers)
            .clockDriftFactor(0.01)

            .build();

        return RedlockManager.withLettuce(config);
    }
}
```

---

## Best Practices

### Lock TTL

| Operation Duration | Recommended TTL |
|-------------------|-----------------|
| < 1 second | 5-10 seconds |
| 1-10 seconds | 30-60 seconds |
| > 10 seconds | Reconsider approach |

### Retry Configuration

```java
// High contention
.retryDelay(Duration.ofMillis(500))
.maxRetryAttempts(10)

// Low latency requirement
.retryDelay(Duration.ofMillis(50))
.maxRetryAttempts(2)
```

### Clock Drift

```java
// NTP-synchronized servers
.clockDriftFactor(0.001)  // 0.1%

// Unsynchronized servers
.clockDriftFactor(0.05)   // 5%
```

## Next Steps

- [RedlockManager](redlock-manager.md) - Main API entry point
- [Lock Types](lock-types.md) - Available lock types

