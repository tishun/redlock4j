# Lock Types API Reference

This page documents the API for all distributed lock types. For detailed explanations and sequence diagrams, see the [Locking Primitives](../primitives/redlock.md) section.

## Quick Reference

| Type | Interface | Details |
|------|-----------|---------|
| [Redlock](../primitives/redlock.md) | `Lock` | Standard distributed lock |
| [FairLock](../primitives/fair-lock.md) | `Lock` | FIFO ordering |
| [MultiLock](../primitives/multi-lock.md) | `Lock` | Multi-resource |
| [ReadWriteLock](../primitives/read-write-lock.md) | `ReadWriteLock` | Reader/writer |
| [Semaphore](../primitives/semaphore.md) | Custom | Permit-based |
| [CountDownLatch](../primitives/count-down-latch.md) | Custom | Coordination |

---

## Lock Interface Methods

Applies to: `Redlock`, `FairLock`, `MultiLock`, read/write locks

| Method | Description |
|--------|-------------|
| `void lock()` | Acquires the lock, blocking until available |
| `void lockInterruptibly()` | Acquires the lock, can be interrupted |
| `boolean tryLock()` | Tries to acquire immediately |
| `boolean tryLock(long time, TimeUnit unit)` | Tries to acquire within timeout |
| `boolean tryLock(Duration timeout)` | Tries to acquire within timeout |
| `void unlock()` | Releases the lock |
| `boolean isHeldByCurrentThread()` | Returns true if current thread holds lock |

---

## RedlockReadWriteLock Methods

| Method | Description |
|--------|-------------|
| `Lock readLock()` | Returns the read lock |
| `Lock writeLock()` | Returns the write lock |

---

## RedlockSemaphore Methods

| Method | Description |
|--------|-------------|
| `void acquire()` | Acquires one permit, blocking |
| `void acquire(int permits)` | Acquires multiple permits |
| `boolean tryAcquire(Duration timeout)` | Tries to acquire one permit |
| `boolean tryAcquire(int permits, Duration timeout)` | Tries to acquire multiple permits |
| `void release()` | Releases one permit |
| `void release(int permits)` | Releases multiple permits |
| `int availablePermits()` | Returns estimated available permits |

---

## RedlockCountDownLatch Methods

| Method | Description |
|--------|-------------|
| `void countDown()` | Decrements the count by one |
| `void await()` | Waits until count reaches zero |
| `boolean await(Duration timeout)` | Waits with timeout |
| `long getCount()` | Returns current count |

---

## Creation Methods

All locks are created via `RedlockManager`:

```java
// Standard lock
Lock lock = manager.createLock("resource");

// Fair lock
Lock fairLock = manager.createFairLock("resource");

// Multi-lock
Lock multiLock = manager.createMultiLock(Arrays.asList("res1", "res2"));

// Read-write lock
RedlockReadWriteLock rwLock = manager.createReadWriteLock("resource");

// Semaphore
RedlockSemaphore sem = manager.createSemaphore("resource", 5);

// Countdown latch
RedlockCountDownLatch latch = manager.createCountDownLatch("latch", 3);
```

## Next Steps

- [Async & Reactive](async-reactive.md) - Async and reactive APIs
- [Configuration](configuration.md) - Configuration options
