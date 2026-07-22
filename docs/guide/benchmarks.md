# Performance Benchmarks

This guide summarizes performance comparisons between **redlock4j** and other Redis-based locking libraries.

!!! note "Authoritative source"
    The full, authoritative benchmark results live in the [Architecture guide's Performance Analysis section](architecture.md#performance-analysis) and in `redlock4j-benchmark/benchmark-analysis.md` (§7). This page summarizes those results; if the numbers ever diverge, the Architecture guide and `benchmark-analysis.md` win.

## Test Environment

| Parameter          | Value                                           |
|--------------------|-------------------------------------------------|
| Redis Nodes        | 3 (Testcontainers)                              |
| Redis Version      | 7-alpine                                        |
| Work Simulation    | 50ms per lock hold                              |
| Lock Timeout       | 30s                                             |
| Clients            | 5 (10 for ReadWriteLock: 5 readers + 5 writers) |
| Warmup (discarded) | 30s                                             |
| Measurement        | 60s (1 minute) per implementation               |
| JDK                | 17                                              |

---

## Single-Node vs Multi-Node Mode

redlock4j automatically detects single-node deployments and uses an optimized `SingleNodeStrategy` that eliminates:

- Quorum calculations
- Clock drift compensation  
- Node iteration overhead

### Distributed Lock Comparison (5 clients under contention)

| Implementation           | Throughput (ops/s) | Notes                                          |
|--------------------------|--------------------|------------------------------------------------|
| RedPulsar                | 21.63              | 3-node Redlock; throughput leader              |
| Spring Integration       | 19.47              | Single node                                    |
| ShedLock                 | 18.86              | Single node                                    |
| **redlock4j-singlenode** | **18.33**          | SingleNodeStrategy optimized                   |
| Redisson RLock           | 18.24              | Single node                                    |
| redlock4j-3node          | 0.81               | Full 3-node Redlock (best p99 in field: 858ms) |

**Key Finding**: Single-node mode is **competitive with other single-node implementations** while retaining the ability to scale to multi-node Redlock when needed.

> **Note**: 3-node Redlock shows low throughput on the basic `DistributedLock` primitive because of the **polling wait strategy under contention**, not the cost of consensus per se — waiters sleep on a fixed poll interval and race on release. Exponential backoff (already landed) doubled 3-node throughput; a pub/sub-on-release wait strategy (planned, benchmark-analysis §8 A3) targets the remainder. Despite the throughput gap, 3-node redlock4j has **best-in-class p99** on several primitives (DistributedLock, RWLock writer, MultiLock, Semaphore, CountDownLatch). For high-throughput contention workloads today, prefer single-node mode.

---

## Lock Type Benchmarks

### 1. Basic Distributed Lock (Redlock)

The fundamental distributed lock implementation.

| Metric             | Redisson  | redlock4j-singlenode | redlock4j-3node  |
|--------------------|-----------|----------------------|------------------|
| **Total Ops/s**    | 18.24     | **18.33**            | 0.81             |
| **Mean Wait Time** | 229ms     | 251ms                | 259ms            |
| **p50 Latency**    | 122.9ms   | **67.1ms**           | 191.3ms          |
| **p99 Latency**    | 1,286.7ms | 1,977.5ms            | **858.0ms**      |
| **Correctness**    | PASS      | PASS                 | PASS             |

**Analysis**: redlock4j-singlenode is at parity with Redisson on throughput (within 1%) and has better p50 latency. redlock4j-3node trades throughput for the **best p99 in the field** (858ms vs Redisson 1,286.7ms); the 3-node throughput gap traces to the polling wait strategy under contention (see note above).

---

### 2. FairLock (FIFO Ordering)

Guarantees lock acquisition in request order using Redis sorted sets. FairLock uses the polling wait strategy (keyspace notifications degrade FairLock, and exponential backoff also hurts the head-of-queue waiter).

| Metric              | Redisson    | redlock4j-singlenode | redlock4j-3node  |
|---------------------|-------------|----------------------|------------------|
| **Total Ops/s**     | **16.95**   | 11.92                | ~2.95            |
| **Mean Wait Time**  | **249ms**   | 365ms                | 1,685ms          |
| **p99 Latency**     | **229.5ms** | 471.1ms              | 2,457.1ms        |
| **FIFO Violations** | 0           | 0                    | 0                |

**Analysis**: Redisson leads FairLock (~1.4x faster than single-node redlock4j) thanks to its single-Lua-script head-of-queue path. Switching redlock4j FairLock to polling moved it from ~0 ops/s to ~70% of Redisson's throughput; the per-attempt quorum head-check round-trip on 3 nodes is the remaining bottleneck (planned fix: atomic head-check + acquire). All implementations maintain strict FIFO ordering.

---

### 3. MultiLock (Atomic Multi-Resource)

Acquires multiple resources atomically with deadlock prevention.

| Metric             | Redisson  | redlock4j-singlenode | redlock4j-3node  |
|--------------------|-----------|----------------------|------------------|
| **Total Ops/s**    | 17.05     | 16.97                | **17.72**        |
| **Mean Wait Time** | 273ms     | 303ms                | **237ms**        |
| **p99 Latency**    | 1,192ms   | 1,709.5ms            | **1,057.6ms**    |
| **Correctness**    | PASS      | PASS                 | PASS             |

**Analysis**: redlock4j-3node (multilock) is the leader on **every axis** — throughput, mean wait, and p99 — after the parallel multi-node I/O change.

---

### 4. ReadWriteLock

Allows concurrent readers with exclusive writers (10 clients: 5 readers + 5 writers).

| Metric           | Redisson   | redlock4j-singlenode | redlock4j-3node  |
|------------------|------------|----------------------|------------------|
| **Reader Ops/s** | **151.65** | 74.66                | 95.41            |
| **Reader p99**   | **5.82ms** | 340.6ms              | 348.4ms          |
| **Writer Ops/s** | 0.21       | 15.63                | **16.42**        |
| **Writer p99**   | 16,820.7ms | 755.6ms              | **104.2ms**      |
| **Correctness**  | PASS       | PASS                 | PASS             |

**Analysis**: Redisson leads reader throughput (~1.6x) because it decrements readers in-process via semaphore counting, while redlock4j hits Redis on every read acquire (an architectural choice, not a regression). On the writer side, **Redisson is starved** (0.21 ops/s, ~9.7s mean wait — only a handful of successful writes in 60s), while redlock4j-3node leads with 16.42 ops/s and a best-in-class 104.2ms writer p99.

---

### 5. Semaphore

Limits concurrent access to a resource (configurable permits).

| Metric             | Redisson  | redlock4j-singlenode | redlock4j-3node  |
|--------------------|-----------|----------------------|------------------|
| **Total Ops/s**    | 54.71     | **91.09**            | 87.50            |
| **Mean Wait Time** | 37.87ms   | **0.83ms**           | 1.84ms           |
| **p99 Latency**    | 386.5ms   | **2.21ms**           | 4.20ms           |
| **Correctness**    | PASS      | PASS                 | PASS             |

**Analysis**: redlock4j-singlenode is **~1.7x faster** than Redisson with **~100x lower p99 latency**; the 3-node mode is nearly as fast.

---

### 6. CountDownLatch

Distributed coordination - wait for N events to complete.

| Metric             | Redisson  | redlock4j-singlenode | redlock4j-3node  |
|--------------------|-----------|----------------------|------------------|
| **Latches/s**      | 59.02     | 58.19                | **59.91**        |
| **Mean Wait Time** | 16.91ms   | 15.67ms              | **15.18ms**      |
| **p99 Latency**    | 22.6ms    | 19.9ms               | **19.9ms**       |
| **Correctness**    | PASS      | PASS                 | PASS             |

**Analysis**: All three are within ~3% on throughput; redlock4j-3node has a slight edge on both throughput and p99 latency.

---

## Summary

### Throughput (ops/s) - Higher is Better

| Lock Type              | Redisson   | redlock4j-singlenode | redlock4j-3node  | Winner                  |
|------------------------|------------|----------------------|------------------|-------------------------|
| Distributed Lock       | 18.24      | **18.33**            | 0.81             | redlock4j (single-node) |
| FairLock               | **16.95**  | 11.92                | ~2.95            | Redisson                |
| MultiLock              | 17.05      | 16.97                | **17.72**        | redlock4j (3-node)      |
| ReadWriteLock (reader) | **151.65** | 74.66                | 95.41            | Redisson                |
| ReadWriteLock (writer) | 0.21       | 15.63                | **16.42**        | redlock4j (3-node)      |
| **Semaphore**          | 54.71      | **91.09**            | 87.50            | **redlock4j**           |
| CountDownLatch         | 59.02      | 58.19                | **59.91**        | redlock4j (3-node)      |

### p99 Latency (ms) - Lower is Better

| Lock Type              | Redisson  | redlock4j-singlenode | redlock4j-3node  | Winner             |
|------------------------|-----------|----------------------|------------------|--------------------|
| Distributed Lock       | 1,286.7   | 1,977.5              | **858.0**        | redlock4j (3-node) |
| FairLock               | **229.8** | 434.8                | 2,457.1          | Redisson           |
| MultiLock              | 1,192.2   | 1,709.5              | **1,057.6**      | redlock4j (3-node) |
| ReadWriteLock (reader) | **5.82**  | 340.6                | 348.4            | Redisson           |
| ReadWriteLock (writer) | 16,820.7  | 755.6                | **104.2**        | redlock4j (3-node) |
| **Semaphore**          | 386.5     | **2.21**             | 4.20             | **redlock4j**      |
| CountDownLatch         | 22.6      | 19.9                 | **19.9**         | redlock4j (3-node) |

---

## Key Takeaways

1. **Basic Distributed Lock**: single-node redlock4j is at parity with Redisson on throughput; 3-node has the best p99 in the field but low throughput under contention (polling wait strategy, not consensus cost)
2. **FairLock**: Redisson has the edge due to its single-Lua-script head-of-queue path
3. **MultiLock**: redlock4j-3node leads on every axis (throughput and p99)
4. **ReadWriteLock**: Redisson better for read-heavy; redlock4j far better for write-heavy (Redisson starves writers) and best-in-class writer p99
5. **Semaphore**: redlock4j is **~1.7x faster** with **~100x lower p99 latency**
6. **CountDownLatch**: redlock4j-3node slightly faster on throughput and p99
7. **3-node throughput gap**: on `DistributedLock` and `FairLock` the bottleneck is the polling wait strategy under contention (pub/sub-on-release is the planned fix); other primitives are at or above field-leader performance
8. **redlock4j leads 4 of 7 categories on throughput and is best-in-class on p99 for 5 of 7**
9. **Correctness**: All implementations pass with zero violations

---

## Running Benchmarks

The benchmark module is located in `redlock4j-benchmark/`. Requirements:

- Java 17+
- Docker (for Testcontainers)

### Basic Commands

```bash
cd redlock4j-benchmark

# Basic Distributed Lock
mvn exec:java -Dbenchmark.mainClass="org.codarama.redlock4j.benchmark.DistributedLockBenchmarkMain" \
    -Dexec.args="--duration 5 --clients 10"

# FairLock (FIFO ordering)
mvn exec:java -Dexec.args="--duration 5 --clients 10"

# MultiLock (atomic multi-resource)
mvn exec:java -Dbenchmark.mainClass="org.codarama.redlock4j.benchmark.MultiLockBenchmarkMain" \
    -Dexec.args="--duration 5 --clients 5 --resources 5"

# ReadWriteLock
mvn exec:java -Dbenchmark.mainClass="org.codarama.redlock4j.benchmark.ReadWriteLockBenchmarkMain" \
    -Dexec.args="--duration 5 --writers 2 --readers 8"

# Semaphore
mvn exec:java -Dbenchmark.mainClass="org.codarama.redlock4j.benchmark.SemaphoreBenchmarkMain" \
    -Dexec.args="--duration 5 --clients 6 --permits 3"

# CountDownLatch
mvn exec:java -Dbenchmark.mainClass="org.codarama.redlock4j.benchmark.CountDownLatchBenchmarkMain" \
    -Dexec.args="--duration 5 --count 5"
```

### Command Line Options

The table below lists the CLI **tool defaults**. Note these differ from the settings used to produce the results on this page: the published numbers were measured with **30s warmup + 60s (1 min) measurement, 5 clients** (10 for ReadWriteLock), matching the Architecture guide's Performance Analysis and `benchmark-analysis.md` §7.

| Option             | Description                            | Tool Default |
|--------------------|----------------------------------------|--------------|
| `--duration <min>` | Measurement duration in minutes        | 30           |
| `--clients <n>`    | Number of concurrent clients           | 10           |
| `--nodes <n>`      | Number of Redis nodes                  | 3            |
| `--warmup <sec>`   | Warmup duration in seconds (discarded) | 60           |
| `--resources <n>`  | Resources per MultiLock                | 5            |
| `--writers <n>`    | Writer count for RWLock                | 2            |
| `--readers <n>`    | Reader count for RWLock                | 8            |
| `--permits <n>`    | Semaphore permits                      | 3            |
| `--count <n>`      | CountDownLatch count                   | 5            |

### Quick Benchmark

For a quick comparison (1 minute):

```bash
mvn exec:java -Dbenchmark.mainClass="org.codarama.redlock4j.benchmark.DistributedLockBenchmarkMain" \
    -Dexec.args="--duration 1 --clients 5 --warmup 10"
```
