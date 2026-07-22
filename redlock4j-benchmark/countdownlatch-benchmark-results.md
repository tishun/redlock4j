# CountDownLatch Benchmark Results

**Generated:** 2026-06-15 19:15:01

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
| Total Ops/s | 59.02 | 58.19 | 59.91 | 
| Avg Ops/s/Client (95% CI) | 59.02 | 58.19 | 59.91 | 
| Successful Ops | 3,542 | 3,500 | 3,601 | 
| Failed Ops | 0 | 1 | 1 | 
| Success Rate | 100.00% | 99.97% | 99.97% | 
| Avg Wait Time | 16.91 ms | 15.67 ms | 15.18 ms | 
| Correctness | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j | 
|------------|--------|--------|--------|
| p50 | 17,170 | 16,167 | 15,285 | 
| p75 | 17,869 | 16,850 | 16,437 | 
| p90 | 18,579 | 17,426 | 17,196 | 
| p95 | 19,225 | 17,889 | 17,813 | 
| p99 | 22,568 | 19,866 | 19,893 | 
| p999 | N/A | N/A | N/A | 
| max | 40,818 | 31,027 | 115,837 | 
| mean | 16,913 | 15,673 | 15,180 | 

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

**Highest Throughput:** redlock4j with 59.91 ops/s

**Lowest Latency:** redlock4j with 15.18 ms average wait time

