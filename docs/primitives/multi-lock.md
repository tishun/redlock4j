# MultiLock - Atomic Multi-Resource Locking

MultiLock enables atomic acquisition of multiple resources, preventing deadlocks through consistent ordering.

## Overview

| Property | Value |
|----------|-------|
| **Type** | Exclusive Lock (multiple keys) |
| **Reentrancy** | Supported (per-thread) |
| **Interface** | `java.util.concurrent.locks.Lock` |
| **Deadlock Prevention** | Sorted key ordering |

## How It Works

### Single Node Mode

```mermaid
sequenceDiagram
    participant Client
    participant Redis as Redis

    Note over Client: Resources: [account:2, account:1, account:3]
    Note over Client: Sort keys: [account:1, account:2, account:3]

    Client->>Redis: SET account:1 val1 NX PX
    Redis-->>Client: OK
    Client->>Redis: SET account:2 val2 NX PX
    Redis-->>Client: OK
    Client->>Redis: SET account:3 val3 NX PX
    Redis-->>Client: OK

    Note over Client: All keys locked!

    rect rgb(200, 230, 200)
        Note over Client: Critical Section
    end

    Client->>Redis: DEL account:1, account:2, account:3
```

### Multi-Node Mode (Quorum)

```mermaid
sequenceDiagram
    participant Client
    participant R1 as Redis Node 1
    participant R2 as Redis Node 2
    participant R3 as Redis Node 3

    Note over Client: Resources: [account:2, account:1, account:3]
    Note over Client: Sort keys: [account:1, account:2, account:3]

    rect rgb(230, 230, 250)
        Note over Client,R3: Acquire ALL keys on Node 1
        Client->>R1: SET account:1 val1 NX PX
        R1-->>Client: OK
        Client->>R1: SET account:2 val2 NX PX
        R1-->>Client: OK
        Client->>R1: SET account:3 val3 NX PX
        R1-->>Client: OK
    end

    rect rgb(230, 230, 250)
        Note over Client,R3: Acquire ALL keys on Node 2
        Client->>R2: SET account:1 val1 NX PX
        R2-->>Client: OK
        Client->>R2: SET account:2 val2 NX PX
        R2-->>Client: OK
        Client->>R2: SET account:3 val3 NX PX
        R2-->>Client: OK
    end

    rect rgb(230, 230, 250)
        Note over Client,R3: Acquire ALL keys on Node 3
        Client->>R3: SET account:1 val1 NX PX
        R3-->>Client: OK
        Client->>R3: SET account:2 val2 NX PX
        R3-->>Client: OK
        Client->>R3: SET account:3 val3 NX PX
        R3-->>Client: OK
    end

    Note over Client: Quorum achieved for ALL keys

    rect rgb(200, 230, 200)
        Note over Client: Critical Section (all resources locked)
    end

    Client->>R1: DEL account:1, account:2, account:3
    Client->>R2: DEL account:1, account:2, account:3
    Client->>R3: DEL account:1, account:2, account:3
```

## Key Concepts

### Sorted Key Ordering
Keys are sorted to prevent deadlocks:
```java
// Input: ["account:2", "account:1", "account:3"]
// Locked: ["account:1", "account:2", "account:3"]
```
All clients acquire in the same order → no circular wait.

### All-or-Nothing Semantics
If any key fails to acquire quorum, all acquired keys are released:
```
Success: All keys locked → proceed
Failure: Any key fails → release all, retry
```

### Unique Lock Values
Each key gets a unique lock value for safe unlock:
```java
Map<String, String> lockValues = {
    "account:1" → "uuid-1",
    "account:2" → "uuid-2", 
    "account:3" → "uuid-3"
}
```

## Usage

```java
Lock multiLock = redlockManager.createMultiLock(
    Arrays.asList("account:1", "account:2", "account:3")
);

multiLock.lock();
try {
    // All three accounts are now locked atomically
    transferBetweenAccounts();
} finally {
    multiLock.unlock();
}
```

## Deadlock Prevention

Without consistent ordering, deadlocks can occur:

| Time | Client A | Client B |
|------|----------|----------|
| T1 | Lock account:1 ✓ | Lock account:2 ✓ |
| T2 | Wait account:2 | Wait account:1 |
| T3 | **DEADLOCK** | **DEADLOCK** |

With sorted ordering, both clients lock `account:1` first → no deadlock.

## Supported Modes

| Mode | Supported | Notes |
|------|-----------|-------|
| **Single Node** | ✅ | All keys locked on single instance |
| **Multi-Node (Quorum)** | ✅ | All keys must achieve quorum on each node |

In multi-node mode, ALL keys must be successfully locked on a quorum of nodes for the MultiLock to succeed.

## Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `lockTimeoutMs` | 30000 | Lock TTL per key |
| `acquisitionTimeoutMs` | 10000 | Max total wait time |

## When to Use

**Good for:**
- Banking: transferring between accounts
- Inventory: reserving multiple items atomically
- Graph operations: locking connected nodes

**Consider alternatives for:**
- Single resource → [Redlock](redlock.md)
- Permit-based limiting → [Semaphore](semaphore.md)

## See Also

- [Redlock](redlock.md) - Single resource locking
- [ReadWriteLock](read-write-lock.md) - Reader/writer pattern
