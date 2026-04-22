# Fair Lock Benchmark Results

**Generated:** 2026-04-21 01:38:55

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 10 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson-reader | redlock4j-singlenode-writer | redlock4j-rwlock-writer | 
|--------|--------|--------|--------|
| Total Ops/s | 67.55 | 31.55 | 27.89 | 
| Avg Ops/s/Client | 11.26 | 5.26 | 4.65 | 
| Successful Ops | 4,056 | 1,896 | 1,676 | 
| Failed Ops | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 229.26 ms | 218.25 ms | 202.78 ms | 
| Correctness | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson-reader | redlock4j-singlenode-writer | redlock4j-rwlock-writer | 
|------------|--------|--------|--------|
| p50 | 10,158 | 1,451 | 3,475 | 
| p75 | 133,469 | 1,791 | 70,346 | 
| p90 | 570,819 | 249,584 | 758,417 | 
| p95 | 782,472 | 1,354,260 | 1,362,848 | 
| p99 | 4,284,011 | 4,606,454 | 2,665,878 | 
| p999 | N/A | N/A | N/A | 
| max | 4,672,595 | 8,791,700 | 3,940,744 | 
| mean | 229,257 | 218,248 | 202,774 | 

## Correctness Validation

### redisson-reader

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 167

### redlock4j-singlenode-writer

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1119

### redlock4j-rwlock-writer

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 924

## Analysis

**Highest Throughput:** redisson-reader with 67.55 ops/s

**Lowest Latency:** redlock4j-rwlock-writer with 202.78 ms average wait time

