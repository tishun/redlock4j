# CountDownLatch Implementation Comparison: redlock4j vs Redisson

This document provides a detailed technical comparison of the CountDownLatch implementations in redlock4j and Redisson.

## Overview

Both libraries implement distributed countdown latches for coordinating multiple threads/processes, but they use different approaches for counting and notification.

## Purpose & Use Case

### redlock4j RedlockCountDownLatch

**Purpose**: Distributed countdown latch with quorum-based consistency

**Use Case**: Coordinating distributed processes with strong consistency requirements

```java
// Create latch waiting for 3 operations
RedlockCountDownLatch latch = manager.createCountDownLatch("startup", 3);

// Worker threads
new Thread(() -> {
    initializeService1();
    latch.countDown();
}).start();

new Thread(() -> {
    initializeService2();
    latch.countDown();
}).start();

new Thread(() -> {
    initializeService3();
    latch.countDown();
}).start();

// Main thread waits
latch.await(); // Blocks until count reaches 0
System.out.println("All services initialized!");
```

### Redisson RedissonCountDownLatch

**Purpose**: Distributed countdown latch with pub/sub notifications

**Use Case**: High-performance coordination with single Redis instance

```java
RCountDownLatch latch = redisson.getCountDownLatch("startup");
latch.trySetCount(3);

// Worker threads
new Thread(() -> {
    initializeService1();
    latch.countDown();
}).start();

// ... more workers

// Main thread waits
latch.await();
System.out.println("All services initialized!");
```

## Architecture & Data Model

### redlock4j

**Design**: Counter with quorum-based reads/writes and pub/sub

**Data Structure**:
```
{latchKey} = {count}                    (counter on each node)
{latchKey}:channel = (pub/sub channel)  (notification channel)
```

**Key Characteristics**:
- Counter replicated across all nodes
- Quorum-based count reads
- Quorum-based decrements
- Atomic per-node decrement + publish (single Lua script)
- Local latch for waiting
- Automatic expiration (10x lock timeout)

**Architecture**:
```
RedlockCountDownLatch
  ├─ latchKey (counter key)
  ├─ channelKey (pub/sub)
  ├─ LockExecutionStrategy (node fan-out + quorum)
  ├─ CountDownLatch localLatch (for waiting)
  ├─ AtomicBoolean subscribed
  └─ Atomic DECR + PUBLISH (decrAndPublishIfZero)
```

### Redisson

**Design**: Single counter with pub/sub and Lua scripts

**Data Structure**:
```
{latchKey} = {count}                                    (single counter)
redisson_countdownlatch__channel__{latchKey} = (pub/sub channel)
```

**Key Characteristics**:
- Single counter (not replicated)
- Atomic Lua scripts
- Pub/sub for notifications
- Async/reactive support
- No automatic expiration

**Architecture**:
```
RedissonCountDownLatch
  ├─ latchKey (counter key)
  ├─ channelName (pub/sub)
  ├─ CountDownLatchPubSub (notification handler)
  ├─ Lua scripts for atomicity
  └─ Async futures
```

## Initialization

### redlock4j

**Initialization**: Automatic when created via the manager factory. Users call
`manager.createCountDownLatch(latchKey, count)`; the manager wires the internal
drivers and execution strategy and seeds the counter on all nodes with a long
expiration (10x lock timeout).

```java
// Simplified/conceptual pseudocode of the internal setup
this.latchKey = latchKey;
this.channelKey = latchKey + ":channel";
this.initialCount = count;
this.localLatch = new CountDownLatch(1);

// Seed the counter on the nodes via the execution strategy,
// with a long expiration (10x lock timeout)
executionStrategy.executeOnNodes(driver -> {
    driver.setex(latchKey, String.valueOf(count),
                 config.getDefaultLockTimeout().toMillis() * 10);
    return true;
});
```

## Count Down Operation

### redlock4j

**Algorithm**: Atomic per-node decrement-and-publish, fanned out via the execution strategy

