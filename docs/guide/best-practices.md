# Best Practices

Follow these best practices to use Redlock4j effectively and safely in production.

All examples assume a `RedlockManager manager` created from a
`RedlockConfiguration`, and locks obtained via `manager.createLock(...)`.

## Lock Management

### Always Release Locks

Always release locks in a `finally` block:

```java
Lock lock = manager.createLock("resource");
lock.lock();
try {
    // Critical section
} finally {
    lock.unlock();  // Always execute
}
```

### Check Lock Acquisition

When you don't want to block indefinitely, use `tryLock()` and check the result:

```java
Lock lock = manager.createLock("resource");
if (lock.tryLock()) {
    try {
        // Lock acquired successfully
    } finally {
        lock.unlock();
    }
} else {
    // Failed to acquire lock - handle appropriately
    handleLockFailure();
}
```

### Use Appropriate TTL

Set the lock timeout longer than your operation. The lock's time-to-live is the
`defaultLockTimeout` configured on the manager:

```java
// Bad: TTL too short
RedlockConfiguration bad = RedlockConfiguration.builder()
    .addRedisNode("redis1", 6379)
    .defaultLockTimeout(Duration.ofSeconds(1))  // 1 second
    .build();
// performLongOperation() takes 5 seconds - lock will expire!

// Good: TTL with safety margin
RedlockConfiguration good = RedlockConfiguration.builder()
    .addRedisNode("redis1", 6379)
    .defaultLockTimeout(Duration.ofSeconds(10))  // 10 seconds
    .build();
// performLongOperation() takes 5 seconds - safe
```

If an operation occasionally runs long, extend the lock instead of setting an
excessively large TTL: `((Redlock) lock).extend(10000)`.

## Redis Configuration

### Use Independent Redis Instances

For production, use truly independent Redis instances:

```java
// Good: Independent instances on different servers
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1.example.com", 6379)
    .addRedisNode("redis2.example.com", 6379)
    .addRedisNode("redis3.example.com", 6379)
    .build();

// Bad: Master-slave replication (not independent)
// Don't use a master and its slaves as separate nodes
```

### Minimum 3 Instances

Use at least 3 Redis instances for fault tolerance:

```java
// Minimum for fault tolerance
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1", 6379)
    .addRedisNode("redis2", 6379)
    .addRedisNode("redis3", 6379)
    .build();

// Better: 5 instances for higher availability
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1", 6379)
    .addRedisNode("redis2", 6379)
    .addRedisNode("redis3", 6379)
    .addRedisNode("redis4", 6379)
    .addRedisNode("redis5", 6379)
    .build();
```

A single node is also valid for local development or non-critical use: with one
node Redlock4j runs in single-node mode. With three or more nodes it requires a
quorum.

### Use Odd Numbers

Always use an odd number of instances:

- 3, 5, 7 instances
- 2, 4, 6 instances

!!! warning "Exactly 2 nodes is not supported"
    A configuration with **exactly 2 nodes** throws `IllegalArgumentException`
    at `build()`. Use **1 node** (single-node mode) or **3 or more** nodes
    (quorum mode). Two nodes cannot form a meaningful quorum, so it is rejected
    outright.

## Error Handling

### Handle Lock Failures

```java
Lock lock = manager.createLock("resource");
if (!lock.tryLock()) {
    // Log the failure
    logger.warn("Failed to acquire lock for resource");

    // Implement a fallback strategy
    // Option 1: Retry later
    scheduleRetry();

    // Option 2: Return an error to the caller
    throw new LockAcquisitionException("Could not acquire lock");

    // Option 3: Use an alternative approach
    performAlternativeOperation();
}
```

### Handle Exceptions

```java
Lock lock = manager.createLock("resource");
boolean acquired = false;
try {
    acquired = lock.tryLock();
    if (acquired) {
        performCriticalOperation();
    }
} catch (Exception e) {
    logger.error("Error in critical section", e);
    handleError(e);
} finally {
    if (acquired) {
        try {
            lock.unlock();
        } catch (Exception e) {
            logger.error("Error releasing lock", e);
            // Don't throw - we're in a finally block
        }
    }
}
```

## Performance

### Reuse the RedlockManager

Create the `RedlockManager` once and reuse it. It owns the Redis connections, so
creating one per operation is wasteful. Creating individual locks from a shared
manager is cheap:

