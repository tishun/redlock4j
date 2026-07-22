# RedlockManager API Reference

The `RedlockManager` is the main entry point for creating distributed locks and other synchronization primitives.

## Creating a RedlockManager

### Factory Methods

```java
// Using Lettuce driver (recommended)
RedlockManager manager = RedlockManager.withLettuce(config);

// Using Jedis driver
RedlockManager manager = RedlockManager.withJedis(config);
```

**Parameters:**

- `config` - A `RedlockConfiguration` instance

**Example:**

```java
RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1.example.com", 6379)
    .addRedisNode("redis2.example.com", 6379)
    .addRedisNode("redis3.example.com", 6379)
    .defaultLockTimeout(Duration.ofSeconds(30))
    .build();

try (RedlockManager manager = RedlockManager.withLettuce(config)) {
    // Use the manager
}
```

## Lock Creation Methods

### createLock()

Creates a standard distributed lock.

```java
public Redlock createLock(String lockKey)
```

**Parameters:**

- `lockKey` - Unique identifier for the lock

**Returns:** A `Redlock` instance implementing `java.util.concurrent.locks.Lock`

**Example:**

```java
Redlock lock = manager.createLock("my-resource");
lock.lock();
try {
    // Critical section
} finally {
    lock.unlock();
}
```

### createFairLock()

Creates a fair lock with FIFO ordering guarantees.

```java
public Lock createFairLock(String lockKey)
```

!!! warning "Performance Note"
    FairLock performs significantly better with polling wait strategy. Consider using `.usePolling()` in your configuration.

**Example:**

```java
Lock fairLock = manager.createFairLock("fair-resource");
```

### createMultiLock()

Creates a lock that atomically acquires multiple resources.

```java
public Lock createMultiLock(List<String> lockKeys)
```

**Example:**

```java
Lock multiLock = manager.createMultiLock(
    Arrays.asList("account:1", "account:2", "account:3")
);
```

### createReadWriteLock()

Creates a read-write lock allowing multiple readers or single writer.

```java
public RedlockReadWriteLock createReadWriteLock(String lockKey)
```

**Example:**

```java
RedlockReadWriteLock rwLock = manager.createReadWriteLock("shared-data");
rwLock.readLock().lock();   // Multiple readers allowed
rwLock.writeLock().lock();  // Exclusive writer
```

### createSemaphore()

Creates a distributed semaphore with a fixed number of permits.

```java
public RedlockSemaphore createSemaphore(String semaphoreKey, int permits)
```

**Example:**

```java
RedlockSemaphore semaphore = manager.createSemaphore("api-rate-limit", 10);
```

### createCountDownLatch()

Creates a distributed countdown latch.

```java
public RedlockCountDownLatch createCountDownLatch(String latchKey, int count)
```

**Example:**

```java
RedlockCountDownLatch latch = manager.createCountDownLatch("startup", 3);
```

## Async Lock Creation Methods

### createAsyncLock()

Creates an asynchronous lock using `CompletionStage`.

```java
public AsyncRedlock createAsyncLock(String lockKey)
```

### createRxLock()

Creates a reactive lock using RxJava.

```java
public RxRedlock createRxLock(String lockKey)
```

### createAsyncRxLock()

Creates a lock supporting both async and reactive APIs.

```java
public AsyncRedlockImpl createAsyncRxLock(String lockKey)
```

## Utility Methods

### getConnectedNodeCount()

Returns the number of Redis nodes currently connected.

```java
public int getConnectedNodeCount()
```

### getQuorum()

Returns the quorum size required for lock acquisition.

```java
public int getQuorum()
```

### getDriverType()

Returns the Redis driver type in use.

```java
public DriverType getDriverType()
```

### isHealthy()

Checks if the manager can reach a quorum of Redis nodes.

```java
public boolean isHealthy()
```

### close()

Closes the manager and releases all resources.

```java
public void close()
```

!!! tip "Resource Management"
    Always use try-with-resources or explicitly call `close()` to prevent resource leaks.

## Thread Safety

`RedlockManager` is thread-safe. A single instance can be shared across multiple threads.

## Next Steps

- [Lock Types](lock-types.md) - Detailed API for each lock type
- [Async & Reactive](async-reactive.md) - Async and reactive APIs
- [Configuration](configuration.md) - Configuration options
