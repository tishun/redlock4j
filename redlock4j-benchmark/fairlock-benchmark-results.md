# Fair Lock Benchmark Results

**Generated:** 2026-06-16 12:54:43

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
| Total Ops/s | 16.99 | 12.42 | 2.87 | 2.95 | 
| Avg Ops/s/Client (95% CI) | 3.40 ± 0.47 | 2.48 ± 0.00 | 0.57 ± 0.00 | 0.59 ± 0.00 | 
| Successful Ops | 1,092 | 745 | 172 | 177 | 
| Failed Ops | 0 | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 247.92 ms | 348.64 ms | 1685.38 ms | 1641.70 ms | 
| Correctness | PASS | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j-lettuce | redlock4j-jedis | 
|------------|--------|--------|--------|--------|
| p50 | 220,970 | 332,021 | 1,667,216 | 1,666,043 | 
| p75 | 222,141 | 380,775 | 2,009,072 | 1,962,217 | 
| p90 | 223,572 | 386,150 | 2,284,444 | 2,113,376 | 
| p95 | 224,687 | 388,930 | 2,369,874 | 2,363,625 | 
| p99 | 229,750 | 434,824 | 2,549,375 | 2,457,072 | 
| p999 | 6,194,120 | 436,999 | 2,549,375 | 2,457,072 | 
| max | 6,194,120 | 436,999 | 2,549,375 | 2,457,072 | 
| mean | 247,923 | 348,636 | 1,685,377 | 1,641,698 | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1638

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1119

### redlock4j-lettuce

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 274

### redlock4j-jedis

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 269

## Analysis

**Highest Throughput:** redisson with 16.99 ops/s

**Lowest Latency:** redisson with 247.92 ms average wait time

