# Fair Lock Benchmark Results

**Generated:** 2026-04-22 00:45:16

## Configuration

| Parameter | Value |
|-----------|-------|
| Redis Nodes | 3 |
| Clients per Implementation | 5 |
| Benchmark Duration | 1 minutes |
| Work Simulation Time | 50 ms |
| Lock Timeout | 30 s |

## Summary Comparison

| Metric | redisson | redlock4j-singlenode | redlock4j-3node | spring-integration | shedlock-lettuce | redpulsar | 
|--------|--------|--------|--------|--------|--------|--------|
| Total Ops/s | 18.33 | 18.36 | 0.50 | 18.55 | 18.87 | 18.49 | 
| Avg Ops/s/Client | 3.67 | 3.67 | 0.10 | 3.71 | 3.77 | 3.70 | 
| Successful Ops | 1,102 | 1,104 | 31 | 1,118 | 1,135 | 1,114 | 
| Failed Ops | 0 | 0 | 5 | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 86.11% | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 224.20 ms | 256.57 ms | 239.75 ms | 223.69 ms | 218.28 ms | 378.76 ms | 
| Correctness | PASS | PASS | PASS | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j-3node | spring-integration | shedlock-lettuce | redpulsar | 
|------------|--------|--------|--------|--------|--------|--------|
| p50 | 99,701 | 99,108 | 216,678 | 511 | 437 | 570 | 
| p75 | 338,708 | 341,942 | 414,533 | 675 | 687 | 778 | 
| p90 | 649,703 | 758,400 | 617,272 | 846 | 781,306 | 1,180 | 
| p95 | 885,987 | 1,045,643 | 617,272 | 1,318 | 1,649,013 | 2,432,775 | 
| p99 | 1,258,035 | 1,692,837 | 617,272 | 8,344,652 | 3,083,844 | 10,604,848 | 
| p999 | N/A | N/A | N/A | N/A | N/A | N/A | 
| max | 1,792,524 | 2,228,022 | 617,272 | 17,979,202 | 4,140,203 | 13,796,471 | 
| mean | 224,195 | 256,572 | 239,748 | 223,693 | 218,275 | 378,756 | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1102

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1104

### redlock4j-3node

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 31

### spring-integration

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1118

### shedlock-lettuce

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1135

### redpulsar

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1114

## Analysis

**Highest Throughput:** shedlock-lettuce with 18.87 ops/s

**Lowest Latency:** shedlock-lettuce with 218.28 ms average wait time

