/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
