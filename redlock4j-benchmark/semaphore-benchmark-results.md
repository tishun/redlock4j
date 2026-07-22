# Semaphore Benchmark Results

**Generated:** 2026-06-15 19:10:27

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 5 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson | redlock4j-singlenode | redlock4j | 
|--------|--------|--------|--------|
| Total Ops/s | 54.71 | 91.09 | 87.50 | 
| Avg Ops/s/Client (95% CI) | 10.94 ± 0.71 | 18.22 ± 0.00 | 17.50 ± 0.01 | 
| Successful Ops | 3,282 | 5,465 | 5,251 | 
| Failed Ops | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 37.87 ms | 0.83 ms | 1.84 ms | 
| Correctness | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j | 
|------------|--------|--------|--------|
| p50 | 1,002 | 733 | 1,767 | 
| p75 | 44,403 | 951 | 2,098 | 
| p90 | 122,490 | 1,216 | 2,466 | 
| p95 | 197,197 | 1,409 | 2,802 | 
| p99 | 386,491 | 2,210 | 4,199 | 
| p999 | N/A | N/A | N/A | 
| max | 734,851 | 12,744 | 13,774 | 
| mean | 37,870 | 825 | 1,837 | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 4925

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 8170

### redlock4j

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 7882

## Analysis

**Highest Throughput:** redlock4j-singlenode with 91.09 ops/s

**Lowest Latency:** redlock4j-singlenode with 0.83 ms average wait time

