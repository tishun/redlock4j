# Fair Lock Benchmark Results

**Generated:** 2026-04-21 01:42:24

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 4 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson | redlock4j-singlenode | redlock4j | 
|--------|--------|--------|--------|
| Total Ops/s | 36.62 | 72.94 | 70.17 | 
| Avg Ops/s/Client | 9.16 | 18.24 | 17.54 | 
| Successful Ops | 2,200 | 4,380 | 4,212 | 
| Failed Ops | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 55.71 ms | 0.78 ms | 1.70 ms | 
| Correctness | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j | 
|------------|--------|--------|--------|
| p50 | 986 | 750 | 1,662 | 
| p75 | 28,597 | 912 | 1,789 | 
| p90 | 192,859 | 1,023 | 1,959 | 
| p95 | 344,472 | 1,122 | 2,235 | 
| p99 | 614,249 | 1,441 | 3,024 | 
| p999 | N/A | N/A | N/A | 
| max | 1,022,297 | 5,403 | 6,445 | 
| mean | 55,706 | 778 | 1,696 | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 2200

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 4380

### redlock4j

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 4212

## Analysis

**Highest Throughput:** redlock4j-singlenode with 72.94 ops/s

**Lowest Latency:** redlock4j-singlenode with 0.78 ms average wait time

