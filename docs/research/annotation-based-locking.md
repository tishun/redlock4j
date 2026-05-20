# Annotation-Based Distributed Locking Research

## Overview

This document explores what it would take to create a declarative annotation (e.g., `@DistributedLock`) that automatically wraps method execution with distributed locking, similar to how `@Transactional` works for database transactions.

## Goals

- Provide a simple, declarative way to protect methods with distributed locks
- Support both Spring Boot and framework-agnostic approaches
- Handle synchronous, asynchronous, and reactive method signatures
- Allow dynamic lock key resolution using method parameters

## Proposed Annotation Design

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    /**
     * The lock key. Supports SpEL expressions like "#{#orderId}" to 
     * reference method parameters.
     */
    String key();
    
    /**
     * Lock timeout in milliseconds (how long the lock is held in Redis).
     */
    long lockTimeoutMs() default 30000;
    
    /**
     * Wait timeout for acquiring the lock.
     */
    long waitTimeoutMs() default 10000;
    
    /**
     * Whether to use fair locking (FIFO order).
     */
    boolean fair() default false;
    
    /**
     * Lock type: SYNC, ASYNC, or REACTIVE
     */
    LockMode mode() default LockMode.SYNC;
}
```

## Required Components

### 1. The Annotation
Define the contract for lock behavior - low complexity.

### 2. SpEL/Expression Parser
Resolve dynamic keys from method arguments - medium complexity.

### 3. Aspect/Interceptor
Intercept and wrap methods with lock acquisition/release - medium-high complexity.

### 4. Configuration
Auto-configure RedlockManager from properties - medium complexity.

### 5. Async Support
Handle `CompletionStage`, `Mono`, `Flowable` return types - high complexity.

### 6. Error Handling
Graceful failures, retries, timeout handling - medium complexity.

## Implementation Approaches

### Approach A: Spring-Specific Module (Recommended for Spring users)

Create a `redlock4j-spring-boot-starter`:
- Auto-configuration for `RedlockManager`
- AOP aspect for `@DistributedLock`
- SpEL support for dynamic keys
- Integration with Spring's async handling

**Spring AOP Example:**
```java
@Aspect
@Component
public class DistributedLockAspect {
    
    @Autowired
    private RedlockManager redlockManager;
    
    @Around("@annotation(distributedLock)")
    public Object aroundLock(ProceedingJoinPoint pjp, DistributedLock distributedLock) throws Throwable {
        String lockKey = resolveKey(distributedLock.key(), pjp);
        
        Lock lock = redlockManager.getLock(lockKey);
        try {
            if (!lock.tryLock(distributedLock.waitTimeoutMs(), TimeUnit.MILLISECONDS)) {
                throw new RedlockException("Failed to acquire lock: " + lockKey);
            }
            return pjp.proceed();
        } finally {
            lock.unlock();
        }
    }
}
```

### Approach B: Framework-Agnostic (Broader reach, more effort)

Options:
- **AspectJ**: Compile-time or load-time weaving
- **ByteBuddy**: Runtime proxy generation
- **Java Dynamic Proxies**: Interfaces only

Requires users to set up an agent or build plugin.

## Example Usage

```java
@Service
public class OrderService {

    @DistributedLock(key = "order:#{#orderId}", waitTimeoutMs = 5000)
    public void processOrder(String orderId) {
        // Automatically protected by distributed lock
    }

