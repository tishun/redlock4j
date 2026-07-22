# Basic Usage

This guide covers common usage patterns and scenarios for Redlock4j.

All examples assume you have created a `RedlockManager` from a `RedlockConfiguration`.
The manager is the entry point: it owns the Redis connections and hands out locks
that implement `java.util.concurrent.locks.Lock`.

```java
import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.Redlock;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import java.time.Duration;
import java.util.concurrent.locks.Lock;

RedlockConfiguration config = RedlockConfiguration.builder()
    .addRedisNode("redis1", 6379)
    .addRedisNode("redis2", 6379)
    .addRedisNode("redis3", 6379)
    .defaultLockTimeout(Duration.ofSeconds(30))
    .retryDelay(Duration.ofMillis(200))
    .maxRetryAttempts(3)
    .build();

RedlockManager manager = RedlockManager.withJedis(config); // or withLettuce(config)
```

## Simple Lock Pattern

The most basic usage pattern. Acquire the lock, do the work, release in a `finally`:

```java
Lock lock = manager.createLock("resource-id");
lock.lock();
try {
    // Critical section
} finally {
    lock.unlock();
}
```

## Try-Lock Pattern

Attempt to acquire a lock without blocking:

```java
Lock lock = manager.createLock("resource-id");
if (lock.tryLock()) {
    try {
        // Got the lock immediately
    } finally {
        lock.unlock();
    }
} else {
    // Lock not available, handle accordingly
}
```

## Lock with Timeout

Wait for a lock up to a bounded amount of time:

```java
Lock lock = manager.createLock("resource-id");
if (lock.tryLock(Duration.ofSeconds(5))) { // wait up to 5 seconds
    try {
        // Acquired lock within timeout
    } finally {
        lock.unlock();
    }
} else {
    // Timeout expired
}
```

`tryLock(long, TimeUnit)` is also available if you prefer the standard
`java.util.concurrent` signature:

```java
if (lock.tryLock(5, TimeUnit.SECONDS)) {
    // ...
}
```

## Extending Lock Duration

If your operation takes longer than expected, you can extend the lock. The
`extend` method is defined on `Redlock`, so cast the lock reference and pass the
additional time in milliseconds:

```java
Lock lock = manager.createLock("resource-id");
lock.lock();
try {
    // Do some work
    performPartialWork();

    // Need more time, extend the lock by 10 seconds
    boolean extended = ((Redlock) lock).extend(10000);
    if (extended) {
        // Continue working
        performMoreWork();
    }
} finally {
    lock.unlock();
}
```

## Checking Lock Validity

There is no `isValid` method. Instead, ask whether the current thread still holds
the lock, or inspect the remaining validity time:

```java
Lock lock = manager.createLock("resource-id");
lock.lock();
try {
    performWork();

    Redlock redlock = (Redlock) lock;
    if (redlock.isHeldByCurrentThread()
            && !redlock.getRemainingValidityTime().isZero()) {
        // Lock is still held and has time left
        performMoreWork();
    } else {
        // Lock expired or was released
        handleExpiredLock();
    }
} finally {
    lock.unlock();
}
```

## Multiple Resources

Lock multiple resources atomically with a multi-lock. `createMultiLock` takes a
`List` of resource keys and returns a single `Lock` that governs all of them:

```java
import java.util.Arrays;

Lock lock = manager.createMultiLock(
    Arrays.asList("resource-1", "resource-2", "resource-3"));

if (lock.tryLock()) {
    try {
        // All resources are locked
        processMultipleResources();
    } finally {
        lock.unlock();
    }
}
```

## Reentrant Locks

Redlock4j supports reentrant locks (the same thread can acquire the same lock
multiple times; the lock is only released when the hold count returns to zero):

```java
Lock lock = manager.createLock("resource-id");
lock.lock();
try {
    // First acquisition

    lock.lock();
    try {
        // Second acquisition by the same thread
    } finally {
        lock.unlock();
    }
} finally {
    lock.unlock();
}
```

## Error Handling

Proper error handling is crucial. Acquire the lock, and only unlock what you
actually hold:

```java
Lock lock = manager.createLock("resource-id");
boolean acquired = false;
try {
    acquired = lock.tryLock();
    if (acquired) {
        // Critical section
        performCriticalOperation();
    } else {
        // Failed to acquire lock
        logger.warn("Could not acquire lock for resource-id");
        handleLockFailure();
    }
} catch (Exception e) {
    logger.error("Error during critical section", e);
    handleError(e);
} finally {
    if (acquired) {
        try {
            lock.unlock();
        } catch (Exception e) {
            logger.error("Error releasing lock", e);
        }
    }
}
```

## Using with Try-With-Resources

The lock is **not** `AutoCloseable`, so you cannot put it in a
try-with-resources block. The `RedlockManager` **is** `AutoCloseable`, so use
try-with-resources on the manager to guarantee its connections are closed, and
release individual locks in a `finally` block:

```java
try (RedlockManager manager = RedlockManager.withJedis(config)) {
    Lock lock = manager.createLock("resource-id");
    lock.lock();
    try {
        // Critical section
        performCriticalOperation();
    } finally {
        lock.unlock();
    }
} // manager (and its connections) automatically closed
```

## Common Patterns

### Singleton Task Execution

Ensure only one instance executes a task:

```java
public void executeScheduledTask() {
    Lock lock = manager.createLock("scheduled-task-id");
    if (lock.tryLock()) {
        try {
            // Only one instance will execute this
            performScheduledTask();
        } finally {
            lock.unlock();
        }
    } else {
        // Another instance is already executing
        logger.info("Task already running on another instance");
    }
}
```

### Resource Pool Management

Manage access to a limited resource pool:

```java
public void processWithResource(String resourceId) {
    Lock lock = manager.createLock("resource-pool:" + resourceId);
    if (lock.tryLock()) {
        try {
            Resource resource = acquireResource(resourceId);
            processResource(resource);
        } finally {
            lock.unlock();
        }
    }
}
```

## Next Steps

- [Advanced Locking](advanced-locking.md) - Learn about advanced features
- [Best Practices](best-practices.md) - Follow recommended practices
