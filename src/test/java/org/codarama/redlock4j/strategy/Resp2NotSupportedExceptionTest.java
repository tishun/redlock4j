/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import static org.junit.jupiter.api.Assertions.*;

import org.codarama.redlock4j.RedlockException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Resp2NotSupportedException}.
 */
@Tag("unit")
public class Resp2NotSupportedExceptionTest {

    @Test
    public void testDefaultConstructorMessage() {
        Resp2NotSupportedException exception = new Resp2NotSupportedException();

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("RESP3 protocol is required"));
        assertTrue(exception.getMessage().contains("WaitStrategy.POLLING"));
    }

    @Test
    public void testConstructorWithDriverIdentifierAppendsContext() {
        Resp2NotSupportedException exception = new Resp2NotSupportedException("redis://localhost:6379");

        assertTrue(exception.getMessage().contains("RESP3 protocol is required"));
        assertTrue(exception.getMessage().contains("Driver: redis://localhost:6379"));
    }

    @Test
    public void testIsRedlockException() {
        assertTrue(new Resp2NotSupportedException() instanceof RedlockException);
    }
}
