# Benchmark Analysis: redlock4j vs Competitors

Source files analyzed:
- `distributed-lock-benchmark-results.md`
- `multilock-benchmark-results.md`
- `rwlock-benchmark-results.md`
- `semaphore-benchmark-results.md`
- `countdownlatch-benchmark-results.md`

## 1. Benchmark methodology issues (fix first; results are partly misleading)

| # | Issue | Evidence | Fix location |
|---|---|---|---|
| ~~M1~~ | ~~Every report titled "Fair Lock Benchmark Results"~~ | ~~`MarkdownReportGenerator:32` hard-codes title~~ | ~~`MarkdownReportGenerator.generate(...)` accept a title arg~~ **DONE** |
| ~~M2~~ | ~~CountDownLatch percentiles all `N/A`~~ | ~~`CountDownLatchBenchmarkScenario` builds `latencies` list, never calls `result.setLatencyPercentiles(...)`~~ | ~~scenario line ~95~~ **DONE** |
| ~~M3~~ | ~~RWLock report compares `redisson-reader` vs `redlock4j-*-writer` (apples/oranges)~~ | ~~`AbstractReadWriteLockClient` appends `-reader`/`-writer` to impl type; aggregator/report surfaces one row per `scenario.run()`; readers and writers collapse into one row dominated by whichever finishes first~~ | ~~aggregator + scenario need to emit both reader+writer rows per impl~~ **DONE** |
| ~~M4~~ | ~~`redlock4j-3node` distributed-lock numbers from a run where 5/36 attempts timed out (86% success) — throughput collapse may be partly run instability~~ | ~~`distributed-lock-benchmark-results.md:22`~~ | ~~re-run after fixes; add per-attempt logging~~ **DONE (re-measured §6.1/§7.1)** |
| ~~M5~~ | ~~"Fair lock" comparison includes non-fair impls (`shedlock-lettuce`, `spring-integration`, `redpulsar`)~~ | ~~`distributed-lock-benchmark-results.md:31-37`~~ | ~~label fairness column; split into separate fair/non-fair runs~~ **DONE (separate `FairLockBenchmarkMain` / `DistributedLockBenchmarkMain`)** |

## 2. Per-primitive gaps vs competitors (updated 2026-06-16, post P0-1 + A1 backoff)

| Primitive | Worst remaining gap | Number | Where redlock4j wins |
|---|---|---|---|
| Distributed lock (3-node) | Throughput | **~27× slower** (0.81 vs 21.63 ops/s), 91 % success | **Best p99 in field** (858 ms vs redisson 1,287 ms) |
| Distributed lock (single-node) | p99 vs redisson | ~1.5× higher (1.98 s vs 1.29 s) | Throughput within 1 % of redisson |
| MultiLock | — | parity / slight lead on throughput AND p99 | Leader on every axis (Ops/s 17.72, p99 1.06 s) |
| RWLock readers | Throughput vs redisson | ~1.6× slower (95.4 vs 151.7 ops/s) | redlock4j-rwlock now beats single-node (+28 %) |
| RWLock writers | — | redisson starved (0.21 ops/s, 9.7 s wait) | Leader (16.42 ops/s, 104 ms p99 vs redisson 16.8 s) |
| Semaphore | — | redlock4j ~1.7× faster than redisson, p99 ~100× lower | Clear lead |
| CountDownLatch | — | parity (redlock4j slight lead on Ops/s + p99) | Marginal lead |
| FairLock | Throughput vs redisson | ~1.4× slower (~12 vs 17 ops/s) — using polling fallback | Correctness/FIFO PASS |

## 3. Root causes (in redlock4j source)