```java
// Good: single shared manager
public class LockService {
    private static final RedlockManager MANAGER = RedlockManager.withJedis(createConfig());

    public Lock acquireLock(String resource) {
        Lock lock = MANAGER.createLock(resource);
        lock.lock();
        return lock;
    }
}

// Bad: creating a new manager each time
public Lock acquireLock(String resource) {
    RedlockManager manager = RedlockManager.withJedis(createConfig());  // Wasteful!
    Lock lock = manager.createLock(resource);
    lock.lock();
    return lock;
}
```

### Tune Node Connections

Connection tuning happens per node via `RedisNodeConfiguration`, not via a pool
config object:

```java
RedisNodeConfiguration node = RedisNodeConfiguration.builder()
    .host("redis1")
    .port(6379)
    .connectionTimeoutMs(2000)  // time to establish a connection
    .socketTimeoutMs(2000)      // time to wait on a command
    .build();
```

### Use Appropriate Retry Settings

```java
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1", 6379)
    .addRedisNode("redis2", 6379)
    .addRedisNode("redis3", 6379)
    .maxRetryAttempts(3)                  // Don't retry too many times
    .retryDelay(Duration.ofMillis(200))   // Reasonable delay between retries
    .build();
```

## Resource Naming

### Use Descriptive Names

```java
// Good: clear and descriptive
Lock lock = manager.createLock("user:123:profile:update");
Lock lock = manager.createLock("order:456:payment:process");

// Bad: unclear names
Lock lock = manager.createLock("lock1");
Lock lock = manager.createLock("temp");
```

### Use a Consistent Naming Convention

```java
// Establish a pattern
String lockKey = String.format("%s:%s:%s",
    entityType,    // "user", "order", "product"
    entityId,      // "123", "456"
    operation);    // "update", "delete", "process"

Lock lock = manager.createLock(lockKey);
```

## Monitoring and Logging

### Log Lock Operations

```java
Lock lock = manager.createLock(resourceId);
if (lock.tryLock()) {
    logger.info("Acquired lock for resource: {}", resourceId);
    try {
        performOperation();
    } finally {
        lock.unlock();
        logger.info("Released lock for resource: {}", resourceId);
    }
} else {
    logger.warn("Failed to acquire lock for resource: {}", resourceId);
}
```

### Monitor Lock Metrics

Track important metrics:

- Lock acquisition success rate
- Lock acquisition latency
- Lock hold time
- Lock contention rate

## Testing

### Test Lock Behavior

```java
@Test
public void testLockAcquisition() {
    Lock lock = manager.createLock("test-resource");
    assertTrue(lock.tryLock(), "Should acquire lock");

    // Try to acquire the same lock from another thread - should fail
    Lock lock2 = manager.createLock("test-resource");
    assertFalse(lockFromOtherThread(lock2), "Should not acquire an already locked resource");

    lock.unlock();
}
```

### Use Testcontainers

For integration tests, point node configuration at the container's mapped port:

```java
@Testcontainers
public class RedlockIntegrationTest {
    @Container
    private static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Test
    public void testWithRealRedis() {
        RedlockConfiguration config = RedlockConfiguration.builder()
            .addRedisNode(redis.getHost(), redis.getFirstMappedPort())
            .build();

        try (RedlockManager manager = RedlockManager.withJedis(config)) {
            // Test with real Redis
        }
    }
}
```

## Common Pitfalls

### Don't Forget to Unlock

```java
// Bad: no unlock
Lock lock = manager.createLock("resource");
lock.lock();
performOperation();  // If this throws, the lock is never released!
```

Always pair `lock()` / successful `tryLock()` with `unlock()` in a `finally`
block.

### Don't Configure Exactly 2 Nodes

```java
// Bad: exactly 2 nodes throws IllegalArgumentException at build()
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1", 6379)
    .addRedisNode("redis2", 6379)
    .build();  // throws!
```

Use 1 node (single-node mode) or 3+ nodes (quorum mode).

### Don't Ignore Lock Acquisition Failures

```java
// Bad: assuming the lock is always acquired
Lock lock = manager.createLock("resource");
lock.tryLock();          // return value ignored
performOperation();      // What if the lock wasn't acquired?
```

## Next Steps

- [API Reference](../api/redlock-manager.md) - Detailed API documentation
- [Advanced Locking](advanced-locking.md) - Advanced features
