# MultiLock Benchmark Results

**Generated:** 2026-06-15 19:01:18

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 5 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson | redlock4j-singlenode | redlock4j-multilock | 
|--------|--------|--------|--------|
| Total Ops/s | 17.05 | 16.97 | 17.72 | 
| Avg Ops/s/Client (95% CI) | 3.41 ± 1.07 | 3.39 ± 1.09 | 3.54 ± 0.69 | 
| Successful Ops | 1,022 | 1,018 | 1,061 | 
| Failed Ops | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 272.93 ms | 302.99 ms | 237.40 ms | 
| Correctness | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j-multilock | 
|------------|--------|--------|--------|
| p50 | 190,208 | 202,917 | 170,985 | 
| p75 | 348,389 | 373,225 | 298,446 | 
| p90 | 583,926 | 649,731 | 556,400 | 
| p95 | 789,649 | 1,038,258 | 687,449 | 
| p99 | 1,192,186 | 1,709,499 | 1,057,619 | 
| p999 | 1,439,439 | 2,087,382 | 1,489,792 | 
| max | 1,439,439 | 2,087,382 | 1,489,792 | 
| mean | 272,925 | 302,988 | 237,400 | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1537

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1539

### redlock4j-multilock

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1598

## Analysis

**Highest Throughput:** redlock4j-multilock with 17.72 ops/s

**Lowest Latency:** redlock4j-multilock with 237.40 ms average wait time