    @DistributedLock(key = "inventory:#{#productId}", mode = LockMode.ASYNC)
    public CompletableFuture<Void> updateInventoryAsync(String productId) {
        // Async variant
    }
}
```

## Effort Estimates

| Scope | Time Estimate |
|-------|---------------|
| Basic annotation + Spring AOP (sync only) | 2-3 days |
| + SpEL key resolution | 1 day |
| + Async/reactive support | 2-3 days |
| + Spring Boot auto-configuration | 1-2 days |
| + Framework-agnostic (ByteBuddy) | 3-5 additional days |
| + Documentation & tests | 2-3 days |

## Existing Solutions (Competitive Analysis)

### Spring Ecosystem

#### 1. Spring Integration Redis - `RedisLockRegistry`

**Source:** Official Spring Project
**URL:** https://docs.spring.io/spring-integration/reference/distributed-locks.html

**Overview:**
- Part of `spring-integration-redis` module
- Implements `java.util.concurrent.locks.Lock` interface
- Provides `ExpirableLockRegistry` and `RenewableLockRegistry` interfaces
- **Does NOT provide annotation-based locking** - programmatic only

**Features:**
- Two lock modes: `SPIN_LOCK` (polling) and `PUB_SUB_LOCK` (Redis pub/sub)
- Automatic expiration (default 60 seconds)
- Lock renewal support (v7.0+)
- Custom TTL support

**Usage:**
```java
@Bean
public RedisLockRegistry redisLockRegistry(RedisConnectionFactory factory) {
    return new RedisLockRegistry(factory, "locks", 60000L);
}

// Programmatic usage only
Lock lock = registry.obtain("my-lock");
lock.lock();
try {
    // critical section
} finally {
    lock.unlock();
}
```

**Limitations:**
- No annotation support - must wrap manually
- No SpEL key resolution
- Single Redis node only (not Redlock algorithm)

---

#### 2. ShedLock

**Source:** Open Source (lukas-krecan)
**URL:** https://github.com/lukas-krecan/ShedLock

**Overview:**
- Popular library for distributed task scheduling locks
- **Annotation-based** via `@SchedulerLock`
- Designed specifically for `@Scheduled` tasks, not general-purpose methods
- Supports 25+ storage backends (Redis, JDBC, MongoDB, DynamoDB, etc.)

**Features:**
- `@SchedulerLock` annotation with `lockAtMostFor` and `lockAtLeastFor`
- Prevents duplicate execution in clustered environments
- Multiple storage providers

**Usage:**
```java
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
@Configuration
public class Config {
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory factory) {
        return new RedisLockProvider(factory, "prod");
    }
}

@Scheduled(cron = "0 0 2 * * *")
@SchedulerLock(name = "WeeklyEmailTask", lockAtLeastFor = "5m", lockAtMostFor = "15m")
public void sendWeeklyEmails() {
    // ...
}
```

**Limitations:**
- **Scheduler-focused only** - not for general method locking
- No SpEL key resolution (static lock names only)
- No wait/retry mechanism
- No reentrant lock support

---

#### 3. Redisson (with custom AOP)

**Source:** Redisson Project
**URL:** https://github.com/redisson/redisson

**Overview:**
- Full-featured Redis client with extensive distributed objects
- **No built-in annotation support** as of 2026
- PR #5240 attempted to add annotation support but was not merged
- Implements proper Redlock algorithm for multi-node clusters

**Features:**
- Reentrant, Fair, ReadWrite, Spin, MultiLock locks
- Pub/sub-based lock notification
- Lock watchdog (auto-renewal)
- Proper Redlock algorithm implementation

**Custom AOP Example (commonly implemented by users):**
```java
@Aspect
@Component
public class RedissonLockAspect {
    @Autowired
    private RedissonClient redisson;

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint pjp, DistributedLock distributedLock) {
        RLock lock = redisson.getLock(distributedLock.key());
        try {
            if (lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), TimeUnit.SECONDS)) {
                return pjp.proceed();
            }
            throw new LockException("Failed to acquire lock");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

---

#### 4. Locksmith (by riido-git)

**Source:** Open Source
**URL:** https://github.com/riido-git/locksmith
**Version:** 3.0.3 (Spring Boot 4.0+, Java 17+)

**Overview:**
- **Most feature-complete annotation-based solution**
- Spring Boot starter with full AOP integration
- Built on Redisson
- Provides locks, semaphores, and rate limiting

**Features:**
- `@DistributedLock` - exclusive method locking
- `@DistributedSemaphore` - limited concurrency
- `@RateLimit` - throughput control
- SpEL key resolution (`#{#orderId}`)
- Lock types: Reentrant, Read, Write
- Auto-renewal for long-running tasks
- Custom skip handlers
- Micrometer metrics integration
- Programmatic template API

