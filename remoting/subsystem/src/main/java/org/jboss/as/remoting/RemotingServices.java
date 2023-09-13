/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.remoting;

import static org.jboss.as.remoting.RemotingSubsystemRootResource.HTTP_LISTENER_REGISTRY_CAPABILITY;
import static org.jboss.as.remoting.RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;

import io.undertow.server.ListenerRegistry;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class RemotingServices {

    /** The name of the remoting service */
    public static final ServiceName REMOTING_BASE = ServiceName.JBOSS.append("remoting");

    /**
     * The name of the endpoint service installed by the remoting subsystem.
     * @deprecated Reference remoting endpoint capability instead.
     */
    @Deprecated(forRemoval = true)
    public static final ServiceName SUBSYSTEM_ENDPOINT = REMOTING_ENDPOINT_CAPABILITY.getCapabilityServiceName(Endpoint.class);//REMOTING_BASE.append("endpoint", "subsystem");

    /** The base name of the connector services */
    private static final ServiceName CONNECTOR_BASE = REMOTING_BASE.append("connector");

    /** The base name of the stream server services */
    private static final ServiceName SERVER_BASE = REMOTING_BASE.append("server");

    public static final ServiceName HTTP_LISTENER_REGISTRY = HTTP_LISTENER_REGISTRY_CAPABILITY.getCapabilityServiceName(ListenerRegistry.class);

    /**
     * Create the service name for a connector
     *
     * @param connectorName
     *            the connector name
     * @return the service name
     */
    public static ServiceName connectorServiceName(final String connectorName) {
        return CONNECTOR_BASE.append(connectorName);
    }

    /**
     * Create the service name for a stream server
     *
     * @param connectorName
     *            the connector name
     * @return the service name
     */
    public static ServiceName serverServiceName(final String connectorName) {
        return SERVER_BASE.append(connectorName);
    }

    /**
     * Create the service name for a channel
     *
     * @param channelName
     *            the channel name
     * @return the service name
     */
    public static ServiceName channelServiceName(final ServiceName endpointName, final String channelName) {
        return endpointName.append("channel").append(channelName);
    }

    public static void installRemotingManagementEndpoint(final ServiceTarget serviceTarget, final ServiceName endpointName,
                                                         final String hostName, final EndpointService.EndpointType type) {
        installRemotingManagementEndpoint(serviceTarget, endpointName, hostName, type, OptionMap.EMPTY);
    }

    public static void installRemotingManagementEndpoint(final ServiceTarget serviceTarget, final ServiceName endpointName,
                                                         final String hostName, final EndpointService.EndpointType type, final OptionMap options) {
        final ServiceBuilder<?> builder = serviceTarget.addService(endpointName);
        final Consumer<Endpoint> endpointConsumer = builder.provides(endpointName);
        final Supplier<XnioWorker> workerSupplier = builder.requires(ServiceName.JBOSS.append("serverManagement", "controller", "management", "worker"));
        builder.setInstance(new EndpointService(endpointConsumer, workerSupplier, hostName, type, options));
        builder.install();
    }

    @Deprecated
    public static void installConnectorServicesForNetworkInterfaceBinding(final ServiceTarget serviceTarget,
                                                                          final ServiceName endpointName,
                                                                          final String connectorName,
                                                                          final ServiceName networkInterfaceBindingName,
                                                                          final int port,
                                                                          final OptionMap connectorPropertiesOptionMap,
                                                                          final ServiceName saslAuthenticationFactory,
                                                                          final ServiceName sslContext,
                                                                          final ServiceName socketBindingManager) {
        final ServiceName serviceName= serverServiceName(connectorName);
        final ServiceBuilder<?> builder = serviceTarget.addService(serviceName);
        final Consumer<AcceptingChannel<StreamConnection>> streamServerConsumer = builder.provides(serviceName);
        final Supplier<Endpoint> eSupplier = builder.requires(endpointName);
        final Supplier<SaslAuthenticationFactory> safSupplier = saslAuthenticationFactory != null ? builder.requires(saslAuthenticationFactory) : null;
        final Supplier<SSLContext> scSupplier = sslContext != null ? builder.requires(sslContext): null;
        final Supplier<SocketBindingManager> sbmSupplier = socketBindingManager != null ? builder.requires(socketBindingManager) : null;
        final Supplier<NetworkInterfaceBinding> ibSupplier = builder.requires(networkInterfaceBindingName);
        builder.setInstance(new InjectedNetworkBindingStreamServerService(streamServerConsumer,
                eSupplier, safSupplier, scSupplier, sbmSupplier, ibSupplier, connectorPropertiesOptionMap, port));
        builder.install();
    }

    public static void installConnectorServicesForSocketBinding(final ServiceTarget serviceTarget,
                                                                final ServiceName endpointName,
                                                                final String connectorName,
                                                                final ServiceName socketBindingName,
                                                                final OptionMap connectorPropertiesOptionMap,
                                                                final ServiceName saslAuthenticationFactory,
                                                                final ServiceName sslContext,
                                                                final ServiceName socketBindingManager) {
        final ServiceName serviceName = serverServiceName(connectorName);
        final ServiceBuilder<?> builder = serviceTarget.addService(serviceName);
        final Consumer<AcceptingChannel<StreamConnection>> streamServerConsumer = builder.provides(serviceName);
        final Supplier<Endpoint> eSupplier = builder.requires(endpointName);
        final Supplier<SaslAuthenticationFactory> safSupplier = saslAuthenticationFactory != null ? builder.requires(saslAuthenticationFactory) : null;
        final Supplier<SSLContext> scSupplier = sslContext != null ? builder.requires(sslContext) : null;
        final Supplier<SocketBindingManager> sbmSupplier = builder.requires(socketBindingManager);
        final Supplier<SocketBinding> sbSupplier = builder.requires(socketBindingName);
        builder.setInstance(new InjectedSocketBindingStreamServerService(streamServerConsumer,
                eSupplier, safSupplier, scSupplier, sbmSupplier, sbSupplier, connectorPropertiesOptionMap, connectorName));
        builder.install();
    }

    public static void removeConnectorServices(final OperationContext context, final String connectorName) {
        context.removeService(serverServiceName(connectorName));
    }
}
