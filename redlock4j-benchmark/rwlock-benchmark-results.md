# ReadWriteLock Benchmark Results

**Generated:** 2026-06-16 13:00:20

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
| Total Ops/s | 151.65 | 74.66 | 95.41 | 0.21 | 15.63 | 16.42 | 
| Avg Ops/s/Client (95% CI) | 18.96 ± 0.02 | 9.33 ± 0.62 | 11.93 ± 0.48 | 0.10 ± 0.02 | 7.82 ± 0.52 | 8.21 ± 0.06 | 
| Successful Ops | 9,098 | 4,479 | 5,735 | 6 | 940 | 496 | 
| Failed Ops | 0 | 0 | 0 | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 100.00% | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 1.32 ms | 54.22 ms | 30.42 ms | 9679.88 ms | 72.36 ms | 63.54 ms | 
| Correctness | PASS | PASS | PASS | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson-reader | redlock4j-singlenode-reader | redlock4j-rwlock-reader | redisson-writer | redlock4j-singlenode-writer | redlock4j-rwlock-writer | 
|------------|--------|--------|--------|--------|--------|--------|
| p50 | 788 | 12,541 | 2,722 | 8,648,961 | 57,777 | 64,145 | 
| p75 | 1,185 | 66,014 | 18,559 | 14,282,812 | 59,814 | 66,332 | 
| p90 | 1,754 | 160,401 | 94,526 | 16,820,725 | 64,436 | 68,545 | 
| p95 | 2,222 | 212,882 | 171,227 | 16,820,725 | 91,540 | 71,256 | 
| p99 | 5,823 | 340,626 | 348,441 | 16,820,725 | 755,568 | 104,200 | 
| p999 | N/A | N/A | N/A | N/A | N/A | N/A | 
| max | 165,110 | 533,835 | 626,540 | 16,820,725 | 3,029,828 | 239,124 | 
| mean | 1,317 | 54,219 | 30,421 | 9,679,882 | 72,356 | 63,534 | 

## Correctness Validation

### redisson-reader

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 25

### redlock4j-singlenode-reader

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1437

### redlock4j-rwlock-reader

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 946

### redisson-writer

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 25

### redlock4j-singlenode-writer

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1437

### redlock4j-rwlock-writer

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 946

## Analysis

**Highest Throughput:** redisson-reader with 151.65 ops/s

**Lowest Latency:** redisson-reader with 1.32 ms average wait time

