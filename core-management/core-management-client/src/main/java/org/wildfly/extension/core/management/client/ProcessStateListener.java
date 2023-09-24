/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.core.management.client;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public interface ProcessStateListener {

    /**
     * Initialize the listener.
     * This should not throw an exception.
     * @param parameters the parameters to initialize the listener.
     */
    default void init(ProcessStateListenerInitParameters parameters) {}

    /**
     * Cleanup the listener before removing it.
     * This should not throw an exception.
     */
    default void cleanup() {}

    /**
     * Called when the runtime configuration changes.
     * @param evt the runtime configuration state change event.
     */
    default void runtimeConfigurationStateChanged(RuntimeConfigurationStateChangeEvent evt) {}

    /**
     * Called when the running state changes.
     * This will <strong>NEVER</strong> be called on a HostController.
     *
     * @param evt the running state change event.
     */
    default void runningStateChanged(RunningStateChangeEvent evt) {}
}
