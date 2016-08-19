/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