**Usage:**
```java
@DistributedLock(key = "#{'order-' + #orderId}", waitTime = "30s")
public void processOrder(String orderId) { }

@DistributedLock(key = "data", type = LockType.READ)
public Data readData() { }

@DistributedLock(key = "long-task", autoRenew = true)
public void longRunningTask() { }
```

**Strengths:**
- Comprehensive feature set
- Well-documented with Wiki
- Active development (v3.0.3 released March 2026)
- Handles failure modes gracefully

**Limitations:**
- Requires Spring Boot 4.0+ and Java 17+
- Depends on Redisson (not pluggable)
- Uses Redisson's locking (not configurable to use Redlock algorithm)

---

#### 5. sadstool/redisson-aspect-lock

**Source:** Open Source
**URL:** https://github.com/sadstool/redisson-aspect-lock

**Overview:**
- Lightweight annotation-based locking with Redisson
- Simple `@Lock` annotation
- Configurable via YAML patterns

**Features:**
- Path-based lock naming
- Configurable wait/lease times per pattern
- Auto-configuration with Spring Boot

**Usage:**
```java
@Lock
public void lockedMethod() { }

@Lock(name = "custom-lock")
public void customLock() { }
```

**Configuration:**
```yaml
sadstool:
  lock:
    waitTime: 5000
    leaseTime: 10000
    names:
      - pattern: payment.*
        waitTime: 30000
        leaseTime: 60000
```

**Limitations:**
- Limited feature set
- No SpEL support
- No read/write locks
- Minimal documentation

---

### Quarkus Ecosystem

The Quarkus ecosystem has **significantly fewer options** for annotation-based distributed locking compared to Spring. This represents a potential opportunity for redlock4j.

---

#### 1. Quarkus Built-in `@Lock` (Local Only - NOT Distributed)

**Source:** Quarkus ArC (CDI implementation)
**URL:** https://quarkus.io/guides/cdi-reference

**Overview:**
- **NOT a distributed lock** - JVM-local only
- Uses `ReentrantReadWriteLock` internally
- Built-in interceptor binding for CDI beans
- Useful for thread-safety within a single instance

**Usage:**
```java
@ApplicationScoped
@Lock // Defaults to WRITE lock on all methods
public class SharedService {

    @Lock(Lock.Type.READ) // Override to allow concurrent reads
    public Data getData() {
        return data;
    }

    @Lock(Lock.Type.WRITE) // Exclusive access
    public void updateData(Data data) {
        this.data = data;
    }
}
```

**Important:** This is often confused with distributed locking but only provides concurrency control within a single JVM/pod.

---

#### 2. ShedLock CDI Integration

**Source:** ShedLock Project
**URL:** https://github.com/lukas-krecan/ShedLock (CDI module)

**Overview:**
- Same ShedLock library with CDI interceptor for Quarkus
- Uses `net.javacrumbs.shedlock.cdi.SchedulerLock`
- Combined with `io.quarkus.scheduler.Scheduled`
- Depends on `jakarta.enterprise.cdi-api` and `microprofile-config-api`

**Usage:**
```java
@ApplicationScoped
public class Scheduler {

    @Scheduled(every = "1h")
    @SchedulerLock(name = "hourlyTask", lockAtMostFor = "55m")
    public void hourlyTask() {
        // Only one instance runs this across the cluster
    }
}
```

**Configuration:**
```java
@ApplicationScoped
public class ShedLockConfig {
    @Produces
    @Singleton
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcLockProvider(dataSource);
    }
}
```

**Limitations:**
- **Scheduler-focused only** - not for general method locking
- No Redis provider with native Quarkus client (uses Jedis or Spring)
- No SpEL/expression-based keys

---

#### 3. quarkus-shedlock (Quarkiverse)

**Source:** Quarkiverse
**URL:** https://github.com/quarkiverse/quarkus-shedlock
**Version:** 0.0.2 (released July 2025)
**Stars:** 2

**Overview:**
- Official Quarkus extension wrapping ShedLock
- Aims for native compilation support
- Very early development stage

**Status:**
- Only 2 releases, 4 open issues
- Limited documentation
- Small community (2 stars)
- Still scheduler-focused only