```java
public void countDown() {
    // Fan out to the appropriate nodes via the execution strategy
    int successCount = executionStrategy.executeOnNodes(driver -> {
        try {
            // Atomic: decrement and publish "zero" if the count hits zero,
            // in a single Lua script (decrAndPublishIfZero)
            long count = driver.decrAndPublishIfZero(latchKey, channelKey, "zero");
            return true;
        } catch (Exception e) {
            logger.debug("Failed to decrement latch count on {}", driver.getIdentifier());
            return false;
        }
    });

    if (executionStrategy.isSuccessful(successCount)) {
        logger.debug("Successfully decremented latch {} on {} nodes", latchKey, successCount);
    } else {
        logger.warn("Failed to decrement latch {} on sufficient nodes", latchKey);
    }
}
```

Where `decrAndPublishIfZero` runs this Lua script atomically on each node:

```lua
local count = redis.call("DECR", KEYS[1])
if count <= 0 then
    redis.call("PUBLISH", KEYS[2], ARGV[1])
end
return count
```

**Characteristics**:
- Atomic DECR + PUBLISH per node (single Lua script)
- Fan-out and quorum check delegated to the execution strategy
- Decrement and zero-notification cannot interleave on a node

**Redis Operations** (M nodes):
- M × `decrAndPublishIfZero` Lua script (DECR, plus PUBLISH when zero)

### Redisson

**Algorithm**: Atomic Lua script with notification

```lua
-- countDownAsync
local v = redis.call('decr', KEYS[1]);
if v <= 0 then
    redis.call('del', KEYS[1])
end;
if v == 0 then
    redis.call(ARGV[2], KEYS[2], ARGV[1])
end;
```

**Characteristics**:
- Atomic decrement + delete + publish
- Single operation
- Deletes key when zero
- Publishes ZERO_COUNT_MESSAGE

**Redis Operations**:
- 1 Lua script execution

## Await Operation

### redlock4j

**Algorithm**: Subscribe + poll with local latch

```java
public boolean await(Duration timeout) throws InterruptedException {
    // Subscribe to notifications
    subscribeToNotifications();

    // Check if already zero
    long currentCount = getCount();
    if (currentCount <= 0) {
        return true;
    }

    // Wait on local latch (released by pub/sub notification)
    return localLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
}

private void subscribeToNotifications() {
    if (subscribed.compareAndSet(false, true)) {
        new Thread(() -> {
            // Subscribe on a single node
            subscriptionDriver.subscribe(new MessageHandler() {
                @Override
                public void onMessage(String channel, String message) {
                    if ("zero".equals(message)) {
                        localLatch.countDown(); // Release waiters
                    }
                }
            }, channelKey);
        }).start();
    }
}

public long getCount() {
    List<Long> results = new ArrayList<>();

    // Read the count from the appropriate nodes via the execution strategy
    executionStrategy.executeOnNodes(driver -> {
        String countStr = driver.get(latchKey);
        if (countStr != null) {
            synchronized (results) {
                results.add(Long.parseLong(countStr));
            }
        }
        return true;
    });

    if (!results.isEmpty()) {
        long total = results.stream().mapToLong(Long::longValue).sum();
        return Math.max(0, total / results.size()); // Average across nodes
    }

    return 0; // Conservative fallback
}
```

**Characteristics**:
- Subscribe to single driver (first one)
- Check count via quorum read
- Wait on local CountDownLatch
- Pub/sub releases local latch
- Average count across nodes

**Redis Operations** (M nodes):
- 1 × `SUBSCRIBE`
- M × `GET` (check count)

### Redisson

**Algorithm**: Subscribe + async polling

```java
public void await() throws InterruptedException {
    if (getCount() == 0) {
        return;
    }

    CompletableFuture<RedissonCountDownLatchEntry> future = subscribe();
    RedissonCountDownLatchEntry entry = future.join();

    try {
        while (getCount() > 0) {
            entry.getLatch().await(); // Wait for notification
        }
    } finally {
        unsubscribe(entry);
    }
}

private CompletableFuture<RedissonCountDownLatchEntry> subscribe() {
    return pubSub.subscribe(getEntryName(), getChannelName());
}

public long getCount() {
    return commandExecutor.writeAsync(
        getRawName(), LongCodec.INSTANCE,
        RedisCommands.GET_LONG, getRawName()
    ).join();
}
```

**Characteristics**:
- Subscribe via pub/sub service
- Poll count in loop
- Semaphore-based waiting
- Async futures
- Unsubscribe when done

**Redis Operations**:
- 1 × `SUBSCRIBE`
- N × `GET` (polling)

## Reset Operation

### redlock4j

**Reset**: Supported (non-standard)

