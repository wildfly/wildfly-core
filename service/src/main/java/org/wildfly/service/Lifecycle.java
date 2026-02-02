/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

/**
 * Encapsulates lifecycle (i.e. start/stop) behaviour.
 * N.B. This is package protected, since it is not intended to be implemented directly.
 * @author Paul Ferraro
 */
interface Lifecycle {
    enum State { STOPPED, STARTING, STARTED, STOPPING }

    /**
     * Indicates whether or not this object is started.
     * @return true, if this object is started, false if stopped or in the process of starting.
     */
    boolean isStarted();

    /**
     * Indicates whether or not this object is stopped.
     * @return true, if this object is started, false if started or in the process of stopping.
     */
    default boolean isStopped() {
        return !this.isStarted();
    }
}