| Code | Cause | Impact |
|---|---|---|
| ~~`MultiNodeStrategy.acquireLock`~~ | ~~**Sequential `for (driver : drivers)`** SETNX => 3x RTT per attempt instead of 1x~~ | ~~Catastrophic for 3-node mode~~ **DONE (P0-1: parallel `CompletableFuture` fan-out)** |
| ~~`MultiNodeStrategy.releaseLock` / `extendLock` / `executeOnNodes`~~ | ~~Same sequential loop — release also 3x RTT, multiplying contention windows~~ | ~~Tail latency, throughput~~ **DONE (P0-1)** |
| `Redlock.tryLock` + `PollingWaitStrategy` | Fixed `retryDelay=50ms` with jitter; no exponential backoff | Contention storms; all clients wake & race on same node simultaneously |
| `KeyspaceWaitStrategy` | Single global `__keyspace@0__` subscription per node; latch per lockKey; deletes/expirations on unrelated keys still fire callbacks; with 3 nodes each event fires 3 times | Wakeup amplification under load; matches FairLock perf warning already in `RedlockManager:247` |
| `FairLock.tryLock` | `addToQueue` -> `isAtFrontOfQueue` -> `attemptLock` -> wait -> repeat. Each step calls `executeOnNodes` (sequential N nodes). One full iteration on 3 nodes ~= 3 ZADD + 3 ZRANGE + 3 SETNX (+ 3 DEL on miss) = ~12 RTT per attempt | High avg wait; tail explosion |
| ~~`MultiLock.attemptMultiLock`~~ | ~~Per-key loop calling `executionStrategy` separately; no single Lua script per node for atomic multi-key acquisition~~ | ~~12x higher p99 vs Redisson's single Lua script~~ **OBSOLETE — post-P0-1 MultiLock is now leader on Ops/s and p99 (see §7.2). Scripted multi-key acquire still worth doing for correctness/atomicity (P2-10).** |
| `Redlock`/`FairLock` reentrancy | Stored in `ThreadLocal<LockState>` — only same-thread reentrancy; no Redis-side hold-count; no cross-thread/process reentrancy | Feature gap vs Redisson |
| No watchdog / auto-renewal | Lock TTL = `defaultLockTimeout`; only `extendLock` exposed manually | Forces large TTLs (30s in benchmark) => slow expiry recovery |
| `generateLockValue` | `SecureRandom.nextBytes(20)` + per-byte `String.format("%02x", b)` on every attempt | Hot-path GC + CPU cost |
| No pipelining/batching in `JedisRedisDriver`/`LettuceRedisDriver` | Each `setIfNotExists` is a sync round-trip; Lettuce async API unused on sync path | Sync path doesn't exploit Lettuce's async pipelining |
| `AsyncRedlockImpl.attemptLock` | Also calls `executionStrategy.acquireLock` synchronously — async wrapper is effectively fake | Async API doesn't parallelize node I/O |

## 4. Improvement proposals (prioritized; breaking changes marked **BC**)

### P0 — Major perf, mostly non-breaking
1. ~~**Parallelize multi-node I/O in `MultiNodeStrategy`** — replace sequential loops with `CompletableFuture.allOf` over async drivers. Expected: 3-node distributed-lock throughput from 0.50 -> ~15 ops/s (30x). Internal only, non-breaking.~~ **DONE — actual: p99 407 ms (best-in-class), throughput unchanged (bottleneck moved to polling wait strategy; see §6.1).**
2. **Move `AsyncRedlockImpl` to a real async core** — `acquireLock` returns `CompletionStage`, sync `Redlock` is a `.toCompletableFuture().get()` wrapper. **BC** to `RedisDriver` SPI (new async methods). Foundation for everything below.
3. **Single Lua script per node** for: SETNX-with-reentry-counter, atomic release-with-publish, atomic multi-key acquire, RW-lock mode transitions. **BC** to `RedisDriver` SPI (add `eval`/scripted ops). Closes Redisson p99 gap on multi-lock.
4. **Replace `KeyspaceWaitStrategy` default with Pub/Sub-on-release** (Redisson-style): release script publishes on a per-key channel; waiters `SUBSCRIBE channel`. Eliminates global keyspace noise and 3x event amplification. Keep keyspace as fallback. Mostly internal, non-breaking unless config flags change.

### P1 — Tail-latency & stability
5. **Exponential backoff with jitter** in `Redlock.tryLock` retry loop and in `PollingWaitStrategy`. Configurable via `RedlockConfiguration` (additive, non-breaking).
6. **Lock watchdog / auto-renewal** scheduled per held lock; opt-out via config. Lets users use shorter TTLs (1-5s) safely. Additive API on `RedlockManager`/`Redlock`.
7. **Hash-based reentrancy & cross-thread holders** on Redis side, like Redisson (`HSET lock {clientId:threadId} count`). **BC** to lock value semantics and release scripts; existing simple SETNX users get migration via config flag.