```java
public void reset() {
    // Delete the existing latch on the appropriate nodes
    executionStrategy.executeOnNodes(driver -> {
        driver.del(latchKey);
        return true;
    });

    // Reset local state
    localLatch = new CountDownLatch(1);
    subscribed.set(false);

    // Reinitialize with the original count
    initializeLatch(initialCount);
}
```

**Characteristics**:
- Deletes on all nodes
- Resets local latch
- Reinitializes with original count
- Not atomic
- Can cause race conditions

### Redisson

**Reset**: Supported via `trySetCount()`

```java
// Delete old latch
latch.delete();

// Create new one
latch.trySetCount(initialCount);
```

**Characteristics**:
- Must explicitly delete first
- Then set new count
- Two separate operations
- Not atomic
- Publishes notifications

## Performance Comparison

### redlock4j

**Count Down** (M nodes):
- M × `decrAndPublishIfZero` Lua script (atomic DECR, plus PUBLISH when zero)
- Total: M script executions

**Await**:
- 1 × `SUBSCRIBE`
- M × `GET` (quorum read)
- Total: M+1 operations

**Complexity**: O(M) per operation

**Latency**:
- Higher due to quorum
- Multiple round trips
- Pub/sub to all nodes

### Redisson

**Count Down**:
- 1 Lua script execution
- Total: 1 operation

**Await**:
- 1 × `SUBSCRIBE`
- N × `GET` (polling)
- Total: N+1 operations

**Complexity**: O(1) for countDown, O(N) for await

**Latency**:
- Lower for single instance
- Single round trip for countDown
- Polling overhead for await

### Measured Performance

