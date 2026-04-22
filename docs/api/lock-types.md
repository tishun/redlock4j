# Lock Types API Reference

This page documents all distributed lock types provided by Redlock4j.

## Redlock (Standard Lock)

The standard distributed lock implementing `java.util.concurrent.locks.Lock`.

### Methods

| Method | Description |
|--------|-------------|
| `void lock()` | Acquires the lock, blocking until available |
| `void lockInterruptibly()` | Acquires the lock, can be interrupted |
| `boolean tryLock()` | Tries to acquire immediately, returns false if unavailable |
| `boolean tryLock(long time, TimeUnit unit)` | Tries to acquire within timeout |
| `boolean tryLock(Duration timeout)` | Tries to acquire within timeout |
| `void unlock()` | Releases the lock |
| `boolean isHeldByCurrentThread()` | Returns true if current thread holds the lock |
| `String getLockKey()` | Returns the lock key |

### Example

```java
Redlock lock = manager.createLock("my-resource");

// Blocking acquisition
lock.lock();
try {
    performCriticalOperation();
} finally {
    lock.unlock();
}

// Non-blocking with timeout
if (lock.tryLock(5, TimeUnit.SECONDS)) {
    try {
        performCriticalOperation();
    } finally {
        lock.unlock();
    }
} else {
    handleLockUnavailable();
}
```

### Reentrancy

Redlock supports reentrant locking - the same thread can acquire the lock multiple times:

```java
lock.lock();       // hold count = 1
lock.lock();       // hold count = 2
lock.unlock();     // hold count = 1
lock.unlock();     // hold count = 0, lock released
```

---

## FairLock

A distributed lock with FIFO ordering guarantees.

### Methods

Same as `Redlock` - implements `java.util.concurrent.locks.Lock`.

### Example

```java
Lock fairLock = manager.createFairLock("fair-resource");
fairLock.lock();
try {
    // Threads acquire in request order
} finally {
    fairLock.unlock();
}
```

!!! warning "Use Polling"
    FairLock performs ~60x better with polling. Configure with `.usePolling()`.

---

## MultiLock

Atomically acquires multiple resources to prevent deadlocks.

### Methods

Same as `Redlock` - implements `java.util.concurrent.locks.Lock`.

### Example

```java
Lock multiLock = manager.createMultiLock(
    Arrays.asList("account:A", "account:B", "account:C")
);

multiLock.lock();
try {
    // All three resources locked atomically
    transferFunds(accountA, accountB, accountC);
} finally {
    multiLock.unlock();
}
```

!!! info "Deadlock Prevention"
    Keys are sorted internally to ensure consistent ordering across all clients.

---

## RedlockReadWriteLock

Allows multiple concurrent readers or a single exclusive writer.

### Methods

| Method | Description |
|--------|-------------|
| `Lock readLock()` | Returns the read lock |
| `Lock writeLock()` | Returns the write lock |

### Example

```java
RedlockReadWriteLock rwLock = manager.createReadWriteLock("shared-data");

// Multiple readers can hold simultaneously
rwLock.readLock().lock();
try {
    readSharedData();
} finally {
    rwLock.readLock().unlock();
}

// Writers get exclusive access
rwLock.writeLock().lock();
try {
    writeSharedData();
} finally {
    rwLock.writeLock().unlock();
}
```

---

## RedlockSemaphore

A distributed semaphore with a fixed number of permits.

### Methods

| Method | Description |
|--------|-------------|
| `void acquire()` | Acquires one permit, blocking |
| `void acquire(int permits)` | Acquires multiple permits, blocking |
| `boolean tryAcquire(Duration timeout)` | Tries to acquire one permit |
| `boolean tryAcquire(int permits, Duration timeout)` | Tries to acquire multiple permits |
| `void release()` | Releases one permit |
| `void release(int permits)` | Releases multiple permits |
| `int availablePermits()` | Returns estimated available permits |

### Example

```java
// Rate limiting: max 10 concurrent API calls
RedlockSemaphore semaphore = manager.createSemaphore("api-limit", 10);

if (semaphore.tryAcquire(Duration.ofSeconds(5))) {
    try {
        callExternalAPI();
    } finally {
        semaphore.release();
    }
}
```

---

## RedlockCountDownLatch

A distributed countdown latch for coordinating multiple processes.

### Methods

| Method | Description |
|--------|-------------|
| `void countDown()` | Decrements the count by one |
| `void await()` | Waits until count reaches zero |
| `boolean await(Duration timeout)` | Waits with timeout |
| `long getCount()` | Returns current count |

### Example

```java
// Coordinator creates latch
RedlockCountDownLatch latch = manager.createCountDownLatch("startup", 3);

// Each service signals completion
latch.countDown();

// Main thread waits for all
latch.await();
System.out.println("All services ready!");
```

## Next Steps

- [Async & Reactive](async-reactive.md) - Async and reactive APIs
- [Configuration](configuration.md) - Configuration options
