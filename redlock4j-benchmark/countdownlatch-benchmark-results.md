# Fair Lock Benchmark Results

**Generated:** 2026-04-21 01:45:35

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 10 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson | redlock4j-singlenode | redlock4j | 
|--------|--------|--------|--------|
| Total Ops/s | 58.36 | 35.65 | 62.08 | 
| Avg Ops/s/Client | 58.36 | 35.65 | 62.08 | 
| Successful Ops | 3,503 | 2,142 | 3,729 | 
| Failed Ops | 0 | 5 | 0 | 
| Success Rate | 100.00% | 99.77% | 100.00% | 
| Avg Wait Time | 17.10 ms | 16.29 ms | 16.04 ms | 
| Correctness | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j | 
|------------|--------|--------|--------|
| p50 | N/A | N/A | N/A | 
| p75 | N/A | N/A | N/A | 
| p90 | N/A | N/A | N/A | 
| p95 | N/A | N/A | N/A | 
| p99 | N/A | N/A | N/A | 
| p999 | N/A | N/A | N/A | 
| max | N/A | N/A | N/A | 
| mean | N/A | N/A | N/A | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0

### redlock4j

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0

## Analysis

**Highest Throughput:** redlock4j with 62.08 ops/s

**Lowest Latency:** redlock4j with 16.04 ms average wait time

