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

import static org.jboss.msc.service.ServiceController.Mode.ACTIVE;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class RemotingServices {

    /** The name of the remoting service */
    public static final ServiceName REMOTING_BASE = ServiceName.JBOSS.append("remoting");

    /** The name of the endpoint service installed by the remoting subsystem.  */
    public static final ServiceName SUBSYSTEM_ENDPOINT = RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY.getCapabilityServiceName(Endpoint.class);//REMOTING_BASE.append("endpoint", "subsystem");

    /** The base name of the connector services */
    private static final ServiceName CONNECTOR_BASE = REMOTING_BASE.append("connector");

    /** The base name of the stream server services */
    private static final ServiceName SERVER_BASE = REMOTING_BASE.append("server");

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
        ManagementEndpointService endpointService = new ManagementEndpointService(hostName, type, options);
        serviceTarget.addService(endpointName, endpointService)
                .setInitialMode(ACTIVE)
                .install();
    }

    public static void installConnectorServicesForNetworkInterfaceBinding(ServiceTarget serviceTarget,
                                                                          final ServiceName endpointName,
                                                                          final String connectorName,
                                                                          final ServiceName networkInterfaceBindingName,
                                                                          final int port,
                                                                          final OptionMap connectorPropertiesOptionMap) {
        installConnectorServices(serviceTarget, endpointName, connectorName, networkInterfaceBindingName, port, true, connectorPropertiesOptionMap);
    }

    public static void installConnectorServicesForSocketBinding(ServiceTarget serviceTarget,
                                                                final ServiceName endpointName,
                                                                final String connectorName,
                                                                final ServiceName socketBindingName,
                                                                final OptionMap connectorPropertiesOptionMap) {
        installConnectorServices(serviceTarget, endpointName, connectorName, socketBindingName, 0, false, connectorPropertiesOptionMap);
    }

    public static void installSecurityServices(ServiceTarget serviceTarget,
                                               final String connectorName,
                                               final String realmName,
                                               final ServiceName serverCallbackServiceName,
                                               final ServiceName tmpDirService) {
        final ServiceName securityProviderName = RealmSecurityProviderService.createName(connectorName);

        final RealmSecurityProviderService rsps = new RealmSecurityProviderService();
        ServiceBuilder<?> builder = serviceTarget.addService(securityProviderName, rsps);
        if (realmName != null) {
            SecurityRealm.ServiceUtil.addDependency(builder, rsps.getSecurityRealmInjectedValue(), realmName, false);
        }
        if (serverCallbackServiceName != null) {
            builder.addDependency(serverCallbackServiceName, CallbackHandler.class, rsps.getServerCallbackValue());
        }
        builder.addDependency(tmpDirService, String.class, rsps.getTmpDirValue());
        builder.install();
    }

    private static void installConnectorServices(ServiceTarget serviceTarget,
                                                 final ServiceName endpointName,
                                                 final String connectorName,
                                                 final ServiceName bindingName,
                                                 final int port,
                                                 final boolean isNetworkInterfaceBinding,
                                                 final OptionMap connectorPropertiesOptionMap) {

        final ServiceName securityProviderName = RealmSecurityProviderService.createName(connectorName);
        if (isNetworkInterfaceBinding) {
            final InjectedNetworkBindingStreamServerService streamServerService = new InjectedNetworkBindingStreamServerService(connectorPropertiesOptionMap, port);
            serviceTarget.addService(serverServiceName(connectorName), streamServerService)
                    .addDependency(securityProviderName, RemotingSecurityProvider.class, streamServerService.getSecurityProviderInjector())
                    .addDependency(endpointName, Endpoint.class, streamServerService.getEndpointInjector())
                    .addDependency(bindingName, NetworkInterfaceBinding.class, streamServerService.getInterfaceBindingInjector())
                    .addDependency(ServiceBuilder.DependencyType.OPTIONAL, SocketBindingManager.SOCKET_BINDING_MANAGER, SocketBindingManager.class, streamServerService.getSocketBindingManagerInjector())
                    .install();
        } else {
            final InjectedSocketBindingStreamServerService streamServerService = new InjectedSocketBindingStreamServerService(connectorPropertiesOptionMap);
            serviceTarget.addService(serverServiceName(connectorName), streamServerService)
                    .addDependency(securityProviderName, RemotingSecurityProvider.class, streamServerService.getSecurityProviderInjector())
                    .addDependency(endpointName, Endpoint.class, streamServerService.getEndpointInjector())
                    .addDependency(bindingName, SocketBinding.class, streamServerService.getSocketBindingInjector())
                    .addDependency(SocketBindingManager.SOCKET_BINDING_MANAGER, SocketBindingManager.class, streamServerService.getSocketBindingManagerInjector())
                    .install();

        }
    }

    public static void removeConnectorServices(final OperationContext context, final String connectorName) {
        final ServiceName securityProviderName = RealmSecurityProviderService.createName(connectorName);
        context.removeService(serverServiceName(connectorName));
        context.removeService(securityProviderName);
    }
}