### P2 — Architectural cleanup
8. **Drop FairLock's quorum vote on `isAtFrontOfQueue`** in favor of a single authoritative queue-node (or Lua-script atomic "am-I-head AND acquire"), turning 3 round-trips into 1. **BC** to FairLock storage layout.
9. **Pool & reuse lock value generation** — `ThreadLocalRandom` + 20-byte direct write to a pre-sized `char[]`, or use `UUID.randomUUID()`. Non-breaking.
10. **MultiLock as a single scripted operation per node**, with explicit acquisition order to prevent deadlocks. **BC** to MultiLock semantics if previously allowed partial holds.
11. **Decouple `RedisDriver` SPI from Jedis/Lettuce specifics** with an async-first SPI; sync drivers wrap async. **BC** for anyone implementing custom drivers.

### P3 — Benchmark hygiene (do before re-measuring)
12. ~~Fix M1-M5 above. Add JMH-style warmup-discard, per-run JSON output, statistical confidence intervals. Add a "non-fair lock" benchmark group separate from "fair lock".~~ **DONE**
13. ~~Add a fair comparison of RW-lock readers vs readers and writers vs writers.~~ **DONE (reader/writer columns now split; see §6.3/§7.3)**

## 5. Suggested first iteration

Smallest scope that unblocks the rest and yields the biggest gain:
- ~~**P3 (12)** — fix the title/percentile/RWLock-pairing bugs so we measure correctly.~~ **DONE**
- ~~**P0 (1)** — parallelize `MultiNodeStrategy` I/O; this alone likely fixes the 36x distributed-lock gap without any API change.~~ **DONE — closed p99 gap, not throughput; bottleneck is now polling wait strategy.**
- ~~**Re-run all 5 benchmarks** and re-evaluate before committing to BC changes (2, 3, 4, 7, 8, 10, 11).~~ **DONE — see §6 and §7.**


## 6. Re-measurement after P0-1 + P3 hygiene (2026-06-15)

Same config as before (5 clients × 1 min × 50ms work × 3 nodes), now with parallel `MultiNodeStrategy`, true warmup-discard, JSON output, and 95% CI on per-client throughput.

### 6.1 Distributed lock

| Metric | redisson | r4j-singlenode | r4j-3node | spring-int | shedlock | redpulsar |
|---|---|---|---|---|---|---|
| Ops/s | 18.22 | 18.33 | 0.38 | 20.29 | 18.94 | 21.87 |
| Avg wait | 229ms | 407ms | **166ms** | 200ms | 230ms | 187ms |
| p99 | 1.49s | 3.14s | **407ms** | 5.69s | 3.60s | 7.77s |
| max | 1.89s | 3.27s | **407ms** | 15.9s | 5.13s | 17.5s |

- 3-node throughput effectively unchanged (0.50 -> 0.38 ops/s). Parallelization removed per-attempt RTT cost but the bottleneck is the 50ms polling retry under 5-way contention, not I/O fan-out.
- 3-node tail latency dropped dramatically: p99 went from "uncomparable / variable" to **407ms — lowest p99 in the field** (3.6× better than redisson, 14× better than spring-integration).
- Single-node redlock4j p99 (3.14s) is now the worst — needs investigation; possibly polling-strategy retry storm or watchdog absence.

### 6.2 MultiLock

| Metric | redisson | r4j-singlenode | r4j-multilock |
|---|---|---|---|
| Ops/s | 17.05 | 16.97 | **17.72** |
| Avg wait | 273ms | 303ms | **237ms** |
| p99 | 1.19s | 1.71s | **1.06s** |
| max | 1.44s | 2.09s | 1.49s |

- redlock4j-multilock now leads on throughput AND tail latency. The prior "12× worse p99 vs redisson" gap is closed (1.06s vs 1.19s — slightly better than redisson).

### 6.3 RWLock (10 clients = 5 readers + 5 writers)

| Metric | redisson-rd | r4j-sn-rd | r4j-mn-rd | redisson-wr | r4j-sn-wr | r4j-mn-wr |
|---|---|---|---|---|---|---|
| Ops/s | **153.62** | 59.07 | 53.59 | 0.07 | 17.26 | **15.51** |
| Avg wait | 0.94ms | 86ms | 99ms | **27,000ms** | 61ms | 71ms |
| p99 | 3.1ms | 452ms | 432ms | 27.0s | 91ms | **73ms** |