---

#### 4. quarkus-redis-klock

**Source:** Open Source (kekingcn)
**URL:** https://github.com/kekingcn/quarkus-redis-klock
**Version:** 1.0-SNAPSHOT (last commit: 2020)
**Stars:** 6

**Overview:**
- **Only Quarkus-native general-purpose distributed lock extension**
- Annotation-driven with `@Klock` and `@KlockKey`
- Uses Redisson under the hood
- Supports dynamic keys from method parameters

**Usage:**
```java
@Singleton
public class ServiceA {

    @Klock
    public String hello(@KlockKey String name, @KlockKey(fieldName = "name") User user) {
        return "hello " + name;
    }
}
```

**Configuration:**
```properties
quarkus.klock=true
quarkus.klock.redis.database=12
quarkus.klock.redis.password=secret
quarkus.klock.redis.address=redis://localhost:6379
```

**Features:**
- `@Klock` annotation for method locking
- `@KlockKey` for parameter-based dynamic keys
- Object property extraction via `fieldName`
- Lock name: class + method + business keys

**Limitations:**
- ⚠️ **Appears abandoned** - no commits since 2020
- Only 4 commits total
- No native compilation support documented
- Not published to Maven Central (must build locally)
- No documentation beyond README
- Single Redis node only (no Redlock algorithm)
- No fair locks, read/write locks, or async support

---

#### 5. Redisson Quarkus Integration

**Source:** Redisson
**URL:** https://github.com/redisson/redisson/tree/master/redisson-quarkus

**Overview:**
- Official Redisson support for Quarkus
- Provides full `RedissonClient` injection
- **Programmatic only** - no annotation support
- Can use all Redisson lock types (RLock, RFairLock, RReadWriteLock)

**Usage:**
```java
@ApplicationScoped
public class MyService {

    @Inject
    RedissonClient redisson;

    public void doWork(String resourceId) {
        RLock lock = redisson.getLock("lock:" + resourceId);
        try {
            lock.lock();
            // critical section
        } finally {
            lock.unlock();
        }
    }
}
```

**Configuration (application.properties):**
```properties
quarkus.redisson.single-server-config.address=redis://localhost:6379
quarkus.redisson.single-server-config.password=secret
```

**Features:**
- Full Redisson feature set
- Native compilation support
- All lock types (Reentrant, Fair, ReadWrite, Spin, MultiLock)
- Watchdog auto-renewal

**Limitations:**
- **No annotation support** - must wrap manually
- Requires custom CDI interceptor for declarative locking
- Note: Redisson has deprecated the "RedLock" object - they now recommend RLock with replication

---

### Quarkus Ecosystem Summary

| Solution | Type | Distributed? | Annotation? | General Purpose? | Status |
|----------|------|--------------|-------------|------------------|--------|
| **Quarkus @Lock** | Built-in | ❌ Local only | ✅ | ✅ | Active |
| **ShedLock CDI** | Library | ✅ | ✅ | ❌ Scheduler only | Active |
| **quarkus-shedlock** | Extension | ✅ | ✅ | ❌ Scheduler only | Early (v0.0.2) |
| **quarkus-redis-klock** | Extension | ✅ | ✅ | ✅ | ⚠️ Abandoned (2020) |
| **Redisson Quarkus** | Library | ✅ | ❌ | ✅ | Active |

### Key Insight: Gap in Quarkus Market

**There is NO actively maintained, annotation-based, general-purpose distributed locking solution for Quarkus.**

- `quarkus-redis-klock` was the only option but appears abandoned since 2020
- ShedLock only covers scheduled tasks
- Redisson requires programmatic usage

This represents a **significant opportunity** for redlock4j to fill this gap with a `redlock4j-quarkus` extension

---

### Full Comparison Matrix

