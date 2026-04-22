# Performance Benchmarks

This guide provides comprehensive performance comparisons between **redlock4j** and other Redis-based locking libraries.

## Test Environment

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 (Testcontainers) |
| Redis Version | 7-alpine |
| Work Simulation | 50ms per lock hold |
| Lock Timeout | 30s |
| Benchmark Duration | 1 minute per implementation |
| JDK | 17 |

---

## Single-Node vs Multi-Node Mode

redlock4j automatically detects single-node deployments and uses an optimized `SingleNodeStrategy` that eliminates:

- Quorum calculations
- Clock drift compensation  
- Node iteration overhead

### Distributed Lock Comparison (1 client, no contention)

| Implementation | Throughput (ops/s) | Notes |
|----------------|-------------------|-------|
| **redlock4j-singlenode** | **18.33** | SingleNodeStrategy optimized |
| ShedLock | 18.37 | Single node |
| Spring Integration | 18.23 | Single node |
| Redisson RLock | 18.32 | Single node |
| RedPulsar | 18.26 | 3-node Redlock |
| redlock4j-3node | 17.60 | Full 3-node Redlock |

**Key Finding**: Single-node mode is **competitive with other single-node implementations** while retaining the ability to scale to multi-node Redlock when needed.

> **Note**: Multi-client contention scenarios with 3-node Redlock show degraded performance due to the inherent cost of distributed consensus. For high-contention workloads, consider single-node mode or tuning retry delays.

---

## Lock Type Benchmarks

### 1. Basic Distributed Lock (Redlock)

The fundamental distributed lock implementation.

| Metric | Redisson | redlock4j-singlenode | redlock4j-3node |
|--------|----------|---------------------|-----------------|
| **Total Ops/s** | 18.21 | **18.56** | 17.49 |
| **Avg Wait Time** | 229ms | **218ms** | 265ms |
| **p50 Latency** | 56.5ms | **0.6ms** | 1.3ms |
| **p95 Latency** | 923ms | 110ms | 1,775ms |
| **Correctness** | ✅ PASS | ✅ PASS | ✅ PASS |

**Analysis**: redlock4j-singlenode outperforms Redisson by 2% with **94x better p50 latency**.

---

### 2. FairLock (FIFO Ordering)

Guarantees lock acquisition in request order using Redis sorted sets.

| Metric | Redisson | redlock4j-singlenode | redlock4j-3node |
|--------|----------|---------------------|-----------------|
| **Total Ops/s** | **18.25** | 17.85 | 16.52 |
| **Avg Wait Time** | **115ms** | 116ms | 126ms |
| **FIFO Violations** | 0 | 0 | 0 |

**Analysis**: Redisson slightly faster due to Lua script optimization. Both maintain strict FIFO ordering.

---

### 3. MultiLock (Atomic Multi-Resource)

Acquires multiple resources atomically with deadlock prevention.

| Metric | Redisson | redlock4j-singlenode | redlock4j-3node |
|--------|----------|---------------------|-----------------|
| **Total Ops/s** | 17.58 | **18.02** | 16.87 |
| **Avg Wait Time** | **114ms** | 117ms | 124ms |
| **Correctness** | ✅ PASS | ✅ PASS | ✅ PASS |

**Analysis**: redlock4j-singlenode is **2.5% faster** than Redisson.

---

### 4. ReadWriteLock

Allows concurrent readers with exclusive writers.

| Metric | Redisson | redlock4j-singlenode | redlock4j-3node |
|--------|----------|---------------------|-----------------|
| **Reader Ops/s** | **50.44** | 20.40 | 9.24 |
| **Writer Ops/s** | 6.09 | **17.81** | 16.65 |
| **Correctness** | ✅ PASS | ✅ PASS | ✅ PASS |

**Analysis**: Redisson excels at concurrent reads; redlock4j has **3x better writer throughput**.

---

### 5. Semaphore

Limits concurrent access to a resource (configurable permits).