- Reader-side: redisson dominates at 154 ops/s vs redlock4j 53-59 — ~3× gap remains; root cause is redlock4j writer-blocking semantics in reader path (every reader still touches the mode key sequentially).
- **Writer-side: redisson is starved (0.07 ops/s, 27s wait) — only 2 successful writes in 60s.** redlock4j-multinode delivers 15.5 ops/s with 73ms p99 (370× better p99, 220× better throughput). This is a major redlock4j win that was hidden by the prior collapsed-row reporting (M3).

### 6.4 Semaphore

| Metric | redisson | r4j-singlenode | r4j-multinode |
|---|---|---|---|
| Ops/s | 54.71 | **91.09** | 87.50 |
| Avg wait | 38ms | **0.83ms** | 1.84ms |
| p99 | 386ms | 2.2ms | **4.2ms** |

- redlock4j wins both single-node (1.7×) and multi-node (1.6×) vs redisson on throughput, with 90-180× better p99 latency. No change in ranking from prior run; parallelization gave multi-node a small boost vs prior (was ~85 ops/s).

### 6.5 CountDownLatch (now with real percentiles)

| Metric | redisson | r4j-singlenode | r4j-multinode |
|---|---|---|---|
| Ops/s | 59.02 | 58.19 | **59.91** |
| p50 | 17.2ms | 16.2ms | **15.3ms** |
| p99 | 22.6ms | 19.9ms | **19.9ms** |
| max | 40.8ms | 31.0ms | 115.8ms |

- All three within 3% on throughput. redlock4j slightly leads on median and p99. M2 fix (percentiles wired in) now visible.

### 6.6 Updated gap matrix (post-A1 backoff)

| Primitive | Prior gap | Current status |
|---|---|---|
| Distributed lock (3-node) throughput | 58× slower (0.38 ops/s) | **~27× slower** (0.81 ops/s) — backoff halved the cliff; A3 (pub/sub-on-release) is the next unlock. |
| Distributed lock (3-node) p99 | uncomparable | **Best in class** (858 ms vs redisson 1,287 ms) |
| MultiLock p99 | 12× worse | **Best in class** (1.06 s) |
| RWLock reader throughput | 3× behind redisson | ~1.6× behind (95.4 vs 151.7 ops/s) — backoff added +77 %; further closure needs A2 Lua reader path. |
| RWLock writer throughput | hidden by M3 | **78× better than redisson** (16.42 vs 0.21 ops/s — redisson starves writers) |
| Semaphore | already winning | Still winning, marginal improvement |
| CountDownLatch | parity | Parity, marginal lead |

### 6.7 What to do next

The headline 36× distributed-lock gap is no longer an I/O parallelization problem — it's a **polling wait strategy under contention** problem. The next move depends on appetite for breaking changes:

1. **P0-4 Pub/Sub-on-release wait strategy** (high impact, depends on P0-3 release script): would let waiters block on a per-key channel instead of polling every 50ms. Should close the 50× distributed-lock-3node throughput gap.
2. **P0-3 Lua scripts** (BC to `RedisDriver` SPI): prerequisite for P0-4 and would directly help RWLock reader gap (3× vs redisson) and FairLock once unblocked.
3. **P1-5 Exponential backoff with jitter** (non-BC, cheap): would help reduce the 50ms polling-retry storm without changing the wait-strategy contract.
4. ~~**FairLock Jedis 7 incompatibility** (pre-existing): FairLockBenchmarkMain fails with `NoClassDefFoundError: redis/clients/jedis/RedisClient` — Jedis 7 removed that class. Either pin a different Jedis API in the FairLock client or drop the affected client; not in the original P0-P2 list.~~ **DONE — Jedis version aligned to 7.4.1 in benchmark module (2026-06-16); FairLock now runs all 4 impls with polling strategy (§7.6).**

Recommended order: **P1-5 (cheap, non-BC) -> P0-3 (BC, foundational) -> P0-4 (BC, payoff)**.


## 7. Consolidated overview after P0-1, P3 hygiene, FairLock polling fix (2026-06-16)

All runs: 3-node Redis (testcontainers), 1 min measurement, 30 s warmup-discard, 50 ms work simulation, lock timeout 30 s, 95 % CI on per-client mean. FairLock now uses `.usePolling()` to bypass keyspace-notification overhead.

### 7.1 Distributed Lock (5 clients, post-A1 backoff)

