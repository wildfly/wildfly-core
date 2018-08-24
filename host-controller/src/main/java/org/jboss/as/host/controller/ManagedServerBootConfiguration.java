/*
 *
 *  * JBoss, Home of Professional Open Source.
 *  * Copyright 2013, Red Hat, Inc., and individual contributors
 *  * as indicated by the @author tags. See the copyright.txt file in the
 *  * distribution for a full listing of individual contributors.
 *  *
 *  * This is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU Lesser General Public License as
 *  * published by the Free Software Foundation; either version 2.1 of
 *  * the License, or (at your option) any later version.
 *  *
 *  * This software is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  * Lesser General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Lesser General Public
 *  * License along with this software; if not, write to the Free
 *  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 */

package org.jboss.as.host.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.dmr.ModelNode;

/**
 * The managed server boot configuration.
 */
public interface ManagedServerBootConfiguration {
    /**
     * Gets the environment variables that should be provided to the launched server process.
     *
     * @return the launch environment. Will not return {@code null}
     */
    Map<String, String> getServerLaunchEnvironment();

    /**
     * Get server launch command, in a form suitable for constructing a {@link ProcessBuilder}.
     *
     * @return the launch command. Will not return {@code null}
     */
    List<String> getServerLaunchCommand();

    /**
     * Gets the system properties that will be set on the launched server process as part
     * of the {@link #getServerLaunchCommand() launch command}.
     *
     * @return the properties. Will not return {@code null}
     */
    Map<String, String> getServerLaunchProperties();

    /**
     * Compares the launch command that would be produced by this object to the one
     * that would be produced by {@code other}. Useful as a means of comparing configurations.
     *
     * @param other the other {@code ManagedServerBootConfiguration}. Cannot be {@code null}
     * @return {@code true} if both objects would produce the same launch command
     */
    boolean compareServerLaunchCommand(ManagedServerBootConfiguration other);

    /**
     * Get the host controller environment.
     *
     * @return the host controller environment
     */
    HostControllerEnvironment getHostControllerEnvironment();

    /**
     * Get whether the native management remoting connector should use the endpoint set up by
     */
    boolean isManagementSubsystemEndpoint();

    /**
     * Get the subsystem endpoint configuration, in case we use the subsystem. This will be a
     * resolved model node with no unresolved expressions. The model will not, however, store defaults.
     *
     * @return the subsystem endpoint config
     */
    ModelNode getSubsystemEndpointConfiguration();

    /**
     * Get a {@link java.io.Serializable} {@link Supplier<SSLContext>} that the server will use to create an SSLContext for connecting
     * back to the HostController.
     *
     * @return A {@link Supplier<SSLContext>} or {@code null} if no SSL configuration specified.
     */
    Supplier<SSLContext> getSSLContextSupplier();

    boolean isSuspended();

    /**
     * Gets a semi-unique id for the server process.
     * @return the id
     */
    int getServerProcessId();
}
