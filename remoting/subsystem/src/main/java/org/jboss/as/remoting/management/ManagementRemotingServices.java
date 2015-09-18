/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.remoting.management;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.msc.service.ServiceController.Mode.ACTIVE;
import static org.jboss.msc.service.ServiceController.Mode.ON_DEMAND;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.remote.AbstractModelControllerOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ModelControllerClientOperationHandlerFactoryService;
import org.jboss.as.protocol.mgmt.support.ManagementChannelInitialization;
import org.jboss.as.remoting.RemotingServices;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

/**
 * Utility class to add remoting services
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:kkhan@redhat.com">Kabir Khan</a>
 */
public final class ManagementRemotingServices extends RemotingServices {
    private ManagementRemotingServices() {
    }

    /** The name of the endpoint service used for management */
    public static final ServiceName MANAGEMENT_ENDPOINT = RemotingServices.REMOTING_BASE.append("endpoint", "management");
    public static final ServiceName SHUTDOWN_EXECUTOR_NAME = MANAGEMENT_ENDPOINT.append("shutdown", "executor");

    /** The name of the external management channel */
    public static final String MANAGEMENT_CHANNEL = "management";

    /** The name of the channel used between slave and master DCs */
    public static final String DOMAIN_CHANNEL = "domain";

    /** The name of the channel used for Server to HC comms */
    public static final String SERVER_CHANNEL = "server";

    public static final String MANAGEMENT_CONNECTOR = "management";
    public static final String HTTP_CONNECTOR = "http-management";
    public static final String HTTPS_CONNECTOR = "https-management";

    /**
     * Installs a remoting stream server for a domain instance
     *  @param serviceTarget the service target to install the services into
     * @param endpointName the name of the endpoint to install the stream server into
     * @param networkInterfaceBinding the network interface binding
     * @param port the port
     * @param securityRealm the security real name
     * @param options the remoting options
     */
    public static void installDomainConnectorServices(final ServiceTarget serviceTarget,
                                                      final ServiceName endpointName,
                                                      final ServiceName networkInterfaceBinding,
                                                      final int port,
                                                      final String securityRealm,
                                                      final OptionMap options) {
        ServiceName serverCallbackService = ServiceName.JBOSS.append("host", "controller", "server-inventory", "callback");
        ServiceName tmpDirPath = ServiceName.JBOSS.append("server", "path", "jboss.domain.temp.dir");
        installSecurityServices(serviceTarget, MANAGEMENT_CONNECTOR, securityRealm, serverCallbackService, tmpDirPath);
        installConnectorServicesForNetworkInterfaceBinding(serviceTarget, endpointName, MANAGEMENT_CONNECTOR, networkInterfaceBinding, port, options);
    }

    /**
     * Set up the services to create a channel listener. This assumes that an endpoint service called {@code endpointName} exists.
     *  @param serviceTarget the service target to install the services into
     * @param endpointName the name of the endpoint to install a channel listener into
     * @param channelName the name of the channel
     * @param operationHandlerName the name of the operation handler to handle request for this channel
     * @param options the remoting options
     * @param onDemand whether to install the services on demand
     */
    public static void installManagementChannelOpenListenerService(
            final ServiceTarget serviceTarget,
            final ServiceName endpointName,
            final String channelName,
            final ServiceName operationHandlerName,
            final OptionMap options,
            final boolean onDemand) {

        final ManagementChannelOpenListenerService channelOpenListenerService = new ManagementChannelOpenListenerService(channelName, options);
        final ServiceBuilder<?> builder = serviceTarget.addService(channelOpenListenerService.getServiceName(endpointName), channelOpenListenerService)
                .addDependency(endpointName, Endpoint.class, channelOpenListenerService.getEndpointInjector())
                .addDependency(operationHandlerName, ManagementChannelInitialization.class, channelOpenListenerService.getOperationHandlerInjector())
                .addDependency(ManagementChannelRegistryService.SERVICE_NAME, ManagementChannelRegistryService.class, channelOpenListenerService.getRegistry())
                .addDependency(SHUTDOWN_EXECUTOR_NAME, ExecutorService.class, channelOpenListenerService.getExecutorServiceInjectedValue())
                .setInitialMode(onDemand ? ON_DEMAND : ACTIVE);

        builder.install();
    }