| Feature | Spring Redis | ShedLock | Redisson | Locksmith | klock (Quarkus) | redlock4j |
|---------|--------------|----------|----------|-----------|-----------------|-----------|
| **Annotation Support** | ❌ | ✅ (scheduler) | ❌ | ✅ | ✅ | ❌ (proposed) |
| **General Method Locking** | ✅ (prog.) | ❌ | ✅ (prog.) | ✅ | ✅ | ✅ (prog.) |
| **SpEL Key Resolution** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ (proposed) |
| **Redlock Algorithm** | ❌ | ❌ | ⚠️ deprecated | via Redisson | ❌ | ✅ |
| **Reentrant Locks** | ❌ | ❌ | ✅ | ✅ | ? | ✅ |
| **Read/Write Locks** | ❌ | ❌ | ✅ | ✅ | ❌ | ✅ |
| **Fair Locks** | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| **Auto-Renewal** | ✅ (v7+) | ❌ | ✅ | ✅ | ❌ | ✅ |
| **Async Support** | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| **Spring Support** | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| **Quarkus Support** | ❌ | ✅ (CDI) | ✅ | ❌ | ✅ | ❌ (proposed) |
| **Native Compilation** | N/A | partial | ✅ | N/A | ? | ❌ (proposed) |
| **Actively Maintained** | ✅ | ✅ | ✅ | ✅ | ❌ (2020) | ✅ |
| **Java Version** | 8+ | 8+ | 8+ | 17+ | 11+ | 8+ |

**Note on Redisson's Redlock:** Redisson has deprecated their `RedLock` object. They now recommend using `RLock` with Redis replication, which they claim provides similar guarantees. This is a philosophical difference from the original Redlock algorithm by Salvatore Sanfilippo.

---

## Strategic Recommendations for redlock4j

### Market Position

Based on this research, redlock4j has a unique position:

1. **True Redlock implementation** - Multi-node quorum algorithm (unlike Redisson's deprecated approach)
2. **Java 8+ compatibility** - Broader reach than Locksmith (Java 17+)
3. **Quarkus opportunity** - No maintained annotation-based solution exists
4. **Fair locks** - Unique feature not offered by Locksmith

### Recommended Approach: Multi-Module Strategy

```
redlock4j/
├── redlock4j-core/              # Current library (keep as-is)
├── redlock4j-spring-boot-starter/  # Spring Boot integration
├── redlock4j-quarkus/           # Quarkus CDI extension (future)
└── redlock4j-annotations/       # Shared annotations (optional)
```

### Phase 1: Spring Boot Starter

Create `redlock4j-spring-boot-starter` that provides:

1. **`@Redlock` annotation** for method-level locking
2. **SpEL key resolution** for dynamic lock keys
3. **Auto-configuration** from `application.yml`
4. **Multiple lock types**: standard, fair, read/write
5. **Async support** for `CompletableFuture` return types

**Differentiators from Locksmith:**
- True Redlock algorithm (multiple Redis nodes)
- Java 8+ compatibility (vs Java 17+)
- Spring Boot 2.x/3.x support (vs 4.0+ only)
- Fair lock support

### Phase 2: Quarkus Extension (High Opportunity)

Create `redlock4j-quarkus` extension:
- **Fill the gap** left by abandoned `quarkus-redis-klock`
- CDI interceptor for `@Redlock` annotation
- Native compilation support (GraalVM)
- Vert.x Redis client integration or Jedis fallback
- First-class Quarkus extension with build-time optimizations

**Why Quarkus is High Priority:**
- Zero actively maintained competition for general-purpose locking
- Growing framework adoption
- Cloud-native focus aligns with distributed systems needs

### Phase 3: Framework-Agnostic (Optional)

Use ByteBuddy or AspectJ for non-Spring/non-Quarkus:
- Works with any DI framework (Micronaut, Guice, etc.)
- Requires agent or compile-time weaving

---

## Next Steps

1. ✅ Research existing solutions (completed)
2. [ ] Decide on module structure (single module vs multi-module)
3. [ ] Define annotation API (`@Redlock`, `@RedlockKey`)
4. [ ] Implement Spring AOP aspect with SpEL resolution
5. [ ] Create auto-configuration for `RedlockManager`
6. [ ] Add async method support (`CompletableFuture`, `Mono`)
7. [ ] Create Spring Boot starter module
8. [ ] **High Priority:** Create Quarkus CDI extension
9. [ ] Documentation and examples
