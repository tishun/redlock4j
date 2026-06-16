# ReadWriteLock Benchmark Results

**Generated:** 2026-06-15 19:05:53

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 10 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson-reader | redlock4j-singlenode-reader | redlock4j-rwlock-reader | redisson-writer | redlock4j-singlenode-writer | redlock4j-rwlock-writer | 
|--------|--------|--------|--------|--------|--------|--------|
| Total Ops/s | 153.62 | 59.07 | 53.59 | 0.07 | 17.26 | 15.51 | 
| Avg Ops/s/Client (95% CI) | 19.20 ± 0.01 | 7.38 ± 1.01 | 6.70 ± 1.02 | 0.04 ± 0.00 | 8.63 ± 0.13 | 7.76 ± 0.15 | 
| Successful Ops | 9,217 | 3,543 | 3,210 | 2 | 1,036 | 931 | 
| Failed Ops | 0 | 0 | 0 | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 100.00% | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 0.94 ms | 86.23 ms | 98.96 ms | 26977.79 ms | 60.99 ms | 70.82 ms | 
| Correctness | PASS | PASS | PASS | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson-reader | redlock4j-singlenode-reader | redlock4j-rwlock-reader | redisson-writer | redlock4j-singlenode-writer | redlock4j-rwlock-writer | 
|------------|--------|--------|--------|--------|--------|--------|
| p50 | 787 | 58,444 | 65,604 | 26,977,794 | 57,704 | 63,961 | 
| p75 | 1,130 | 122,436 | 133,819 | 26,977,794 | 58,483 | 65,647 | 
| p90 | 1,578 | 213,040 | 236,833 | 26,977,794 | 59,401 | 67,367 | 
| p95 | 1,920 | 277,235 | 311,223 | 26,977,794 | 60,295 | 68,532 | 
| p99 | 3,098 | 452,207 | 431,941 | 26,977,794 | 90,528 | 72,728 | 
| p999 | N/A | N/A | N/A | N/A | N/A | N/A | 
| max | 13,802 | 636,015 | 508,208 | 26,977,794 | 1,449,909 | 3,583,183 | 
| mean | 938 | 86,227 | 98,963 | 26,977,794 | 60,993 | 70,824 | 

## Correctness Validation

### redisson-reader

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 32

### redlock4j-singlenode-reader

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1563

### redlock4j-rwlock-reader

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1426

### redisson-writer

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 32

### redlock4j-singlenode-writer

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1563

### redlock4j-rwlock-writer

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1426

## Analysis

**Highest Throughput:** redisson-reader with 153.62 ops/s

**Lowest Latency:** redisson-reader with 0.94 ms average wait time