| Metric | Redisson | redlock4j-singlenode | redlock4j-3node |
|--------|----------|---------------------|-----------------|
| **Total Ops/s** | 54.69 | **108.49** | 103.32 |
| **Avg Wait Time** | 56.94ms | **0.85ms** | 2.09ms |
| **Correctness** | ✅ PASS | ✅ PASS | ✅ PASS |

**Analysis**: redlock4j-singlenode is **2x faster** with **67x lower latency**.

---

### 6. CountDownLatch

Distributed coordination - wait for N events to complete.

| Metric | Redisson | redlock4j-singlenode | redlock4j-3node |
|--------|----------|---------------------|-----------------|
| **Latches/s** | 59.66 | 35.59 | **60.97** |
| **Avg Wait Time** | 16.74ms | **16.32ms** | 16.34ms |
| **Correctness** | ✅ PASS | ✅ PASS | ✅ PASS |

**Analysis**: redlock4j-3node slightly beats Redisson. Coordination primitives benefit from multi-node distribution.

---

## Summary

### Throughput (ops/s) - Higher is Better

| Lock Type | Redisson | redlock4j-singlenode | redlock4j-3node | Winner |
|-----------|----------|---------------------|-----------------|--------|
| Distributed Lock | 18.21 | **18.56** | 17.49 | redlock4j |
| FairLock | **18.25** | 17.85 | 16.52 | Redisson |
| MultiLock | 17.58 | **18.02** | 16.87 | redlock4j |
| ReadWriteLock | **56.53** | 38.22 | 25.89 | Redisson |
| **Semaphore** | 54.69 | **108.49** | 103.32 | **redlock4j** |
| CountDownLatch | 59.66 | 35.59 | **60.97** | redlock4j |

### Latency (avg ms) - Lower is Better

| Lock Type | Redisson | redlock4j-singlenode | redlock4j-3node | Winner |
|-----------|----------|---------------------|-----------------|--------|
| Distributed Lock | 229 | **218** | 265 | redlock4j |
| FairLock | **115** | 116 | 126 | Redisson |
| MultiLock | **114** | 117 | 124 | Redisson |
| ReadWriteLock | **110** | 117 | 275 | Redisson |
| **Semaphore** | 56.94 | **0.85** | 2.09 | **redlock4j** |
| CountDownLatch | 16.74 | **16.32** | 16.34 | redlock4j |

---

## Key Takeaways

1. **Basic Distributed Lock**: redlock4j matches or beats Redisson
2. **FairLock**: Redisson has slight edge due to Lua optimization
3. **MultiLock**: redlock4j competitive, simpler implementation
4. **ReadWriteLock**: Redisson better for read-heavy, redlock4j better for write-heavy
5. **Semaphore**: redlock4j is **2x faster** with **67x lower latency**
6. **CountDownLatch**: redlock4j-3node slightly faster
7. **3-node overhead**: ~5-10% performance cost for distributed consensus
8. **Correctness**: All implementations pass with zero violations

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

| Option | Description | Default |
|--------|-------------|---------|
| `--duration <min>` | Benchmark duration in minutes | 30 |
| `--clients <n>` | Number of concurrent clients | 10 |
| `--nodes <n>` | Number of Redis nodes | 3 |
| `--warmup <sec>` | Warmup duration in seconds | 60 |
| `--resources <n>` | Resources per MultiLock | 5 |
| `--writers <n>` | Writer count for RWLock | 2 |
| `--readers <n>` | Reader count for RWLock | 8 |
| `--permits <n>` | Semaphore permits | 3 |
| `--count <n>` | CountDownLatch count | 5 |

### Quick Benchmark

For a quick comparison (1 minute):

```bash
mvn exec:java -Dbenchmark.mainClass="org.codarama.redlock4j.benchmark.DistributedLockBenchmarkMain" \
    -Dexec.args="--duration 1 --clients 5 --warmup 10"
```
