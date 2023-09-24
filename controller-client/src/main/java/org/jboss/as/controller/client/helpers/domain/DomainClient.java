/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import javax.security.auth.callback.CallbackHandler;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.impl.DomainClientImpl;

/**
 * Client interface used to interact with the domain management infrastructure.  THis interface allows clients to get
 * information about the domain as well as apply updates to the domain.
 *
 * @author John Bailey
 */
public interface DomainClient extends ModelControllerClient {

    /**
     * Gets the list of currently running host controllers.
     *
     * @return the names of the host controllers. Will not be <code>null</code>
     */
    List<String> getHostControllerNames();

    /**
     * Add the content for a deployment to the domain controller. Note that
     * {@link #getDeploymentManager() the DomainDeploymentManager offers a
     * more convenient API for manipulating domain deployments.
     *
     * @param stream the data stream for the deployment
     * @return the unique hash for the deployment
     */
    byte[] addDeploymentContent(InputStream stream);

    /**
     * Gets a {@link DomainDeploymentManager} that provides a convenience API
     * for manipulating domain deployments.
     *
     * @return the deployment manager. Will not be {@code null}
     */
    DomainDeploymentManager getDeploymentManager();

    /**
     * Gets a list of all servers known to the domain, along with their current
     * {@link ServerStatus status}. Servers associated with host controllers that
     * are currently off line will not be included.
     *
     * @return the servers and their current status. Will not be <code>null</code>
     */
    Map<ServerIdentity, ServerStatus> getServerStatuses();

    /**
     * Starts the given server. Ignored if the server is not stopped.
     *
     * @param hostControllerName the name of the host controller responsible for the server
     * @param serverName the name of the server
     *
     * @return the status of the server following the start. Will not be <code>null</code>
     */
    ServerStatus startServer(String hostControllerName, String serverName);

    /**
     * Stops the given server.
     *
     * @param hostControllerName the name of the host controller responsible for the server
     * @param serverName the name of the server
     * @param gracefulShutdownTimeout maximum period to wait to allow the server
     *           to gracefully handle long running tasks before shutting down,
     *           or {@code -1} to shutdown immediately
     * @param timeUnit time unit in which {@code gracefulShutdownTimeout} is expressed
     *
     * @return the status of the server following the stop. Will not be <code>null</code>
     */
    ServerStatus stopServer(String hostControllerName, String serverName, long gracefulShutdownTimeout, TimeUnit timeUnit);

    /**
     * Restarts the given server.
     *
     * @param hostControllerName the name of the host controller responsible for the server
     * @param serverName the name of the server
     * @param gracefulShutdownTimeout maximum period to wait to allow the server
     *           to gracefully handle long running tasks before shutting down,
     *           or {@code -1} to shutdown immediately
     * @param timeUnit time unit in which {@code gracefulShutdownTimeout} is expressed
     *
     * @return the status of the server following the restart. Will not be <code>null</code>
     */
    ServerStatus restartServer(String hostControllerName, String serverName, long gracefulShutdownTimeout, TimeUnit timeUnit);

    /**
     * Factory used to create an {@link org.jboss.as.controller.client.helpers.domain.DomainClient} instance for a remote address
     * and port.
     */
    class Factory {
        /**
         * Create an {@link org.jboss.as.controller.client.helpers.domain.DomainClient} instance for a remote address and port.
         *
         * @param address The remote address to connect to
         * @param port The remote port
         * @return A domain client
         */
        public static DomainClient create(final InetAddress address, int port) {
            return new DomainClientImpl(address, port);
        }


        /**
         * Create an {@link org.jboss.as.controller.client.helpers.domain.DomainClient} instance for a remote address and port.
         *
         * @param address The remote address to connect to
         * @param port The remote port
         * @param handler CallbackHandler to prompt for authentication requirements.
         * @return A domain client
         */
        public static DomainClient create(final InetAddress address, int port, CallbackHandler handler) {
            return new DomainClientImpl(address, port, handler);
        }

        /**
         * Create an {@link org.jboss.as.controller.client.helpers.domain.DomainClient} instance for a remote address and port.
         *
         * @param protocol The protocol to use
         * @param address The remote address to connect to
         * @param port    The remote port
         * @return A domain client
         */
        public static DomainClient create(String protocol, final InetAddress address, int port) {
            return new DomainClientImpl(protocol, address, port);
        }


        /**
         * Create an {@link org.jboss.as.controller.client.helpers.domain.DomainClient} instance for a remote address and port.
         *
         * @param protocol The protocol to use
         * @param address The remote address to connect to
         * @param port    The remote port
         * @param handler CallbackHandler to prompt for authentication requirements.
         * @return A domain client
         */
        public static DomainClient create(String protocol, final InetAddress address, int port, CallbackHandler handler) {
            return new DomainClientImpl(protocol, address, port, handler);
        }

        /**
         * Create an {@link org.jboss.as.controller.client.helpers.domain.DomainClient} instance based on an existing
         * {@link ModelControllerClient}.
         *
         * @param client the client
         * @return A domain domain
         */
        public static DomainClient create(final ModelControllerClient client) {
            return new DomainClientImpl(client);
        }

    }
}
