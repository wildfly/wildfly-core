/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.server;

import static org.jboss.as.server.security.sasl.Constants.JBOSS_DOMAIN_SERVER;
import java.io.Serializable;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.remoting.EndpointConfigFactory;
import org.jboss.as.remoting.EndpointService;
import org.jboss.as.remoting.RemotingServices;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.mgmt.ManagementWorkerService;
import org.jboss.as.server.mgmt.domain.HostControllerConnectionService;
import org.jboss.as.server.security.DomainServerCredential;
import org.jboss.as.server.security.sasl.DomainServerSaslClientFactory;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.xnio.OptionMap;

/**
 * Service activator for the communication services of a managed server in a domain.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class DomainServerCommunicationServices  implements ServiceActivator, Serializable {

    private static final OptionMap DEFAULTS = OptionMap.EMPTY;

    private static final long serialVersionUID = 1593964083902839384L;

    // Shared operation ID for connection, this will get updated for start and reload
    private static volatile int initialOperationID;

    private final ModelNode endpointConfig;
    private final URI managementURI;
    private final String serverName;
    private final String serverProcessName;
    private final String serverAuthToken;
    private final Supplier<SSLContext> sslContextSupplier;

    private final boolean managementSubsystemEndpoint;

    DomainServerCommunicationServices(ModelNode endpointConfig, URI managementURI, String serverName, String serverProcessName, String serverAuthToken, boolean managementSubsystemEndpoint, Supplier<SSLContext> sslContextSupplier) {
        this.endpointConfig = endpointConfig;
        this.managementURI = managementURI;
        this.serverName = serverName;
        this.serverProcessName = serverProcessName;
        this.serverAuthToken = serverAuthToken;
        this.managementSubsystemEndpoint = managementSubsystemEndpoint;
        this.sslContextSupplier = sslContextSupplier;
    }

    static void updateOperationID(final int operationID) {
        initialOperationID = operationID;
    }

    @Override
    public void activate(final ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        final ServiceTarget serviceTarget = serviceActivatorContext.getServiceTarget();
        final ServiceName endpointName = managementSubsystemEndpoint ? RemotingServices.SUBSYSTEM_ENDPOINT : ManagementRemotingServices.MANAGEMENT_ENDPOINT;
        final EndpointService.EndpointType endpointType = managementSubsystemEndpoint ? EndpointService.EndpointType.SUBSYSTEM : EndpointService.EndpointType.MANAGEMENT;
        try {
            ManagementWorkerService.installService(serviceTarget);
            // TODO see if we can figure out a way to work in the vault resolver instead of having to use ExpressionResolver.SIMPLE
            @SuppressWarnings("deprecation")
            final OptionMap options = EndpointConfigFactory.create(ExpressionResolver.SIMPLE, endpointConfig, DEFAULTS);
            ManagementRemotingServices.installRemotingManagementEndpoint(serviceTarget, endpointName, WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.NODE_NAME, null), endpointType, options);

            // Install the communication services
            final ServiceBuilder<?> sb = serviceTarget.addService(HostControllerConnectionService.SERVICE_NAME);
            final Supplier<ExecutorService> esSupplier = Services.requireServerExecutor(sb);
            final Supplier<ScheduledExecutorService> sesSupplier = sb.requires(ServerService.JBOSS_SERVER_SCHEDULED_EXECUTOR);
            final Supplier<Endpoint> eSupplier = sb.requires(endpointName);
            final Supplier<ProcessStateNotifier> cpsnSupplier = sb.requires(ControlledProcessStateService.INTERNAL_SERVICE_NAME);
            AuthenticationContext latestAuthenticationContext = DomainServerMain.getLatestAuthenticationContext();
            sb.setInstance(new HostControllerConnectionService(managementURI, serverName, serverProcessName,
                    latestAuthenticationContext == null ? createAuthenticationContect(serverName, serverAuthToken) : latestAuthenticationContext,
                    initialOperationID, managementSubsystemEndpoint, sslContextSupplier, esSupplier, sesSupplier, eSupplier, cpsnSupplier));
            sb.install();
        } catch (OperationFailedException e) {
            throw new ServiceRegistryException(e);
        }
    }

    /**
     * Create a new service activator for the domain server communication services.
     *
     * @param endpointConfig the endpoint configuration
     * @param managementURI the management connection URI
     * @param serverName the server name
     * @param serverProcessName the server process name
     * @param serverAuthToken the authentication token the server will use to connect back to the management interface.
     * @param managementSubsystemEndpoint whether to use the mgmt subsystem endpoint or not
     * @return the service activator
     */
    public static ServiceActivator create(final ModelNode endpointConfig, final URI managementURI, final String serverName, final String serverProcessName,
                                          final String serverAuthToken, final boolean managementSubsystemEndpoint, final Supplier<SSLContext> sslContextSupplier) {

        return new DomainServerCommunicationServices(endpointConfig, managementURI, serverName, serverProcessName, serverAuthToken, managementSubsystemEndpoint, sslContextSupplier);
    }

    /**
     * Create the {@code AuthenticationContext} that configures security for the connection back to the host controller.
     *
     * @param username - The username to use to authenticate the server.
     * @param serverAuthToken - The server auth token to use as the credential to verify the server.
     * @return A constructed {@code AuthenticationContext}
     */
    static AuthenticationContext createAuthenticationContect(final String username, final String serverAuthToken) {
        AuthenticationConfiguration authConfig = AuthenticationConfiguration.empty()
                .useName(username)
                .useCredential(new DomainServerCredential(serverAuthToken))
                .useSaslClientFactory(DomainServerSaslClientFactory.getInstance())
                .setSaslMechanismSelector(SaslMechanismSelector.fromString(JBOSS_DOMAIN_SERVER));

        AuthenticationContext authContext = AuthenticationContext.empty()
                .with(MatchRule.ALL, authConfig);

        return authContext;
    }

    public interface OperationIDUpdater {

        /**
         * Update the operation ID when connecting to the HC.
         *
         * @param operationID the new operation ID
         */
        void updateOperationID(int operationID);

    }

}
