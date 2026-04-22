/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.driver;

/**
 * Exception thrown when there's an error communicating with Redis through a driver.
 *
 * @since 1.0
 * @author Tihomir Mateev
 */
public class RedisDriverException extends Exception {

    /**
     * Creates a new exception with the specified message.
     *
     * @param message
     *            the detail message
     */
    public RedisDriverException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param message
     *            the detail message
     * @param cause
     *            the underlying cause
     */
    public RedisDriverException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception with the specified cause.
     *
     * @param cause
     *            the underlying cause
     */
    public RedisDriverException(Throwable cause) {
        super(cause);
    }
}