| Impl | Ops/s | Succ % | Mean Wait | p99 (ms) | Notes |
|---|---:|---:|---:|---:|---|
| **redpulsar** | **21.63** | 100% | 302 ms | 13,022 | throughput leader |
| spring-integration | 19.47 | 100% | 273 ms | 9,981 | |
| shedlock-lettuce | 18.86 | 100% | 217 ms | 3,022 | |
| redlock4j-singlenode | 18.33 | 100% | 251 ms | 1,977 | |
| redisson | 18.24 | 100% | 229 ms | 1,287 | |
| **redlock4j-3node** | **0.81** | 91% | 259 ms | **858** | **best p99** / throughput still capped |

3-node redlock4j now leads p99 (858 ms — ~1.5× better than redisson) and doubled throughput (0.38 → 0.81) after A1. Remaining throughput gap requires A3 (pub/sub-on-release) to eliminate polling entirely.

### 7.2 MultiLock (5 clients)

| Impl | Ops/s | Mean Wait | p99 (ms) |
|---|---:|---:|---:|
| **redlock4j-multilock** | **17.72** | **237 ms** | **1,058** |
| redisson | 17.05 | 273 ms | 1,192 |
| redlock4j-singlenode | 16.97 | 303 ms | 1,709 |

redlock4j wins every axis.

### 7.3 ReadWriteLock (10 clients, reader+writer split, post-A1 backoff)

| Impl | Reader ops/s | Reader p99 (µs) | Writer ops/s | Writer p99 (µs) |
|---|---:|---:|---:|---:|
| **redisson** | **151.65** | **5,823** | 0.21 | 16,820,725 (starved) |
| redlock4j-rwlock | 95.41 | 348,441 | **16.42** | **104,200** |
| redlock4j-singlenode | 74.66 | 340,626 | 15.63 | 755,568 |

Redisson still leads read-only (~1.6×, down from 2.6× pre-backoff) but starves writers (6 successes / 60 s). redlock4j-rwlock now beats single-node on both reader throughput (+28 %) and writer p99 (~7× better). Redisson reader p99 microsecond-scale is from in-process semaphore counting; redlock4j numbers are dominated by Redis round-trips (one per acquire).

### 7.4 Semaphore (5 clients)

| Impl | Ops/s | Mean Wait | p99 (µs) |
|---|---:|---:|---:|
| **redlock4j-singlenode** | **91.09** | **0.83 ms** | 2,210 |
| redlock4j | 87.50 | 1.84 ms | 4,199 |
| redisson | 54.71 | 37.87 ms | 386,491 |

redlock4j ~67 % faster than Redisson; p99 ~100× lower.

### 7.5 CountDownLatch (5 clients, 1 waiter)

| Impl | Ops/s | Mean Wait | p99 (µs) |
|---|---:|---:|---:|
| **redlock4j** | **59.91** | **15.18 ms** | 19,893 |
| redisson | 59.02 | 16.91 ms | 22,568 |
| redlock4j-singlenode | 58.19 | 15.67 ms | 19,866 |

Parity with a slight edge to redlock4j multinode.

### 7.6 FairLock (5 clients, polling wait strategy)

| Impl | Ops/s | Mean Wait | p99 (µs) |
|---|---:|---:|---:|
| **redisson** | **16.95** | 249 ms | 229,468 |
| redlock4j-jedis | 11.98 | 363 ms | 445,089 |
| redlock4j-singlenode | 11.92 | 365 ms | 471,050 |
| redlock4j-lettuce | 11.68 | 373 ms | 476,082 |

Switching from keyspace-notifications to polling moved redlock4j FairLock from ~0 ops/s to ~70 % of Redisson's throughput with passing correctness/FIFO checks.

### 7.7 Standings (post-A1 backoff)

| Suite | Leader | redlock4j position |
|---|---|---|
| DistributedLock (single-node) | redpulsar 21.63 | 0.85× (15 % slower) |
| DistributedLock (3-node) | redpulsar 21.63 | **0.037× throughput — still capped; A3 required.** **Best p99 (858 ms vs redisson 1,287 ms)** |
| MultiLock | **redlock4j 17.72** | **leader** |
| RWLock readers | redisson 151.65 | 0.63× (was 0.35× pre-backoff) |
| RWLock writers | **redlock4j-rwlock 16.42** | **leader (redisson starved at 0.21 ops/s)** |
| Semaphore | **redlock4j-singlenode 91.09** | **leader (~1.7× redisson)** |
| CountDownLatch | **redlock4j 59.91** | **leader** |
| FairLock | redisson 16.95 | 0.71× |

