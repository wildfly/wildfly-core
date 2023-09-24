/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.beans.PropertyChangeListener;

/**
 * Allows callers to check the current {@link ControlledProcessState.State} of the process
 * and to register for notifications of state changes.
 *
 * @author Brian Stansberry (c) 2019 Red Hat Inc.
 */
public interface ProcessStateNotifier {

    /**
     * Gets the current state of the controlled process.
     * @return the current state. Will not be {@code null}
     */
    ControlledProcessState.State getCurrentState();

    /**
     * Register a listener for changes in the current state. The listener will be notified
     * with a {@code PropertyChangeEvent} whose property name will be {@code currentState}.
     * If <code>listener</code> is null, no exception is thrown and no action
     * is taken.
     *
     * @param listener the listener
     */
    void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * Remove a listener for changes in the current state.
     * If <code>listener</code> was added more than once,
     * it will be notified one less time after being removed.
     * If <code>listener</code> is null, or was never added, no exception is
     * thrown and no action is taken.
     *
     * @param listener the listener
     */
    void removePropertyChangeListener(PropertyChangeListener listener);
}
