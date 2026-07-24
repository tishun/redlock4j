/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.driver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RedisDriverException}.
 */
@Tag("unit")
public class RedisDriverExceptionTest {

    @Test
    public void testMessageOnlyConstructor() {
        RedisDriverException exception = new RedisDriverException("boom");

        assertEquals("boom", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    public void testMessageAndCauseConstructor() {
        Throwable cause = new IllegalStateException("underlying");
        RedisDriverException exception = new RedisDriverException("wrapped", cause);

        assertEquals("wrapped", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    public void testCauseOnlyConstructor() {
        Throwable cause = new IllegalStateException("underlying");
        RedisDriverException exception = new RedisDriverException(cause);

        assertSame(cause, exception.getCause());
        // Exception(Throwable) uses cause.toString() as the message
        assertTrue(exception.getMessage().contains("underlying"));
    }

    @Test
    public void testIsCheckedException() {
        assertTrue(new RedisDriverException("x") instanceof Exception);
    }
}
