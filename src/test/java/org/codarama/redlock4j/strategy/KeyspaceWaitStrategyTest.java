/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KeyspaceWaitStrategy helper methods.
 */
@Tag("unit")
class KeyspaceWaitStrategyTest {

    @Test
    void mergeFlags_shouldMergeWithEmptyExisting() {
        String result = KeyspaceWaitStrategy.mergeFlags("", "Kgx");
        assertTrue(result.contains("K"));
        assertTrue(result.contains("g"));
        assertTrue(result.contains("x"));
    }

    @Test
    void mergeFlags_shouldMergeWithNullExisting() {
        String result = KeyspaceWaitStrategy.mergeFlags(null, "Kgx");
        assertTrue(result.contains("K"));
        assertTrue(result.contains("g"));
        assertTrue(result.contains("x"));
    }

    @Test
    void mergeFlags_shouldPreserveExistingFlags() {
        String result = KeyspaceWaitStrategy.mergeFlags("AE", "Kgx");
        assertTrue(result.contains("A"));
        assertTrue(result.contains("E"));
        assertTrue(result.contains("K"));
        assertTrue(result.contains("g"));
        assertTrue(result.contains("x"));
    }

    @Test
    void mergeFlags_shouldNotDuplicateFlags() {
        String result = KeyspaceWaitStrategy.mergeFlags("Kg", "Kgx");
        assertEquals(1, result.chars().filter(c -> c == 'K').count());
        assertEquals(1, result.chars().filter(c -> c == 'g').count());
        assertTrue(result.contains("x"));
    }

    @Test
    void hasAllFlags_shouldReturnTrueWhenAllPresent() {
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("Kgx", "Kgx"));
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("AKEgx", "Kgx"));
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("xgK", "Kgx"));
    }

    @Test
    void hasAllFlags_shouldReturnFalseWhenMissing() {
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("Kg", "Kgx"));
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("AE", "Kgx"));
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("", "Kgx"));
        assertFalse(KeyspaceWaitStrategy.hasAllFlags(null, "Kgx"));
    }

    @Test
    void getType_shouldReturnKeyspaceNotifications() {
        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();
        assertEquals(WaitStrategy.KEYSPACE_NOTIFICATIONS, strategy.getType());
    }

    @Test
    void mergeFlags_shouldHandleEmptyRequired() {
        String result = KeyspaceWaitStrategy.mergeFlags("AE", "");
        assertEquals("AE", result);
    }

    @Test
    void mergeFlags_shouldHandleSingleCharacter() {
        String result = KeyspaceWaitStrategy.mergeFlags("A", "K");
        assertTrue(result.contains("A"));
        assertTrue(result.contains("K"));
        assertEquals(2, result.length());
    }

    @Test
    void hasAllFlags_shouldHandleEmptyRequired() {
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("Kgx", ""));
        assertTrue(KeyspaceWaitStrategy.hasAllFlags("", ""));
    }

    @Test
    void hasAllFlags_shouldBeCaseSensitive() {
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("kgx", "Kgx"));
        assertFalse(KeyspaceWaitStrategy.hasAllFlags("KGX", "Kgx"));
    }

    @Test
    void close_shouldBeIdempotent() {
        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();

        assertDoesNotThrow(() -> {
            strategy.close();
            strategy.close();
            strategy.close();
        });
    }

    @Test
    void waitForRelease_shouldReturnTrueWhenNotInitialized() throws InterruptedException {
        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();

        // Without initialization, should fall back to immediate return
        boolean result = strategy.waitForRelease("test-lock", Duration.ofMillis(100));

        assertTrue(result);
    }

    @Test
    void initialize_withEmptyDriverList_shouldThrowIllegalArgument() {
        KeyspaceWaitStrategy strategy = new KeyspaceWaitStrategy();

        // Empty driver list should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> strategy.initialize(Collections.emptyList(), Duration.ofMillis(50)));
    }
}
