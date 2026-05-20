# Redis Array Improvements for Distributed Primitives

Based on [Redis PR #15162](https://github.com/redis/redis/pull/15162) - the new Redis Array type by antirez.

## Overview

Redis Arrays provide native indexed data structures optimized for slot-based access, ring buffers, and sparse data. This document analyzes how they can improve redlock4j's distributed primitives.

## Analysis Summary

| Primitive | Current Approach | Array Benefit | Priority |
|-----------|-----------------|---------------|----------|
| **Semaphore** | N separate permit keys | Single array with slots, ARCOUNT for availability | **HIGH** |
| **ReadWriteLock** | INCR/DECR counter + per-reader keys | Array tracks readers, ARCOUNT for count | **MEDIUM** |
| **MultiLock** | Individual SET NX per key | None (different keys, not indexed) | NONE |
| **CountDownLatch** | Counter with DECR | None (single counter) | NONE |
| **FairLock** | Sorted set queue | Arrays don't provide ordering by score | NONE |

## Detailed Analysis

### 1. RedlockSemaphore - HIGH PRIORITY

**Current Implementation Issues:**
- Creates **N separate keys** for N permits (`semaphore:permit:<uuid>`)
- Each permit acquisition requires individual SET NX + lock management
- `availablePermits()` returns placeholder (not implemented)
- Cleanup requires scanning/deleting individual permit keys
- No visibility into which slots are occupied

**Redis Array Solution:**
```redis
ARSET semaphore:api-limiter <slot> <permit_id>   # Acquire slot atomically
ARGET semaphore:api-limiter <slot>               # Check if slot taken
ARCOUNT semaphore:api-limiter                    # Count active permits (O(1))
ARSCAN semaphore:api-limiter 0 <maxPermits>      # List active permits
ARDEL semaphore:api-limiter <slot>               # Release permit
```

**Benefits:**
- Single key instead of N keys (better memory, atomicity)
- O(1) `ARCOUNT` for `availablePermits()` 
- Atomic slot-based acquisition
- Built-in TTL per array (vs per-key TTL management)
- Server-side sparse storage for partially-used semaphores

### 2. RedlockReadWriteLock - MEDIUM PRIORITY

**Current Implementation:**
- Uses `INCR/DECR` counter for reader count (`resource:readers`)
- Separate key per reader token (`resource:readers:<lockValue>`)
- No tracking of *which* readers hold the lock
- Counter can drift if readers crash without cleanup

**Redis Array Solution:**
```redis
ARSET resource:readers <reader_slot> <reader_id>  # Register reader
ARCOUNT resource:readers                           # Get reader count (O(1))
ARSCAN resource:readers - +                        # List all active readers
ARDEL resource:readers <reader_slot>               # Unregister reader
```

**Benefits:**
- Track individual reader identities (debugging, fairness analysis)
- Atomic reader count with `ARCOUNT` instead of INCR counter
- Better cleanup - can identify and remove stale readers
- Single key vs counter + N reader keys

### 3. MultiLock - NO IMPROVEMENT

Arrays don't help because:
- Locks are on *different resource keys*, not indexed positions
- Order matters for deadlock prevention (sorted keys)
- Each lock needs its own TTL and value

### 4. CountDownLatch - NO IMPROVEMENT

Single atomic counter is the correct approach. Arrays add no value.

### 5. FairLock - NO IMPROVEMENT

Sorted sets provide score-based ordering (timestamps) that Arrays cannot replicate.

---

## Implementation Plan

### Phase 1: Infrastructure (Required First)

#### Task 1.1: Add Redis Array Commands to RedisDriver
Add new methods to `RedisDriver` interface for Array operations.

#### Task 1.2: Implement in Jedis and Lettuce Drivers
Implement the Array commands in both driver implementations.

#### Task 1.3: Feature Detection
Add capability detection for Redis 8.x+ Array support with fallback.

### Phase 2: RedlockSemaphore Improvement

#### Task 2.1: Create ArrayBasedSemaphore Strategy
New strategy class using Arrays for permit management.

#### Task 2.2: Slot Allocation Algorithm
Implement efficient slot finding (first available slot).

#### Task 2.3: Implement availablePermits() Properly
Use ARCOUNT for accurate permit counting.

#### Task 2.4: Backward Compatibility
Fallback to current implementation for older Redis versions.

### Phase 3: RedlockReadWriteLock Improvement

#### Task 3.1: Create ArrayBasedReaderTracking
Replace INCR/DECR with Array-based reader tracking.

#### Task 3.2: Reader Identity Tracking
Store reader IDs in array slots for visibility.

#### Task 3.3: Stale Reader Cleanup
Use ARSCAN to identify and clean expired readers.

---

## New RedisDriver Methods Required

```java
// Array operations for Redis 8.x+
boolean arSet(String key, long index, String value, long expireTimeMs);
String arGet(String key, long index);
long arCount(String key);
boolean arDel(String key, long index);
List<ArrayEntry> arScan(String key, long start, long end);
long arLen(String key);
```

---

## Detailed Implementation Plan

### Phase 1: Infrastructure

#### 1.1 RedisDriver Interface Changes

**File:** `src/main/java/org/codarama/redlock4j/driver/RedisDriver.java`

```java
// ========== Array Operations (Redis 8.x+ for Semaphore/RWLock) ==========

/**
 * Sets a value at the specified index in a Redis Array.
 * Creates the array if it doesn't exist.
 */
boolean arSet(String key, long index, String value, long expireTimeMs) throws RedisDriverException;

/**
 * Gets the value at the specified index in a Redis Array.
 * @return the value, or null if index is empty
 */
String arGet(String key, long index) throws RedisDriverException;

/**
 * Returns the count of populated elements in the array.
 */
long arCount(String key) throws RedisDriverException;

/**
 * Deletes the value at the specified index.
 * @return true if a value was deleted
 */
boolean arDel(String key, long index) throws RedisDriverException;

/**
 * Scans populated elements in the given range.
 * @return list of index-value pairs for populated slots
 */
List<ArrayEntry> arScan(String key, long start, long end) throws RedisDriverException;

/**
 * Atomically finds and sets the first empty slot in range [0, maxIndex).
 * @return the slot index that was set, or -1 if all slots occupied
 */
long arSetFirstEmpty(String key, long maxIndex, String value, long expireTimeMs) throws RedisDriverException;

/**
 * Checks if Redis server supports Array commands.
 */
boolean supportsArrays();
```

#### 1.2 ArrayEntry Record

**File:** `src/main/java/org/codarama/redlock4j/driver/ArrayEntry.java`

```java
public record ArrayEntry(long index, String value) {}
```

#### 1.3 Feature Detection

Add to driver initialization:
```java
private boolean checkArraySupport() {
    try {
        // Try ARLEN on non-existent key - returns 0 on 8.x+, error on older
        execute("ARLEN", "redlock4j:feature:test");
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

---

### Phase 2: RedlockSemaphore with Arrays

#### 2.1 New ArraySemaphoreStrategy

**File:** `src/main/java/org/codarama/redlock4j/strategy/ArraySemaphoreStrategy.java`

**Algorithm:**
```
acquire(permits):
  for i in 0..permits:
    slot = findAndOccupySlot(maxPermits)
    if slot == -1:
      rollback(acquired_slots)
      return FAILED
    acquired_slots.add(slot)
  return SUCCESS(acquired_slots)

findAndOccupySlot(maxPermits):
  // Use Lua script for atomicity:
  // 1. ARSCAN to find empty slot in [0, maxPermits)
  // 2. ARSET to occupy it
  // 3. Return slot index or -1
  return driver.arSetFirstEmpty(key, maxPermits, permitId, ttl)

release(slots):
  for slot in slots:
    driver.arDel(key, slot)
```

#### 2.2 Updated RedlockSemaphore

Key changes:
- Store `List<Long> acquiredSlots` instead of `List<String> permitIds`
- Use `arCount()` for `availablePermits()`: `return maxPermits - arCount(key)`
- Single key `semaphore:<name>` instead of `semaphore:<name>:permit:<uuid>`

#### 2.3 Lua Script for Atomic Slot Acquisition

```lua
-- KEYS[1] = semaphore key
-- ARGV[1] = max permits, ARGV[2] = permit value, ARGV[3] = ttl ms
local max = tonumber(ARGV[1])
for i = 0, max - 1 do
    local val = redis.call('ARGET', KEYS[1], i)
    if val == nil or val == false then
        redis.call('ARSET', KEYS[1], i, ARGV[2])
        redis.call('PEXPIRE', KEYS[1], ARGV[3])
        return i
    end
end
return -1
```

---

### Phase 3: RedlockReadWriteLock with Arrays

#### 3.1 ArrayBasedReaderTracking

**Current:**
```
readers -> counter (INCR/DECR)
readers:<lockValue> -> "1" (per reader)
```

**New:**
```
resource:readers -> Array [reader0_id, reader1_id, ...]
```

#### 3.2 Reader Slot Assignment

Each reader gets a slot (0 to reasonable max, e.g., 1000).
- `incrementReaderCount()` → `arSetFirstEmpty(readersKey, MAX_READERS, readerId, ttl)`
- `decrementReaderCount()` → `arDel(readersKey, slot)`
- `hasActiveReaders()` → `arCount(readersKey) > 0`

#### 3.3 LockState Changes

```java
private static class LockState {
    final String lockValue;
    final long readerSlot;  // NEW: slot in array
    final Instant acquisitionTime;
    final Duration validityDuration;
    int holdCount;
}
```

---

### Phase 4: Testing & Migration

#### 4.1 Integration Tests
- Test with Redis 8.x+ (Array support)
- Test fallback with Redis 7.x (no Array support)
- Concurrent acquisition stress tests

#### 4.2 Benchmark Comparison
- Compare throughput: current vs Array-based
- Compare memory: N keys vs single Array
- Compare `availablePermits()` accuracy

#### 4.3 Migration Strategy
- Feature flag: `redlock4j.useArrayOptimization=true`
- Auto-detect Redis version and select strategy
- Document minimum Redis version requirement

---

## Performance Improvement Prognosis

### RedlockSemaphore Performance Analysis

#### Current Implementation Overhead

For a semaphore with `maxPermits = 100` and acquiring `N` permits:

| Operation | Current Approach | Commands per Node |
|-----------|------------------|-------------------|
| Acquire 1 permit | SET NX for permit key | 1 × nodes |
| Acquire N permits | N × SET NX | N × nodes |
| Release N permits | N × DEL (conditional) | N × nodes |
| `availablePermits()` | **Not implemented** (returns placeholder) | N/A |
| Cleanup expired | SCAN + DEL pattern | O(keys) × nodes |

**Example: 3-node cluster, acquire 5 permits:**
- Current: 5 × 3 = **15 network round trips** (sequential per permit)
- Keys created: 5 separate keys (`semaphore:name:permit:<uuid1..5>`)

#### Array-Based Improvement

| Operation | Array Approach | Commands per Node |
|-----------|----------------|-------------------|
| Acquire 1 permit | ARSETFIRSTEMPTY (Lua) | 1 × nodes |
| Acquire N permits | N × ARSETFIRSTEMPTY | N × nodes |
| Release N permits | N × ARDEL | N × nodes |
| `availablePermits()` | ARCOUNT | 1 × nodes |
| Cleanup expired | Automatic (TTL on array) | 0 |

#### Projected Improvements

| Metric | Current | With Arrays | Improvement |
|--------|---------|-------------|-------------|
| **Keys per semaphore** | N (one per permit) | 1 | **N× reduction** |
| **Memory overhead** | ~100 bytes × N permits | ~50 bytes + 8×N | **~40-60% less** |
| **`availablePermits()` latency** | N/A (unimplemented) | O(1) single command | **Now functional** |
| **Permit acquisition** | Same | Same | Neutral |
| **Cleanup complexity** | O(N) SCAN+DEL | O(1) key expiry | **Simpler** |

#### Estimated Throughput Gains

Based on reduced key count and server-side operations:

| Scenario | Expected Improvement |
|----------|---------------------|
| High-permit semaphores (50-100 permits) | **15-25% throughput** |
| Frequent `availablePermits()` calls | **50-80% latency reduction** |
| Memory per semaphore instance | **40-60% reduction** |
| GC pressure (fewer objects) | **10-20% improvement** |

### RedlockReadWriteLock Performance Analysis

#### Current Implementation Overhead

| Operation | Current Approach | Commands per Node |
|-----------|------------------|-------------------|
| Acquire read lock | INCR + SETEX (reader key) + GET (write lock) | 3 × nodes |
| Release read lock | DECR + DEL (reader key) | 2 × nodes |
| Check readers | GET counter | 1 × nodes |
| List active readers | **Not possible** | N/A |

**Keys created per reader:** 2 (`readers` counter + `readers:<lockValue>`)

#### Array-Based Improvement

| Operation | Array Approach | Commands per Node |
|-----------|----------------|-------------------|
| Acquire read lock | ARSETFIRSTEMPTY + GET (write lock) | 2 × nodes |
| Release read lock | ARDEL | 1 × nodes |
| Check readers | ARCOUNT | 1 × nodes |
| List active readers | ARSCAN | 1 × nodes |

#### Projected Improvements

| Metric | Current | With Arrays | Improvement |
|--------|---------|-------------|-------------|
| **Keys per RWLock** | 1 + N readers | 2 (write + readers array) | **N× reduction** |
| **Acquire read lock ops** | 3 | 2 | **33% fewer ops** |
| **Release read lock ops** | 2 | 1 | **50% fewer ops** |
| **Reader visibility** | None | Full (ARSCAN) | **New capability** |
| **Counter drift risk** | Possible on crashes | Eliminated | **Better consistency** |

#### Estimated Throughput Gains

| Scenario | Expected Improvement |
|----------|---------------------|
| Read-heavy workloads (10:1 read:write) | **20-30% throughput** |
| High reader concurrency (50+ readers) | **25-35% throughput** |
| Memory per RWLock instance | **30-50% reduction** |
| Reader cleanup/debugging | **100% improvement** (now possible) |

### Latency Comparison (Estimated)

Assuming 0.5ms average Redis RTT:

| Operation | Current Latency | Array Latency | Savings |
|-----------|-----------------|---------------|---------|
| Semaphore acquire (5 permits, 3 nodes) | ~7.5ms | ~7.5ms | 0% |
| Semaphore `availablePermits()` | N/A | ~1.5ms | New |
| ReadLock acquire (3 nodes) | ~4.5ms | ~3.0ms | **33%** |
| ReadLock release (3 nodes) | ~3.0ms | ~1.5ms | **50%** |

### Memory Footprint Comparison

For a semaphore with 100 max permits, 50 active:

| Component | Current | With Arrays |
|-----------|---------|-------------|
| Key overhead | 50 keys × ~80 bytes = 4KB | 1 key × ~80 bytes = 80 bytes |
| Value storage | 50 × ~50 bytes = 2.5KB | Array: ~500 bytes (sparse) |
| **Total** | **~6.5KB** | **~600 bytes** |
| **Reduction** | | **~90%** |

### Caveats & Risks

1. **Redis Version Dependency**: Requires Redis 8.x+ (PR #15162 merged)
2. **Client Library Support**: Jedis/Lettuce must add Array commands
3. **Lua Script Overhead**: `arSetFirstEmpty` uses Lua for atomicity
4. **Learning Curve**: New data type semantics
5. **Fallback Complexity**: Must maintain two code paths

### When NOT to Use Arrays

- Single-permit semaphores (simple lock is sufficient)
- Redis < 8.x environments
- Extremely low-latency requirements (Lua script adds ~0.1ms)

---

## Timeline Estimate

| Phase | Tasks | Effort |
|-------|-------|--------|
| Phase 1 | Driver interface + implementations | 2-3 days |
| Phase 2 | Semaphore improvement | 2-3 days |
| Phase 3 | ReadWriteLock improvement | 1-2 days |
| Phase 4 | Testing & documentation | 2 days |
| **Total** | | **7-10 days** |

## Dependencies

- Redis 8.x+ with Array support (PR #15162 merged)
- Jedis/Lettuce client updates for Array commands
- Feature detection mechanism
