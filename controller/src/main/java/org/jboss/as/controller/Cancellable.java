/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

/**
 * A handle for a specific cancellable running operation.
 */
public interface Cancellable {

    /**
     * Attempt to cancel this operation.
     *
     * @return <tt>false</tt> if the task could not be cancelled;
     * <tt>true</tt> otherwise
     */
    boolean cancel();

    /**
     * An empty implementation which does nothing when a cancel is requested.
     */
    Cancellable NULL = new Cancellable() {
        public boolean cancel() {
            return false;
        }
    };
}
