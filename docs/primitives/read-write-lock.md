# ReadWriteLock - Distributed Reader/Writer Lock

ReadWriteLock allows multiple concurrent readers OR a single exclusive writer.

## Overview

| Property      | Value                                      |
|---------------|--------------------------------------------|
| **Type**      | Shared/Exclusive Lock                      |
| **Readers**   | Multiple concurrent                        |
| **Writers**   | Single exclusive                           |
| **Interface** | `java.util.concurrent.locks.ReadWriteLock` |

## How It Works

```mermaid
sequenceDiagram
    participant R1 as Reader 1
    participant R2 as Reader 2
    participant W as Writer
    participant Redis as Redis
    
    Note over Redis: State: unlocked<br/>readers: 0

    R1->>Redis: INCR readers_count
    Redis-->>R1: 1
    Note over Redis: readers: 1
    Note over R1: Reading...
    
    R2->>Redis: Check write_lock exists?
    Redis-->>R2: NO
    R2->>Redis: INCR readers_count
    Redis-->>R2: 2
    Note over Redis: readers: 2
    Note over R2: Reading...
    
    W->>Redis: Check readers_count
    Redis-->>W: 2 (active readers)
    Note over W: Cannot write, waiting...
    
    R1->>Redis: DECR readers_count
    Note over Redis: readers: 1
    R2->>Redis: DECR readers_count
    Note over Redis: readers: 0
    
    W->>Redis: Check readers_count
    Redis-->>W: 0
    W->>Redis: SET write_lock NX PX
    Redis-->>W: OK
    Note over Redis: write_lock: held
    Note over W: Writing (exclusive)...
    
    R1->>Redis: Check write_lock?
    Redis-->>R1: EXISTS (blocked)
    Note over R1: Cannot read, waiting...
    
    W->>Redis: DEL write_lock
    Note over Redis: write_lock: none
    
    R1->>Redis: INCR readers_count
    Note over R1: Reading resumed
```

## Key Concepts

### Reader Count Tracking
Readers increment/decrement a distributed counter:
```
INCR resource:readers    # Acquire read lock
DECR resource:readers    # Release read lock
```

### Write Lock Exclusion
Writers must wait for `readers == 0` AND acquire exclusive lock:
```lua
local readers = redis.call("GET", "resource:readers")
if (readers == nil or tonumber(readers) == 0) then
    return redis.call("SET", "resource:write", value, "NX", "PX", timeout)
end
return nil
```

### Preventing Writer Starvation
When a writer is waiting, new readers may be blocked to prevent indefinite starvation.

!!! note "Mode Differences"
    The diagram above shows the conceptual flow. In **multi-node mode**:

    - Reader count is maintained on all nodes independently
    - Write lock requires quorum (N/2+1 nodes)
    - Reader count check uses aggregated value across nodes
    - Clock drift is applied to lock validity time

## Usage

```java
RedlockReadWriteLock rwLock = redlockManager.createReadWriteLock("resource");

// Multiple readers can read simultaneously
rwLock.readLock().lock();
try {
    readData();
} finally {
    rwLock.readLock().unlock();
}

// Writer has exclusive access
rwLock.writeLock().lock();
try {
    writeData();
} finally {
    rwLock.writeLock().unlock();
}
```

## Concurrency Matrix

| Holder    | Read Request  | Write Request  |
|-----------|---------------|----------------|
| None      | ✓ Granted     | ✓ Granted      |
| Reader(s) | ✓ Granted     | ✗ Blocked      |
| Writer    | ✗ Blocked     | ✗ Blocked      |

## Supported Modes

| Mode                    | Supported  | Notes                              |
|-------------------------|------------|------------------------------------|
| **Single Node**         | Yes        | Reader count on single instance    |
| **Multi-Node (Quorum)** | Yes        | Reader count averaged across nodes |

## Configuration

| Parameter         | Default  | Description        |
|-------------------|----------|--------------------|
| `lockTimeoutMs`   | 30000    | Lock TTL           |
| `readerTimeoutMs` | 30000    | Reader counter TTL |

## When to Use

**Good for:**
- Caching: multiple readers, occasional updates
- Configuration: frequent reads, rare writes
- Reports: concurrent read access

**Consider alternatives for:**
- Equal read/write frequency → [Redlock](redlock.md)
- FIFO ordering needed → [FairLock](fair-lock.md)

## See Also

- [Redlock](redlock.md) - Standard exclusive locking
- [Semaphore](semaphore.md) - Permit-based concurrency