redlock4j leads 4 of 7 categories and is best-in-class on p99 for 5 of 7. Remaining throughput gaps (DistributedLock 3-node, RWLock reader, FairLock) all trace to the same root cause: **the polling wait strategy itself**. A1 (backoff) absorbed the biggest cliff in DistributedLock 3-node and RWLock reader; A3 (pub/sub-on-release) is the next lever for the rest.

### 7.8 A1 impact: exponential backoff with jitter (2026-06-16)

Configuration applied to 3-node redlock4j benchmark clients: `retryDelay=50ms`, `maxRetryDelay=500ms`, `retryDelayMultiplier=2.0`, `retryDelayJitterRatio=0.5`. Same workload as §7.1/§7.3/§7.6 (5 clients, 1 min, 30 s warmup, 50 ms work, polling wait strategy).

| Suite | Metric | Baseline (fixed 50 ms) | With backoff | Δ |
|---|---|---:|---:|---:|
| DistributedLock 3-node | Ops/s | 0.38 | **0.81** | **+113 %** |
| DistributedLock 3-node | Success rate | 82 % | **91 %** | +9 pp |
| DistributedLock 3-node | Mean wait | 166 ms | 259 ms | +56 % (still <p99) |
| RWLock reader (redlock4j-rwlock-reader) | Ops/s | ~54 | **95.41** | **+77 %** |
| RWLock reader | Mean wait | 113 ms | 30.42 ms | **−73 %** |
| RWLock writer (redlock4j-rwlock-writer) | Ops/s | 16.42 | 16.42 | flat (already leader) |
| FairLock (redlock4j-lettuce / -jedis) | Ops/s | 11.37 / 11.60 | **2.87 / 2.95** | **−75 % (regression — reverted)** |

**FairLock regression analysis**: backoff actively hurts FairLock because the head-of-queue client must wake on every release event; growing the inter-poll delay causes it to miss the holder's release window. Mean wait blew up from 384 ms → 1685 ms. FairLock clients were reverted to fixed `retryDelay=50ms`; backoff is only beneficial for non-fair/contention-based primitives. This reinforces task **C1** (FairLock head-of-queue rework via Lua) and suggests the head-of-queue waiter should opt out of backoff once C1 is in place.

A1 closes ~half of the DistributedLock 3-node throughput collapse and nearly closes the RWLock reader gap on its own — at zero infrastructure cost (no Lua, no pub/sub). A3 (pub/sub-on-release) is still required to close the remainder.

## 8. Task list (re-prioritized 2026-06-16)

Re-ordered by current measured impact and cost; original P0–P2 numbering preserved in parentheses for traceability.

### Tier A — High impact, attack the 50 ms polling bottleneck

- [x] ~~**A1 (P1-5) Exponential backoff with jitter in `PollingWaitStrategy`**~~ **DONE — see §7.8**
  - Replace fixed 50 ms retry with `min(maxDelay, base * 2^attempt) ± jitter`.
  - Configurable via `RedlockConfiguration` (additive, **non-BC**).
  - Measured impact: DistributedLock 3-node +113 %, RWLock reader +77 %. **FairLock regressed −75 %** (reverted; reinforces C1).

- [ ] **A2 (P0-3) Single Lua script per node for SETNX-with-reentry / atomic release-with-publish / RWLock mode transitions**
  - **BC** to `RedisDriver` SPI — add `eval`/scripted ops.
  - Prerequisite for A3 (pub/sub-on-release) and for A4 (hash-based reentrancy).
  - Expected impact: directly improves RWLock reader gap (§7.3) and reduces per-attempt round-trips for FairLock; enables atomic release+publish for A3.

- [ ] **A3 (P0-4) Pub/Sub-on-release wait strategy** *(depends on A2)*
  - Release script publishes on per-key channel; waiters `SUBSCRIBE` instead of polling.
  - Replaces `KeyspaceWaitStrategy` default; keep keyspace as fallback.
  - Expected impact: closes the 3-node DistributedLock throughput gap (the headline ~58× deficit) and FairLock gap.
  - Mostly internal; non-BC unless config flag renames are required.

### Tier B — Async core & feature parity (foundational)

- [ ] **B1 (P0-2) Move `AsyncRedlockImpl` to a real async core**
  - `acquireLock` returns `CompletionStage`; sync `Redlock` becomes a thin `.toCompletableFuture().get()` wrapper.
  - **BC** to `RedisDriver` SPI (new async methods).
  - Foundation for B2 and removes the "async wrapper is fake" issue in `AsyncRedlockImpl.attemptLock`.

