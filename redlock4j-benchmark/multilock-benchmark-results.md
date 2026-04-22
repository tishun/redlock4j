# Fair Lock Benchmark Results

**Generated:** 2026-04-21 01:35:27

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 3 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson | redlock4j-singlenode | redlock4j-multilock | 
|--------|--------|--------|--------|
| Total Ops/s | 17.44 | 17.80 | 16.73 | 
| Avg Ops/s/Client | 5.81 | 5.93 | 5.58 | 
| Successful Ops | 1,048 | 1,069 | 1,005 | 
| Failed Ops | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 115.48 ms | 142.73 ms | 126.66 ms | 
| Correctness | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j-multilock | 
|------------|--------|--------|--------|
| p50 | 95,354 | 1,319 | 2,996 | 
| p75 | 137,200 | 1,550 | 3,465 | 
| p90 | 223,156 | 2,588 | 146,669 | 
| p95 | 267,811 | 374,907 | 1,003,327 | 
| p99 | 439,992 | 5,194,174 | 2,569,274 | 
| p999 | 577,919 | 6,879,269 | 3,921,713 | 
| max | 577,919 | 6,879,269 | 3,921,713 | 
| mean | 115,481 | 142,728 | 126,659 | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1048

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1069

### redlock4j-multilock

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1005

## Analysis

**Highest Throughput:** redlock4j-singlenode with 17.80 ops/s

**Lowest Latency:** redisson with 115.48 ms average wait time

