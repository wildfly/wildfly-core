/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

/**
 * Utility to track whether the server's running lock was successfully acquired.
 * This information is used by the Installation Manager to determine whether
 * Prospero can rely on advisory lock detection or must fall back to temp file checking.
 *
 * Uses a system property to share state across modules (server and process-controller).
 */
public final class RunningLockUtil {

    private static final String LOCK_ACQUIRED_PROPERTY = "org.wildfly.core.running.lock.acquired";

    private RunningLockUtil() {
        // Utility class, no instances
    }

    /**
     * Records that the running lock was successfully acquired.
     * Called by ApplicationServerService (standalone) or ProcessController (domain).
     */
    public static void setLockAcquired(boolean acquired) {
        System.setProperty(LOCK_ACQUIRED_PROPERTY, String.valueOf(acquired));
    }

    /**
     * Returns whether the running lock is currently held.
     * Used by Installation Manager to signal lock status to Prospero.
     *
     * @return true if the advisory lock was acquired, false otherwise
     */
    public static boolean isLockAcquired() {
        return Boolean.parseBoolean(System.getProperty(LOCK_ACQUIRED_PROPERTY, "false"));
    }

    /**
     * Resets lock status. Used for testing.
     */
    static void reset() {
        System.clearProperty(LOCK_ACQUIRED_PROPERTY);
    }
}
