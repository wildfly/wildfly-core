/*
Copyright 2015 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.wildfly.core.embedded;

import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Base interface for an embedded process that can be managed with a {@link ModelControllerClient}.
 *
 * @author Thomas.Diesler@jboss.com
 * @author Brian Stansberry
 */
public interface EmbeddedManagedProcess {

    /**
     * Gets the client that can be used to manage the embedded process.
     *
     * @return the client, or {@code null} if the process is not started
     */
    ModelControllerClient getModelControllerClient();

    /**
     * Start the embedded process.
     *
     * @throws EmbeddedProcessStartException
     */
    void start() throws EmbeddedProcessStartException;

    /**
     * Stop the embedded process.
     */
    void stop();

    /**
     * Returns the current process state of this managed process.
     * <p>
     * The returned value is a String representation of one of the possible {@code ControlledProcessState.State} values.
     *
     * @return The current process state, or {@code null} if currently the process state is unknown.
     * @throws UnsupportedOperationException if the requested operation is not supported by the implementation of this embedded server.
     */
    String getProcessState();

    /**
     * Check if the implementation of this interface is able to use getProcessState() to retrieve the current process state.
     * <p>
     * The implementation class could be an implementation comming from an older server version that does not support to check
     * the process state directly.
     *
     * @return Whether the implementation supports to query for the process state.
     */
    boolean canQueryProcessState();

}
