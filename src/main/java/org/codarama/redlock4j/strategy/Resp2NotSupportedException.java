/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.strategy;

import org.codarama.redlock4j.RedlockException;

/**
 * Exception thrown when a RESP2 connection is detected but RESP3 is required.
 *
 * <p>
 * The keyspace notifications wait strategy requires RESP3 protocol because:
 * <ul>
 * <li>RESP3 allows pub/sub push notifications on the same connection used for commands</li>
 * <li>RESP2 requires a dedicated connection for pub/sub, which is blocked in subscription mode</li>
 * </ul>
 *
 * <p>
 * To resolve this exception:
 * <ol>
 * <li>Upgrade your Redis client library to a version supporting RESP3 (Lettuce 6+, Jedis 5+)</li>
 * <li>Ensure your client is configured to use RESP3 protocol</li>
 * <li>Or use {@link WaitStrategy#POLLING} as a fallback (not recommended)</li>
 * </ol>
 *
 * @see WaitStrategy#KEYSPACE_NOTIFICATIONS
 * @since 1.0
 * @author Tihomir Mateev
 */
public class Resp2NotSupportedException extends RedlockException {

    private static final String MESSAGE = "RESP3 protocol is required for keyspace notifications. "
            + "The connection is using RESP2 which is not supported. "
            + "Please upgrade your Redis client library (Lettuce 6+, Jedis 5+) and ensure RESP3 is enabled, "
            + "or use WaitStrategy.POLLING as a fallback.";

    /**
     * Creates a new exception with the default message.
     */
    public Resp2NotSupportedException() {
        super(MESSAGE);
    }

    /**
     * Creates a new exception with additional context.
     *
     * @param driverIdentifier
     *            the identifier of the Redis driver that failed RESP3 verification
     */
    public Resp2NotSupportedException(String driverIdentifier) {
        super(MESSAGE + " Driver: " + driverIdentifier);
    }
}
