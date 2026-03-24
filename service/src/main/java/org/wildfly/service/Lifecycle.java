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
    enum State { STARTING, STARTED, STOPPING, STOPPED, CLOSED }

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

    /**
     * Indicates whether or not this object is closed.
     * @return true, if this object is closed, false otherwise.
     */
    default boolean isClosed() {
        return false;
    }

    class DecoratedLifecycle implements Lifecycle {
        private final Lifecycle lifecycle;

        DecoratedLifecycle(Lifecycle lifecycle) {
            this.lifecycle = lifecycle;
        }

        @Override
        public boolean isStarted() {
            return this.lifecycle.isStarted();
        }

        @Override
        public boolean isStopped() {
            return this.lifecycle.isStopped();
        }

        @Override
        public boolean isClosed() {
            return this.lifecycle.isClosed();
        }

        @Override
        public int hashCode() {
            return this.lifecycle.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            return this.lifecycle.equals(object);
        }

        @Override
        public String toString() {
            return this.lifecycle.toString();
        }
    }
}
