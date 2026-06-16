# Distributed Lock Benchmark Results

**Generated:** 2026-06-15 18:45:04

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
| Total Ops/s | 18.22 | 18.33 | 0.38 | 20.29 | 18.94 | 21.87 | 
| Avg Ops/s/Client (95% CI) | 3.64 ± 0.64 | 3.67 ± 2.81 | 0.08 ± 0.03 | 4.06 ± 0.70 | 3.79 ± 1.09 | 4.37 ± 1.00 | 
| Successful Ops | 1,091 | 1,093 | 23 | 1,106 | 1,123 | 1,107 | 
| Failed Ops | 0 | 0 | 5 | 0 | 0 | 0 | 
| Success Rate | 100.00% | 100.00% | 82.14% | 100.00% | 100.00% | 100.00% | 
| Avg Wait Time | 228.81 ms | 406.73 ms | 166.13 ms | 199.84 ms | 230.23 ms | 187.27 ms | 
| Correctness | PASS | PASS | PASS | PASS | PASS | PASS | 

## Latency Percentiles (microseconds)

| Percentile | redisson | redlock4j-singlenode | redlock4j-3node | spring-integration | shedlock-lettuce | redpulsar | 
|------------|--------|--------|--------|--------|--------|--------|
| p50 | 89,320 | 133,024 | 104,842 | 648 | 494 | 656 | 
| p75 | 342,395 | 572,609 | 337,742 | 764 | 706 | 821 | 
| p90 | 629,993 | 1,198,846 | 406,971 | 930 | 760,011 | 1,075 | 
| p95 | 914,320 | 1,573,656 | 406,971 | 218,039 | 1,618,169 | 1,630 | 
| p99 | 1,486,782 | 3,142,396 | 406,971 | 5,687,493 | 3,598,404 | 7,771,265 | 
| p999 | N/A | N/A | N/A | N/A | N/A | N/A | 
| max | 1,886,599 | 3,272,320 | 406,971 | 15,908,574 | 5,130,364 | 17,503,920 | 
| mean | 228,805 | 406,725 | 166,128 | 199,838 | 230,230 | 187,272 | 

## Correctness Validation

### redisson

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1639

### redlock4j-singlenode

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1644

### redlock4j-3node

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 185

### spring-integration

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1663

### shedlock-lettuce

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1689

### redpulsar

- **Status:** PASSED
- **Correctness Violations:** 0
- **FIFO Violations:** 0
- **Lock Events Analyzed:** 1664

## Analysis

**Highest Throughput:** redpulsar with 21.87 ops/s

**Lowest Latency:** redlock4j-3node with 166.13 ms average wait time

