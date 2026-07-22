# Distributed Lock Benchmark Results

**Generated:** 2026-06-16 12:43:41

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
| Total Ops/s | 18.24 | 18.33 | 0.81 | 19.47 | 18.86 | 21.63 | 
| Avg Ops/s/Client (95% CI) | 3.65 ± 0.60 | 3.67 ± 1.03 | 0.16 ± 0.06 | 3.89 ± 1.63 | 3.77 ± 0.49 | 4.33 ± 2.48 | 
| Successful Ops | 1,086 | 1,098 | 51 | 1,113 | 1,129 | 1,111 | 
| Failed Ops | 0 | 0 | 5 | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 91.07% | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 229.12 ms | 251.01 ms | 258.56 ms | 273.45 ms | 216.81 ms | 302.17 ms | 
| Correctness | PASS | PASS | PASS | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j-3node | spring-integration | shedlock-lettuce | redpulsar | 
|------------|--------|--------|--------|--------|--------|--------|
| p50 | 122,859 | 67,055 | 191,277 | 591 | 491 | 634 | 
| p75 | 346,087 | 339,311 | 366,379 | 694 | 671 | 803 | 
| p90 | 610,624 | 745,956 | 715,687 | 833 | 723,733 | 1,225 | 
| p95 | 828,274 | 1,127,312 | 858,015 | 209,974 | 1,673,087 | 172,153 | 
| p99 | 1,286,685 | 1,977,457 | 858,015 | 9,980,758 | 3,021,756 | 13,021,778 | 
| p999 | N/A | N/A | N/A | N/A | N/A | N/A | 
| max | 1,642,704 | 2,746,851 | 858,015 | 15,904,777 | 4,754,299 | 19,156,663 | 
| mean | 229,120 | 251,005 | 258,557 | 273,445 | 216,807 | 302,166 | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1636

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1647

### redlock4j-3node

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 300

### spring-integration

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1674

### shedlock-lettuce

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1697

### redpulsar

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1670

## Analysis

**Highest Throughput:** redpulsar with 21.63 ops/s

**Lowest Latency:** shedlock-lettuce with 216.81 ms average wait time

