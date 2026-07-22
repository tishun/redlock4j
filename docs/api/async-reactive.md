# Async & Reactive API Reference

Redlock4j provides modern asynchronous and reactive APIs for non-blocking applications.

## AsyncRedlock (CompletionStage API)

Non-blocking lock operations using Java's `CompletionStage`.

### Creating an AsyncRedlock

```java
AsyncRedlock asyncLock = manager.createAsyncLock("async-resource");
```

### Methods

| Method                       | Return Type                | Description                                             |
|------------------------------|----------------------------|---------------------------------------------------------|
| `tryLockAsync()`             | `CompletionStage<Boolean>` | Tries to acquire the lock                               |
| `tryLockAsync(Duration)`     | `CompletionStage<Boolean>` | Tries with timeout                                      |
| `lockAsync()`                | `CompletionStage<Void>`    | Acquires the lock, waiting if necessary                 |
| `unlockAsync()`              | `CompletionStage<Void>`    | Releases the lock                                       |
| `isHeldByCurrentThread()`    | `boolean`                  | Checks if held by current thread                        |
| `getRemainingValidityTime()` | `Duration`                 | Remaining validity time, or `Duration.ZERO` if not held |
| `getHoldCount()`             | `int`                      | Number of times the lock has been acquired, or 0        |
| `getLockKey()`               | `String`                   | Returns the lock key                                    |
| `extendAsync(Duration)`      | `CompletionStage<Boolean>` | Extends the lock validity time                          |

### Example

```java
AsyncRedlock asyncLock = manager.createAsyncLock("async-resource");

asyncLock.tryLockAsync(Duration.ofSeconds(5))
    .thenAccept(acquired -> {
        if (acquired) {
            System.out.println("Lock acquired!");
            performAsyncWork();
        }
    })
    .thenCompose(v -> asyncLock.unlockAsync())
    .exceptionally(ex -> {
        System.err.println("Error: " + ex.getMessage());
        return null;
    });
```

### Chaining Operations

```java
asyncLock.tryLockAsync()
    .thenCompose(acquired -> {
        if (!acquired) {
            return CompletableFuture.completedFuture(null);
        }
        return performAsyncOperation()
            .thenCompose(result -> asyncLock.unlockAsync()
                .thenApply(v -> result));
    })
    .thenAccept(result -> {
        if (result != null) {
            processResult(result);
        }
    });
```

---

## RxRedlock (RxJava API)

Reactive lock operations using RxJava.

### Creating an RxRedlock

```java
RxRedlock rxLock = manager.createRxLock("rx-resource");
```

### Methods

| Method                                                    | Return Type             | Description                                                               |
|-----------------------------------------------------------|-------------------------|---------------------------------------------------------------------------|
| `tryLockRx()`                                             | `Single<Boolean>`       | Tries to acquire the lock                                                 |
| `tryLockRx(Duration)`                                     | `Single<Boolean>`       | Tries with timeout                                                        |
| `lockRx()`                                                | `Completable`           | Acquires the lock, waiting if necessary                                   |
| `unlockRx()`                                              | `Completable`           | Releases the lock                                                         |
| `tryLockWithRetryRx(int maxRetries, Duration retryDelay)` | `Single<Boolean>`       | Tries to acquire with retry logic                                         |
| `validityObservable(Duration checkInterval)`              | `Observable<Duration>`  | Emits remaining validity time at each check                               |
| `lockStateObservable()`                                   | `Observable<LockState>` | Emits lock state changes (ACQUIRING, ACQUIRED, RELEASED, EXPIRED, FAILED) |
| `isHeldByCurrentThread()`                                 | `boolean`               | Checks if held by current thread                                          |
| `getRemainingValidityTime()`                              | `Duration`              | Remaining validity time, or `Duration.ZERO` if not held                   |
| `getHoldCount()`                                          | `int`                   | Number of times the lock has been acquired, or 0                          |
| `getLockKey()`                                            | `String`                | Returns the lock key                                                      |
| `extendRx(Duration)`                                      | `Single<Boolean>`       | Extends the lock validity time                                            |

### Example

```java
RxRedlock rxLock = manager.createRxLock("rx-resource");

rxLock.tryLockRx(Duration.ofSeconds(5))
    .flatMapCompletable(acquired -> {
        if (acquired) {
            return performReactiveWork()
                .andThen(rxLock.unlockRx());
        }
        return Completable.complete();
    })
    .subscribe(
        () -> System.out.println("Operation complete"),
        error -> System.err.println("Error: " + error)
    );
```

### Validity Monitoring

Monitor lock validity in real-time:

```java
rxLock.validityObservable(Duration.ofSeconds(1))
    .subscribe(remaining -> {
        if (remaining.toMillis() < 5000) {
            System.out.println("Warning: Lock expiring soon!");
        }
    });
```

---

## Combined Async/Rx Lock

For applications using both async and reactive patterns:

```java
AsyncRedlockImpl combinedLock = manager.createAsyncRxLock("combined-resource");

// Use as CompletionStage
combinedLock.tryLockAsync().thenAccept(acquired -> { ... });

// Use as RxJava
combinedLock.tryLockRx().subscribe(acquired -> { ... });
```

---

## Auto-Renewal

Both async and reactive locks support automatic renewal:

```java
// CompletionStage auto-renewal
asyncLock.tryLockAsync()
    .thenCompose(acquired -> {
        if (acquired) {
            // Lock auto-renews while work is in progress
            return longRunningOperation();
        }
        return CompletableFuture.completedFuture(null);
    });

// RxJava auto-renewal
rxLock.tryLockRx()
    .flatMapCompletable(acquired -> {
        if (acquired) {
            return longRunningReactiveOperation();
        }
        return Completable.complete();
    });
```

---

## Error Handling

### CompletionStage

```java
asyncLock.tryLockAsync()
    .thenAccept(acquired -> { ... })
    .exceptionally(ex -> {
        if (ex.getCause() instanceof RedlockException) {
            // Handle lock-specific error
        }
        return null;
    });
```

### RxJava

```java
rxLock.tryLockRx()
    .onErrorReturn(ex -> {
        logger.error("Lock failed", ex);
        return false;
    })
    .subscribe(acquired -> { ... });
```

## Thread Safety

All async and reactive operations are thread-safe. However, the lock ownership is tied to the thread that initiated the lock operation.

## Next Steps

- [Configuration](configuration.md) - Configuration options
- [Lock Types](lock-types.md) - Standard lock types
