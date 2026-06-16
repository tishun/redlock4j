# Fair Lock Benchmark Results

**Generated:** 2026-06-16 09:01:07

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 5 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson | redlock4j-singlenode | redlock4j-lettuce | redlock4j-jedis | 
|--------|--------|--------|--------|--------|
| Total Ops/s | 16.95 | 11.92 | 11.68 | 11.98 | 
| Avg Ops/s/Client (95% CI) | 3.39 ± 0.47 | 2.38 ± 0.00 | 2.34 ± 0.00 | 2.40 ± 0.00 | 
| Successful Ops | 1,088 | 716 | 701 | 719 | 
| Failed Ops | 0 | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 248.53 ms | 365.02 ms | 373.32 ms | 362.73 ms | 
| Correctness | PASS | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j-lettuce | redlock4j-jedis | 
|------------|--------|--------|--------|--------|
| p50 | 221,531 | 376,841 | 361,576 | 354,855 | 
| p75 | 222,592 | 386,740 | 407,230 | 398,471 | 
| p90 | 223,792 | 436,671 | 416,142 | 411,908 | 
| p95 | 224,794 | 439,692 | 439,925 | 418,482 | 
| p99 | 229,468 | 471,050 | 476,082 | 445,089 | 
| p999 | 6,187,845 | 492,703 | 482,587 | 455,135 | 
| max | 6,187,845 | 492,703 | 482,587 | 455,135 | 
| mean | 248,525 | 365,020 | 373,319 | 362,725 | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1633

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1080

### redlock4j-lettuce

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1054

### redlock4j-jedis

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1079

## Analysis

**Highest Throughput:** redisson with 16.95 ops/s

**Lowest Latency:** redisson with 248.53 ms average wait time