Benchmark: 5 clients, count=5, 50 ms work per cycle, 3-node Redis 7 cluster, 60 s measurement (full methodology in [Architecture › Performance Analysis](../guide/architecture.md#performance-analysis)).

| Implementation         | Ops/s     | p50 (ms)  | p99 (ms)  | mean (ms) | success |
|------------------------|----------:|----------:|----------:|----------:|--------:|
| Redisson               |     59.02 |     17.17 |     22.57 |     16.91 |   100 % |
| redlock4j-singlenode   |     58.19 |     16.17 |     19.87 |     15.67 |   100 % |
| **redlock4j-3node**    | **59.91** | **15.29** | **19.89** | **15.18** |   100 % |

**Reading the numbers**: `redlock4j-3node` matches Redisson on throughput while delivering slightly lower p50/p99/mean latency. Quorum overhead is fully amortized by the parallel multi-node I/O — coordination primitives benefit from multi-node distribution rather than paying for it.

## Safety & Correctness

### redlock4j

**Safety Guarantees**:
- Quorum-based consistency
- Survives minority node failures
- Count averaged across nodes
- Automatic expiration
- No single point of failure

**Potential Issues**:
- Higher latency
- More network overhead
- Subscribe to single node only
- Count averaging may be inaccurate
- Reset not atomic

**Consistency Model**:
```
Count decremented if:
  - Quorum of nodes decremented
  - Average count used for reads

Notification sent if:
  - The decrement drives a node's count to zero
  - Decrement + publish are atomic on that node (single Lua script)
```


### Redisson

**Safety Guarantees**:
- Atomic operations (Lua scripts)
- Accurate count
- Pub/sub notifications
- Async/reactive support
- Low latency

**Potential Issues**:
- Single point of failure
- No quorum mechanism
- No automatic expiration
- Polling in await loop
- Reset not atomic

**Consistency Model**:
```
Count decremented if:
  - Atomic Lua script succeeds
  - Single instance

Notification sent if:
  - Count reaches exactly zero
  - Atomic with decrement
```

## Feature Comparison Table

| Feature              | redlock4j                                   | Redisson                           |
|----------------------|---------------------------------------------|------------------------------------|
| **Data Model**       | Counter on all nodes                        | Single counter                     |
| **Quorum**           | Yes                                         | No                                 |
| **Fault Tolerance**  | Survives minority failures                  | Single point of failure            |
| **Initialization**   | Automatic via manager factory               | Explicit via trySetCount()         |
| **Expiration**       | Automatic (10x timeout)                     | No automatic expiration            |
| **Count Accuracy**   | Average across nodes                        | Exact                              |
| **Atomicity**        | Atomic per-node DECR + PUBLISH (Lua script) | Atomic (Lua script)                |
| **Subscription**     | Single node                                 | Managed pub/sub service            |
| **Reset**            | Supported (non-standard)                    | Supported via delete + trySetCount |
| **Async Support**    | No                                          | Yes                                |
| **Reactive Support** | No                                          | Yes                                |
| **Performance**      | O(M)                                        | O(1) for countDown                 |
| **Latency**          | Higher                                      | Lower                              |
| **Network Overhead** | High                                        | Low                                |

## Use Case Comparison

### redlock4j RedlockCountDownLatch

**Best For**:
- Distributed systems requiring quorum-based safety
- Coordination with strong consistency
- Multi-master Redis setups
- Fault-tolerant coordination
- Automatic expiration needed

**Example Scenarios**:
```java
// Distributed service startup coordination
RedlockCountDownLatch startupLatch = manager.createCountDownLatch("app:startup", 5);

// Batch job coordination
RedlockCountDownLatch batchLatch = manager.createCountDownLatch("batch:job:123", 100);

// Multi-stage workflow
RedlockCountDownLatch stageLatch = manager.createCountDownLatch("workflow:stage1", 10);
```

### Redisson RedissonCountDownLatch

**Best For**:
- Single Redis instance deployments
- High-throughput coordination
- Applications needing async/reactive APIs
- Scenarios requiring exact count
- Low-latency requirements

**Example Scenarios**:
```java
// High-performance service coordination
RCountDownLatch startupLatch = redisson.getCountDownLatch("app:startup");
startupLatch.trySetCount(5);

// Async coordination
RCountDownLatch asyncLatch = redisson.getCountDownLatch("async:task");
asyncLatch.trySetCount(10);
RFuture<Void> future = asyncLatch.awaitAsync();

// Reusable latch
RCountDownLatch reusableLatch = redisson.getCountDownLatch("reusable");
reusableLatch.trySetCount(3);
// ... use it
reusableLatch.delete();
reusableLatch.trySetCount(5); // Reuse
```

## Recommendations

### Choose redlock4j RedlockCountDownLatch when:

- Need quorum-based distributed consistency
- Require fault tolerance (multi-master)
- Automatic expiration is important
- Can tolerate higher latency
- Count averaging is acceptable

### Choose Redisson RedissonCountDownLatch when:

- Single Redis instance is acceptable
- Need high throughput / low latency
- Require exact count tracking
- Need async/reactive APIs
- Want atomic operations
- Explicit initialization preferred

## Migration Considerations

### From Redisson to redlock4j

```java
// Before (Redisson)
RCountDownLatch latch = redisson.getCountDownLatch("startup");
latch.trySetCount(3);
latch.await();

// After (redlock4j)
RedlockCountDownLatch latch = manager.createCountDownLatch("startup", 3);
latch.await();
```

**Benefits**:
- Quorum-based safety
- Fault tolerance
- Automatic expiration

**Considerations**:
- Higher latency
- Count is averaged
- No async support

### From redlock4j to Redisson

```java
// Before (redlock4j)
RedlockCountDownLatch latch = manager.createCountDownLatch("startup", 3);

// After (Redisson)
RCountDownLatch latch = redisson.getCountDownLatch("startup");
latch.trySetCount(3);
```

**Benefits**:
- Lower latency
- Exact count
- Async/reactive support
- Atomic operations

**Considerations**:
- Single point of failure
- Must initialize explicitly
- No automatic expiration

## Conclusion

Both implementations provide distributed countdown latches with different trade-offs:

**redlock4j RedlockCountDownLatch**:
- Quorum-based with fault tolerance
- Automatic initialization and expiration
- Higher latency but survives failures
- Count averaged across nodes
- Best for multi-master setups requiring strong consistency

**Redisson RedissonCountDownLatch**:
- Atomic Lua scripts with exact counting
- Lower latency but single point of failure
- Pub/sub notifications
- Async/reactive support
- Best for high-throughput single-instance deployments

Choose based on your specific requirements:
- **Distributed consistency & fault tolerance** → redlock4j RedlockCountDownLatch
- **High throughput & low latency** → Redisson RedissonCountDownLatch
- **Exact count tracking** → Redisson RedissonCountDownLatch
- **Automatic expiration** → redlock4j RedlockCountDownLatch


