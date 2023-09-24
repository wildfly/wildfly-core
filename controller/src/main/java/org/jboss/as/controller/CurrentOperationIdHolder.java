/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

/**
 * A {@linkplain ThreadLocal} based operation-id holder.
 *
 * @author Emanuel Muckenhuber
 * @deprecated internal usage only
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public final class CurrentOperationIdHolder {

    private static final ThreadLocal<Integer> currentOperationID = new ThreadLocal<>();

    /**
     * Get the current operation-id.
     *
     * @return the current operation-id, {@code null} if not available
     */
    public static Integer getCurrentOperationID() {
        return currentOperationID.get();
    }

    protected static void setCurrentOperationID(final Integer value) {
        currentOperationID.set(value);
    }

}