    public static void removeManagementChannelOpenListenerService(final OperationContext context, final ServiceName endpointName, final String channelName) {
        context.removeService(RemotingServices.channelServiceName(endpointName, channelName));
    }

    /**
     * Set up the services to create a channel listener and operation handler service.
     * @param serviceTarget the service target to install the services into
     * @param endpointName the endpoint name to install the services into
     * @param channelName the name of the channel
     * @param executorServiceName service name of the executor service to use in the operation handler service
     * @param scheduledExecutorServiceName  service name of the scheduled executor service to use in the operation handler service
     */
    public static void installManagementChannelServices(
            final ServiceTarget serviceTarget,
            final ServiceName endpointName,
            final AbstractModelControllerOperationHandlerFactoryService operationHandlerService,
            final ServiceName modelControllerName,
            final String channelName,
            final ServiceName executorServiceName,
            final ServiceName scheduledExecutorServiceName) {

        final OptionMap options = OptionMap.EMPTY;
        final ServiceName operationHandlerName = endpointName.append(channelName).append(ModelControllerClientOperationHandlerFactoryService.OPERATION_HANDLER_NAME_SUFFIX);

        serviceTarget.addService(operationHandlerName, operationHandlerService)
            .addDependency(modelControllerName, ModelController.class, operationHandlerService.getModelControllerInjector())
            .addDependency(executorServiceName, ExecutorService.class, operationHandlerService.getExecutorInjector())
            .addDependency(scheduledExecutorServiceName, ScheduledExecutorService.class, operationHandlerService.getScheduledExecutorInjector())
            .setInitialMode(ACTIVE)
            .install();

        installManagementChannelOpenListenerService(serviceTarget, endpointName, channelName, operationHandlerName, options, false);
    }

    public static void removeManagementChannelServices(final OperationContext context, final ServiceName endpointName,
                                                       final String channelName) {
        removeManagementChannelOpenListenerService(context, endpointName, channelName);
        final ServiceName operationHandlerName = endpointName.append(channelName).append(ModelControllerClientOperationHandlerFactoryService.OPERATION_HANDLER_NAME_SUFFIX);
        context.removeService(operationHandlerName);
    }

    private static final String USE_MGMT_ENDPOINT = "use-management-endpoint";

    /**
     * Manual check because introducing a capability can't be done without a full refactoring.
     * This has to go as soon as the management interfaces are redesigned.
     * @param context the OperationContext
     * @param otherManagementEndpoint : the address to check that may provide an exposed jboss-remoting endpoint.
     * @throws OperationFailedException in case we can't remove the management resource.
     */
    public static void isManagementResourceRemoveable(OperationContext context, PathAddress otherManagementEndpoint) throws OperationFailedException {
        ModelNode remotingConnector;
        try {
            remotingConnector = context.readResourceFromRoot(
                    PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "jmx"), PathElement.pathElement("remoting-connector", "jmx")), false).getModel();
        } catch (NoSuchElementException ex) {
            return;
        }
        if (!remotingConnector.hasDefined(USE_MGMT_ENDPOINT) ||
                (remotingConnector.hasDefined(USE_MGMT_ENDPOINT) && context.resolveExpressions(remotingConnector.get(USE_MGMT_ENDPOINT)).asBoolean(true))) {
            try {
                context.readResourceFromRoot(otherManagementEndpoint, false);
            } catch (NoSuchElementException ex) {
                throw RemotingLogger.ROOT_LOGGER.couldNotRemoveResource(context.getCurrentAddress());
            }
        }
    }
}