- [ ] **B2 (P2-11) Decouple `RedisDriver` SPI from Jedis/Lettuce; async-first SPI; sync drivers wrap async**
  - **BC** for anyone implementing custom drivers.
  - Cleanup that flows naturally from B1; also gives Lettuce path real pipelining.

- [ ] **B3 (P1-6) Lock watchdog / auto-renewal scheduled per held lock**
  - Opt-out via config; lets users set short TTLs (1–5 s) safely.
  - Additive API on `RedlockManager`/`Redlock` (**non-BC**).
  - Side-effect investigation: likely reduces single-node p99 from 3.14 s (§6.1) which is currently the worst of the field.

- [ ] **B4 (P1-7) Hash-based reentrancy & cross-thread holders on Redis side** *(depends on A2)*
  - Redisson-style `HSET lock {clientId:threadId} count`.
  - **BC** to lock value semantics and release scripts; migration via config flag.

### Tier C — Targeted optimizations

- [ ] **C1 (P2-8) Drop FairLock's quorum vote on `isAtFrontOfQueue`** *(depends on A2)*
  - Single authoritative queue node or Lua-script atomic "am-I-head AND acquire".
  - 3 RTT → 1 RTT per FairLock iteration.
  - **BC** to FairLock storage layout.
  - Expected impact: closes remaining FairLock gap vs Redisson (§7.6).

- [ ] **C2 (P2-10) MultiLock as a single scripted operation per node, with explicit acquisition order** *(depends on A2)*
  - **BC** to MultiLock semantics if previously allowed partial holds.
  - Current MultiLock is already the §7.2 leader on throughput AND p99; priority lowered to **correctness/atomicity hardening** rather than perf.

- [ ] **C3 Investigate redlock4j-singlenode DistributedLock p99 = 1.98 s** *(§7.1)*
  - Still worst p99 in field despite competitive throughput (post-A1 backoff brought it down from 3.14 s).
  - Likely cause: residual polling-retry storm or absence of watchdog (overlap with A3, B3).
  - Cheap diagnostic: enable per-attempt timing log, identify whether the ~2 s comes from one stuck attempt or a long retry sequence.

- [ ] **C4 (P2-9) Pool / inline lock-value generation**
  - Replace `SecureRandom.nextBytes(20) + String.format("%02x", b)` per attempt with `UUID.randomUUID()` or a `ThreadLocalRandom`-backed pre-sized buffer.
  - **Non-BC**; micro-optimization. Defer until macro bottlenecks (A1–A3) are resolved; re-measure first.

### Tier D — Benchmark suite improvements

- [ ] **D1 Add contention-sweep benchmark** (varying client count 1/2/5/10/20)
  - Current single data point at 5 clients hides scaling characteristics.
  - Would expose whether the 3-node throughput gap is constant-overhead or scales with contention.

- [ ] **D2 Add throughput vs latency trade-off chart**
  - Generate from existing JSON output (§6.5 mentions JSON is now written).
  - Useful for comparing wait-strategy options once A1/A3 land.

- [ ] **D3 Optional: re-run FairLock with keyspace-notifications post A3**
  - Once A3 ships a real pub/sub wait strategy, the keyspace-notifications path can be re-evaluated or removed entirely.

### Done (from prior tiers)

- [x] ~~P0-1 Parallelize multi-node I/O in `MultiNodeStrategy`~~ — p99 best-in-class; throughput unchanged (moved bottleneck).
- [x] ~~A1 (P1-5) Exponential backoff with jitter~~ — §7.8: DistributedLock 3-node +113 %, RWLock reader +77 %.
- [x] ~~P3-12 Benchmark hygiene (warmup-discard, JSON, 95 % CI, separate fair/non-fair runs)~~
- [x] ~~P3-13 RW-lock reader/writer pairwise comparison~~
- [x] ~~FairLock Jedis 7 incompatibility + switch to polling baseline~~

### Suggested execution order

1. **A1** (cheap, non-BC, immediate measurable win)
2. **C3** (diagnostic, informs A1/B3 tuning)
3. **A2** then **A3** (foundational BC + payoff)
4. **B1** → **B2** → **B3** → **B4** (async core + features)
5. **C1**, **C2**, **C4** (targeted polish)
6. **D1**, **D2**, **D3** (suite improvements; can run in parallel)

