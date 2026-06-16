# Fair Lock Benchmark Results

**Generated:** 2026-06-16 08:37:31

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 2 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson | redlock4j-singlenode | redlock4j-lettuce | redlock4j-jedis | 
|--------|--------|--------|--------|--------|
| Total Ops/s | 18.07 | 0.00 | 0.02 | 0.00 | 
| Avg Ops/s/Client (95% CI) | 9.03 ± 0.00 | 0.00 ± 0.00 | 0.01 ± 0.02 | 0.00 ± 0.00 | 
| Successful Ops | 1,084 | 0 | 1 | 0 | 
| Failed Ops | 0 | 2 | 1 | 2 | 
| Success Rate | 100.00% | 0.00% | 50.00% | 0.00% | 
| Avg Wait Time | 56.17 ms | 0.00 ms | 30005.60 ms | 0.00 ms | 
| Correctness | PASS | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j-lettuce | redlock4j-jedis | 
|------------|--------|--------|--------|--------|
| p50 | 56,061 | N/A | 60,011,198 | N/A | 
| p75 | 56,701 | N/A | 60,011,198 | N/A | 
| p90 | 57,619 | N/A | 60,011,198 | N/A | 
| p95 | 58,521 | N/A | 60,011,198 | N/A | 
| p99 | 62,769 | N/A | 60,011,198 | N/A | 
| p999 | 68,806 | N/A | 60,011,198 | N/A | 
| max | 68,806 | N/A | 60,011,198 | N/A | 
| mean | 56,172 | N/A | 60,011,198 | N/A | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1174

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1

### redlock4j-lettuce

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 3

### redlock4j-jedis

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1

## Analysis

**Highest Throughput:** redisson with 18.07 ops/s

**Lowest Latency:** redlock4j-singlenode with 0.00 ms average wait time

