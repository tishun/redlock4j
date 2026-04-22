/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.async;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import org.codarama.redlock4j.LockResult;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.codarama.redlock4j.RedlockException;
import org.codarama.redlock4j.driver.RedisDriver;
import org.codarama.redlock4j.strategy.LockExecutionStrategy;
import org.codarama.redlock4j.strategy.LockExecutionStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

/**
 * Implementation supporting both AsyncRedlock and RxRedlock interfaces. Provides asynchronous CompletionStage and
 * RxJava reactive capabilities.
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class AsyncRedlockImpl implements AsyncRedlock, RxRedlock {
    private static final Logger logger = LoggerFactory.getLogger(AsyncRedlockImpl.class);

    private final String lockKey;
    private final RedlockConfiguration config;
    private final SecureRandom secureRandom;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final LockExecutionStrategy executionStrategy;

    // Shared lock state for async operations (not thread-local)
    private volatile LockStateInfo lockState;

    // RxJava subject for lock state changes
    private final BehaviorSubject<LockState> lockStateSubject = BehaviorSubject.createDefault(LockState.RELEASED);

    private static class LockStateInfo {
        final String lockValue;
        final Instant acquisitionTime;
        final Duration validityDuration;
        volatile int holdCount; // For reentrancy - volatile for thread safety

        LockStateInfo(String lockValue, Instant acquisitionTime, Duration validityDuration) {
            this.lockValue = lockValue;
            this.acquisitionTime = acquisitionTime;
            this.validityDuration = validityDuration;
            this.holdCount = 1; // Initial acquisition
        }

        boolean isValid() {
            return Instant.now().isBefore(acquisitionTime.plus(validityDuration));
        }

        Instant getExpiryTime() {
            return acquisitionTime.plus(validityDuration);
        }

        synchronized void incrementHoldCount() {
            holdCount++;
        }

        synchronized int decrementHoldCount() {
            return --holdCount;
        }
    }

    public AsyncRedlockImpl(String lockKey, List<RedisDriver> redisDrivers, RedlockConfiguration config,
            ExecutorService executorService, ScheduledExecutorService scheduledExecutorService) {
        this.lockKey = lockKey;
        this.config = config;
        this.secureRandom = new SecureRandom();
        this.executorService = executorService;
        this.scheduledExecutorService = scheduledExecutorService;
        this.executionStrategy = LockExecutionStrategyFactory.create(redisDrivers, config);
    }

    // AsyncRedlock implementation (CompletionStage)

    @Override
    public CompletionStage<Boolean> tryLockAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if already held (reentrancy)
                LockStateInfo currentState = lockState;
                if (currentState != null && currentState.isValid()) {
                    currentState.incrementHoldCount();
                    logger.debug("Reentrant async lock acquisition for {} (hold count: {})", lockKey,
                            currentState.holdCount);
                    return true;
                }

                lockStateSubject.onNext(LockState.ACQUIRING);
                LockResult result = attemptLock();
                if (result.isAcquired()) {
                    lockState = new LockStateInfo(result.getLockValue(), Instant.now(),
                            Duration.ofMillis(result.getValidityTimeMs()));
                    lockStateSubject.onNext(LockState.ACQUIRED);
                    logger.debug("Successfully acquired async lock {}", lockKey);
                    return true;
                } else {
                    lockStateSubject.onNext(LockState.FAILED);
                    return false;
                }
            } catch (Exception e) {
                lockStateSubject.onNext(LockState.FAILED);
                logger.error("Error in async tryLock for {}: {}", lockKey, e.getMessage());
                throw new CompletionException(new RedlockException("Failed to acquire lock", e));
            }
        }, executorService);
    }

    @Override
    public CompletionStage<Boolean> tryLockAsync(Duration timeout) {
        return tryLockWithRetryAsync(timeout, Instant.now(), 0);
    }

    private CompletionStage<Boolean> tryLockWithRetryAsync(Duration timeout, Instant startTime, int attempt) {
        return tryLockAsync().thenCompose(acquired -> {
            if (acquired) {
                return CompletableFuture.completedFuture(true);
            }

            // Check timeout
            if (startTime.plus(timeout).isBefore(Instant.now())) {
                return CompletableFuture.completedFuture(false);
            }

            // Check max attempts
            if (attempt >= config.getMaxRetryAttempts()) {
                return CompletableFuture.completedFuture(false);
            }

            // Schedule retry with delay
            long retryDelayMs = config.getRetryDelay().toMillis();
            long delay = retryDelayMs + ThreadLocalRandom.current().nextLong(retryDelayMs);

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            scheduledExecutorService.schedule(() -> {
                tryLockWithRetryAsync(timeout, startTime, attempt + 1).whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(result);
                    }
                });
            }, delay, TimeUnit.MILLISECONDS);

            return future;
        });
    }

    @Override
    public CompletionStage<Void> lockAsync() {
        return tryLockAsync(config.getLockAcquisitionTimeout()).thenCompose(acquired -> {
            if (acquired) {
                return CompletableFuture.completedFuture(null);
            } else {
                CompletableFuture<Void> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(
                        new RedlockException("Failed to acquire lock within timeout: " + lockKey));
                return failedFuture;
            }
        });
    }

    @Override
    public CompletionStage<Void> unlockAsync() {
        return CompletableFuture.runAsync(() -> {
            LockStateInfo state = lockState;
            if (state == null) {
                logger.warn("Attempting to unlock {} but no lock state found", lockKey);
                return;
            }

            if (!state.isValid()) {
                logger.warn("Lock {} has expired, cannot safely unlock", lockKey);
                lockState = null;
                lockStateSubject.onNext(LockState.EXPIRED);
                return;
            }

            // Handle reentrancy - only release when hold count reaches 0
            int remainingHolds = state.decrementHoldCount();
            if (remainingHolds > 0) {
                logger.debug("Reentrant async unlock for {} (remaining holds: {})", lockKey, remainingHolds);
                return;
            }

            // Final unlock - release the distributed lock
            releaseLock(state.lockValue);
            lockState = null;
            lockStateSubject.onNext(LockState.RELEASED);
            logger.debug("Successfully released async lock {}", lockKey);
        }, executorService);
    }

    // AsyncRedlockImpl implementation (RxJava)

    @Override
    public Single<Boolean> tryLockRx() {
        return Single.fromCompletionStage(tryLockAsync()).subscribeOn(Schedulers.io());
    }

    @Override
    public Single<Boolean> tryLockRx(Duration timeout) {
        return Single.fromCompletionStage(tryLockAsync(timeout)).subscribeOn(Schedulers.io());
    }

    @Override
    public Completable lockRx() {
        return Completable.fromCompletionStage(lockAsync()).subscribeOn(Schedulers.io());
    }

    @Override
    public Completable unlockRx() {
        return Completable.fromCompletionStage(unlockAsync()).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<Duration> validityObservable(Duration checkInterval) {
        return Observable.interval(checkInterval.toMillis(), TimeUnit.MILLISECONDS, Schedulers.io())
                .map(tick -> getRemainingValidityTime()).takeWhile(validity -> !validity.isZero());
    }

    @Override
    public Single<Boolean> tryLockWithRetryRx(int maxRetries, Duration retryDelay) {
        return tryLockRx().retry(maxRetries).delay(retryDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Observable<LockState> lockStateObservable() {
        return lockStateSubject.distinctUntilChanged();
    }

    @Override
    public Single<Boolean> extendRx(Duration additionalTime) {
        return Single.fromCompletionStage(extendAsync(additionalTime)).subscribeOn(Schedulers.io());
    }

    // Common methods

    @Override
    public boolean isHeldByCurrentThread() {
        LockStateInfo state = lockState;
        return state != null && state.isValid();
    }

    @Override
    public Duration getRemainingValidityTime() {
        LockStateInfo state = lockState;
        if (state == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(Instant.now(), state.getExpiryTime());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    @Override
    public String getLockKey() {
        return lockKey;
    }

    /**
     * Gets the hold count for the async lock. This indicates how many times the lock has been acquired.
     *
     * @return hold count, or 0 if not held
     */
    public int getHoldCount() {
        LockStateInfo state = lockState;
        return state != null && state.isValid() ? state.holdCount : 0;
    }

    @Override
    public CompletionStage<Boolean> extendAsync(Duration additionalTime) {
        if (additionalTime.isNegative() || additionalTime.isZero()) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Additional time must be positive"));
            return future;
        }

        return CompletableFuture.supplyAsync(() -> {
            LockStateInfo state = lockState;
            if (state == null || !state.isValid()) {
                logger.debug("Cannot extend lock {} - not held or expired", lockKey);
                return false;
            }

            Duration newExpireTime = config.getDefaultLockTimeout().plus(additionalTime);

            // Delegate to execution strategy (SingleNode or MultiNode)
            boolean extended = executionStrategy.extendLock(lockKey, state.lockValue, newExpireTime.toMillis());

            if (extended) {
                // Calculate new validity time using strategy
                long newValidityTimeMs = executionStrategy.calculateValidityTime(newExpireTime.toMillis(), 0);
                Duration newValidityDuration = Duration.ofMillis(newValidityTimeMs);
                // Update lock state with new validity time
                LockStateInfo newState = new LockStateInfo(state.lockValue, Instant.now(), newValidityDuration);
                newState.holdCount = state.holdCount; // Preserve hold count
                lockState = newState;
                logger.debug("Successfully extended async lock {} (new validity: {})", lockKey, newValidityDuration);
            } else {
                logger.debug("Failed to extend async lock {}", lockKey);
            }

            return extended;
        }, executorService);
    }

    // Private helper methods

    private LockResult attemptLock() {
        String lockValue = generateLockValue();
        // Delegate to execution strategy (SingleNode or MultiNode)
        return executionStrategy.acquireLock(lockKey, lockValue, config.getDefaultLockTimeout().toMillis());
    }

    private void releaseLock(String lockValue) {
        // Delegate to execution strategy (SingleNode or MultiNode)
        executionStrategy.releaseLock(lockKey, lockValue);
    }

    private String generateLockValue() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
