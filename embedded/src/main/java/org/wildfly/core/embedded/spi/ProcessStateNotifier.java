/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded.spi;

import java.beans.PropertyChangeListener;

import org.jboss.msc.service.ServiceName;

/**
 * Allows callers to check the current {@link EmbeddedProcessState state} of the process
 * and to register for notifications of state changes.
 *
 * @author Brian Stansberry
 */
public interface ProcessStateNotifier {

    /** Only for use within the WildFly kernel; may change or be removed at any time */
    ServiceName SERVICE_NAME = ServiceName.parse("org.wildfly.embedded.process-state-notifier");

    /**
     * Gets the current state of the controlled process.
     * @return the current state. Will not be {@code null}
     *
     * @throws IllegalStateException if the process is not an embedded process
     */
    EmbeddedProcessState getEmbeddedProcessState();

    /**
     * Register a listener for changes in the current state. The listener will be notified
     * with a {@code PropertyChangeEvent} whose property name will be {@code embeddedState}.
     * If <code>listener</code> is null, no exception is thrown and no action
     * is taken.
     *
     * @param listener the listener
     *
     * @throws IllegalStateException if the process is not an embedded process
     */
    void addProcessStateListener(PropertyChangeListener listener);


    /**
     * Remove a listener for changes in the current state.
     * If <code>listener</code> was added more than once,
     * it will be notified one less time after being removed.
     * If <code>listener</code> is null, or was never added, no exception is
     * thrown and no action is taken.
     *
     * @param listener the listener
     *
     * @throws IllegalStateException if the process is not an embedded process
     */
    void removeProcessStateListener(PropertyChangeListener listener);
}

