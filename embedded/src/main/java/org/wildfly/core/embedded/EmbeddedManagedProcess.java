/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
     * The returned value is a String representation of one of the possible {@code EmbeddedProcessState} values.
     *
     * @return The current process state, or {@code null} if currently the process state is unknown.
     * @throws UnsupportedOperationException if the requested operation is not supported by the implementation of this embedded server.
     */
    String getProcessState();

    /**
     * Check if the implementation of this interface is able to use getProcessState() to retrieve the current process state.
     * <p>
     * The implementation class could be an implementation coming from an older server version that does not support checking
     * the process state directly.
     *
     * @return Whether the implementation supports querying for the process state.
     */
    boolean canQueryProcessState();

}
