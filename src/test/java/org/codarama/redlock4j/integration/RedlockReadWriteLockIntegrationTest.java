/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.integration;

import org.codarama.redlock4j.RedlockManager;
import org.codarama.redlock4j.RedlockReadWriteLock;
import org.codarama.redlock4j.configuration.RedlockConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link RedlockReadWriteLock}.
 */
@Tag("integration")
@Testcontainers
public class RedlockReadWriteLockIntegrationTest {

    @Container
    static GenericContainer<?> redis1 = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379).withCommand("redis-server", "--appendonly", "yes");

    @Container
    static GenericContainer<?> redis2 = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379).withCommand("redis-server", "--appendonly", "yes");

    @Container
    static GenericContainer<?> redis3 = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379).withCommand("redis-server", "--appendonly", "yes");

    private static RedlockConfiguration testConfiguration;

    @BeforeAll
    static void setUp() {
        testConfiguration = RedlockConfiguration.builder().addRedisNode("localhost", redis1.getMappedPort(6379))
                .addRedisNode("localhost", redis2.getMappedPort(6379))
                .addRedisNode("localhost", redis3.getMappedPort(6379)).defaultLockTimeout(Duration.ofSeconds(30))
                .lockAcquisitionTimeout(Duration.ofSeconds(10)).retryDelay(Duration.ofMillis(50)).maxRetryAttempts(100)
                .build();
    }

    // ========== Basic Functionality ==========

    @Test
    void shouldAcquireAndReleaseReadLock() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("basic-read");
            Lock readLock = rwLock.readLock();

            assertTrue(readLock.tryLock(5, TimeUnit.SECONDS));
            readLock.unlock();
        }
    }

    @Test
    void shouldAcquireAndReleaseWriteLock() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("basic-write");
            Lock writeLock = rwLock.writeLock();

            assertTrue(writeLock.tryLock(5, TimeUnit.SECONDS));
            writeLock.unlock();
        }
    }

    // ========== Multiple Readers ==========

    @Test
    void shouldAllowMultipleConcurrentReaders() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("multi-readers");
            int readerCount = 5;
            AtomicInteger acquired = new AtomicInteger(0);
            CountDownLatch allAcquired = new CountDownLatch(readerCount);
            CountDownLatch releaseSignal = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(readerCount);

            for (int i = 0; i < readerCount; i++) {
                new Thread(() -> {
                    try {
                        Lock readLock = rwLock.readLock();
                        if (readLock.tryLock(10, TimeUnit.SECONDS)) {
                            try {
                                acquired.incrementAndGet();
                                allAcquired.countDown();
                                releaseSignal.await();
                            } finally {
                                readLock.unlock();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            assertTrue(allAcquired.await(15, TimeUnit.SECONDS), "All readers should acquire");
            assertEquals(readerCount, acquired.get(), "All readers should hold the lock simultaneously");

            releaseSignal.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }
    }

    // ========== Writer Exclusivity ==========

    @Test
    void writerShouldHaveExclusiveAccess() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("writer-exclusive");
            Lock writeLock = rwLock.writeLock();
            AtomicBoolean writerHoldsLock = new AtomicBoolean(false);
            AtomicBoolean secondWriterBlocked = new AtomicBoolean(false);
            CountDownLatch writerAcquired = new CountDownLatch(1);
            CountDownLatch secondAttempted = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);

            // First writer acquires
            assertTrue(writeLock.tryLock(5, TimeUnit.SECONDS));
            writerHoldsLock.set(true);
            writerAcquired.countDown();

            // Second writer tries to acquire (should fail quickly)
            new Thread(() -> {
                try {
                    writerAcquired.await();
                    Lock secondWrite = rwLock.writeLock();
                    boolean acquired = secondWrite.tryLock(500, TimeUnit.MILLISECONDS);
                    secondWriterBlocked.set(!acquired);
                    secondAttempted.countDown();
                    if (acquired) {
                        secondWrite.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();

            assertTrue(secondAttempted.await(5, TimeUnit.SECONDS));
            assertTrue(secondWriterBlocked.get(), "Second writer should be blocked");

            writeLock.unlock();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }
    }

    // ========== Reader/Writer Blocking ==========

    @Test
    void readerShouldBeBlockedByWriter() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("reader-blocked-by-writer");
            Lock writeLock = rwLock.writeLock();
            AtomicBoolean readerBlocked = new AtomicBoolean(false);
            CountDownLatch writerAcquired = new CountDownLatch(1);
            CountDownLatch readerAttempted = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);

            // Writer acquires first
            assertTrue(writeLock.tryLock(5, TimeUnit.SECONDS));
            writerAcquired.countDown();

            // Reader tries to acquire (should fail with short timeout)
            new Thread(() -> {
                try {
                    writerAcquired.await();
                    Lock readLock = rwLock.readLock();
                    boolean acquired = readLock.tryLock(500, TimeUnit.MILLISECONDS);
                    readerBlocked.set(!acquired);
                    readerAttempted.countDown();
                    if (acquired) {
                        readLock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();

            assertTrue(readerAttempted.await(5, TimeUnit.SECONDS));
            assertTrue(readerBlocked.get(), "Reader should be blocked by writer");

            writeLock.unlock();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void writerShouldWaitForReadersToFinish() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("writer-waits-readers");
            Lock readLock = rwLock.readLock();
            AtomicBoolean writerAcquiredAfterReaderRelease = new AtomicBoolean(false);
            CountDownLatch readerAcquired = new CountDownLatch(1);
            CountDownLatch writerStarted = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);

            // Reader acquires first
            assertTrue(readLock.tryLock(5, TimeUnit.SECONDS));
            readerAcquired.countDown();

            // Writer tries to acquire in background
            new Thread(() -> {
                try {
                    readerAcquired.await();
                    writerStarted.countDown();
                    Lock writeLock = rwLock.writeLock();
                    boolean acquired = writeLock.tryLock(10, TimeUnit.SECONDS);
                    writerAcquiredAfterReaderRelease.set(acquired);
                    if (acquired) {
                        writeLock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();

            assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(200); // Give writer time to attempt

            // Release read lock - writer should then succeed
            readLock.unlock();

            assertTrue(done.await(15, TimeUnit.SECONDS));
            assertTrue(writerAcquiredAfterReaderRelease.get(), "Writer should acquire after reader releases");
        }
    }

    // ========== Concurrent Access ==========

    @Test
    void shouldHandleConcurrentReadersAndWriters() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("concurrent-rw");
            int readerCount = 5;
            int writerCount = 3;
            int totalThreads = readerCount + writerCount;
            AtomicInteger readAcquisitions = new AtomicInteger(0);
            AtomicInteger writeAcquisitions = new AtomicInteger(0);
            CountDownLatch startSignal = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(totalThreads);

            // Start readers
            for (int i = 0; i < readerCount; i++) {
                new Thread(() -> {
                    try {
                        startSignal.await();
                        Lock readLock = rwLock.readLock();
                        if (readLock.tryLock(10, TimeUnit.SECONDS)) {
                            try {
                                readAcquisitions.incrementAndGet();
                                Thread.sleep(50);
                            } finally {
                                readLock.unlock();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            // Start writers
            for (int i = 0; i < writerCount; i++) {
                new Thread(() -> {
                    try {
                        startSignal.await();
                        Lock writeLock = rwLock.writeLock();
                        if (writeLock.tryLock(10, TimeUnit.SECONDS)) {
                            try {
                                writeAcquisitions.incrementAndGet();
                                Thread.sleep(50);
                            } finally {
                                writeLock.unlock();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            startSignal.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS));
            assertTrue(readAcquisitions.get() > 0, "Should have successful read acquisitions");
            assertTrue(writeAcquisitions.get() > 0, "Should have successful write acquisitions");
        }
    }

    // ========== Lettuce Driver ==========

    @Test
    void shouldWorkWithLettuceDriver() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withLettuce(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("lettuce-rwlock");

            Lock readLock = rwLock.readLock();
            assertTrue(readLock.tryLock(5, TimeUnit.SECONDS));
            readLock.unlock();

            Lock writeLock = rwLock.writeLock();
            assertTrue(writeLock.tryLock(5, TimeUnit.SECONDS));
            writeLock.unlock();
        }
    }

    // ========== Writer Starvation Tests ==========

    /**
     * Tests that under continuous reader load, a writer can still eventually acquire the lock. This documents the
     * current behavior - the RW lock does NOT guarantee writer preference, meaning writers CAN be starved by continuous
     * readers.
     */
    @Test
    void shouldDocumentWriterStarvationRisk() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("writer-starvation-test");
            AtomicBoolean writerAcquired = new AtomicBoolean(false);
            AtomicInteger readerAcquisitions = new AtomicInteger(0);
            AtomicBoolean stopReaders = new AtomicBoolean(false);
            CountDownLatch readersStarted = new CountDownLatch(3);
            CountDownLatch writerStarted = new CountDownLatch(1);
            CountDownLatch allDone = new CountDownLatch(4); // 3 readers + 1 writer

            // Start continuous readers
            for (int i = 0; i < 3; i++) {
                new Thread(() -> {
                    try {
                        readersStarted.countDown();
                        while (!stopReaders.get()) {
                            Lock readLock = rwLock.readLock();
                            if (readLock.tryLock(1, TimeUnit.SECONDS)) {
                                try {
                                    readerAcquisitions.incrementAndGet();
                                    Thread.sleep(20); // Hold briefly
                                } finally {
                                    readLock.unlock();
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        allDone.countDown();
                    }
                }).start();
            }

            // Wait for readers to start
            assertTrue(readersStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(100); // Let readers establish presence

            // Writer tries to acquire
            new Thread(() -> {
                try {
                    writerStarted.countDown();
                    Lock writeLock = rwLock.writeLock();
                    // Writer may or may not succeed under reader pressure
                    // Using short timeout to demonstrate the starvation risk
                    boolean acquired = writeLock.tryLock(2, TimeUnit.SECONDS);
                    writerAcquired.set(acquired);
                    if (acquired) {
                        writeLock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            }).start();

            // Wait for writer to attempt
            assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(3000); // Give writer time to try

            // Stop readers and clean up
            stopReaders.set(true);
            assertTrue(allDone.await(10, TimeUnit.SECONDS));

            // Document the actual behavior
            assertTrue(readerAcquisitions.get() > 0, "Readers should have acquired locks");

            // NOTE: Writer may or may not have acquired depending on timing.
            // This test documents that writer starvation IS possible.
            // A production system needing writer fairness should use FairLock instead.
            System.out.println("Writer starvation test: writer acquired = " + writerAcquired.get()
                    + ", reader acquisitions = " + readerAcquisitions.get());
        }
    }

    /**
     * Tests that a writer can acquire the lock when readers release in a gap between read operations.
     */
    @Test
    void writerShouldAcquireInReaderGap() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("writer-in-gap-test");
            AtomicBoolean writerAcquired = new AtomicBoolean(false);
            AtomicInteger readerAcquisitions = new AtomicInteger(0);
            CountDownLatch readerDone = new CountDownLatch(1);
            CountDownLatch writerDone = new CountDownLatch(1);

            // Reader does a few acquisitions then stops
            new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        Lock readLock = rwLock.readLock();
                        if (readLock.tryLock(5, TimeUnit.SECONDS)) {
                            try {
                                readerAcquisitions.incrementAndGet();
                                Thread.sleep(50);
                            } finally {
                                readLock.unlock();
                            }
                        }
                        Thread.sleep(100); // Gap between reads
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    readerDone.countDown();
                }
            }).start();

            // Writer waits for gap
            new Thread(() -> {
                try {
                    Thread.sleep(200); // Start after first reader acquisition
                    Lock writeLock = rwLock.writeLock();
                    // Should eventually acquire in a gap
                    boolean acquired = writeLock.tryLock(5, TimeUnit.SECONDS);
                    writerAcquired.set(acquired);
                    if (acquired) {
                        Thread.sleep(50);
                        writeLock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    writerDone.countDown();
                }
            }).start();

            assertTrue(readerDone.await(10, TimeUnit.SECONDS));
            assertTrue(writerDone.await(10, TimeUnit.SECONDS));
            assertTrue(writerAcquired.get(), "Writer should acquire during reader gap");
        }
    }

    /**
     * Tests that multiple writers waiting don't starve each other.
     */
    @Test
    void multipleWritersShouldNotStarveEachOther() throws InterruptedException {
        try (RedlockManager manager = RedlockManager.withJedis(testConfiguration)) {
            RedlockReadWriteLock rwLock = manager.createReadWriteLock("multi-writer-fairness");
            int writerCount = 3;
            AtomicInteger successfulWriters = new AtomicInteger(0);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(writerCount);

            for (int i = 0; i < writerCount; i++) {
                new Thread(() -> {
                    try {
                        startGate.await();
                        Lock writeLock = rwLock.writeLock();
                        if (writeLock.tryLock(15, TimeUnit.SECONDS)) {
                            try {
                                successfulWriters.incrementAndGet();
                                Thread.sleep(100);
                            } finally {
                                writeLock.unlock();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            startGate.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "All writers should complete");
            assertEquals(writerCount, successfulWriters.get(), "All writers should eventually acquire the lock");
        }
    }
}
