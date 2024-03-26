/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.management.BaseNativeInterfaceResourceDefinition.NATIVE_MANAGEMENT_RUNTIME_CAPABILITY;
import static org.jboss.msc.service.ServiceController.Mode.ACTIVE;
import static org.jboss.msc.service.ServiceController.Mode.ON_DEMAND;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.remote.AbstractModelControllerOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ModelControllerClientOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ModelControllerOperationHandlerFactory;
import org.jboss.as.network.SocketBindingManager;
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
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
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
    public static void installDomainConnectorServices(final OperationContext context,
                                                      final ServiceTarget serviceTarget,
                                                      final ServiceName endpointName,
                                                      final ServiceName networkInterfaceBinding,
                                                      final int port,
                                                      final OptionMap options,
                                                      final ServiceName saslAuthenticationFactory,
                                                      final ServiceName sslContext) {
        ServiceName sbmName = context.hasOptionalCapability(SocketBindingManager.SERVICE_DESCRIPTOR, NATIVE_MANAGEMENT_RUNTIME_CAPABILITY, null)
                ? context.getCapabilityServiceName(SocketBindingManager.SERVICE_DESCRIPTOR) : null;
        installConnectorServicesForNetworkInterfaceBinding(serviceTarget, endpointName, MANAGEMENT_CONNECTOR,
                networkInterfaceBinding, port, options, saslAuthenticationFactory, sslContext, sbmName);
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

        final ServiceName serviceName = RemotingServices.channelServiceName(endpointName, channelName);
        final ServiceBuilder<?> builder = serviceTarget.addService(serviceName);
        final Supplier<ManagementChannelInitialization> ohfSupplier = builder.requires(operationHandlerName);
        final Supplier<ExecutorService> esSupplier = builder.requires(SHUTDOWN_EXECUTOR_NAME);
        final Supplier<Endpoint> eSupplier = builder.requires(endpointName);
        final Supplier<ManagementChannelRegistryService> rSupplier = builder.requires(ManagementChannelRegistryService.SERVICE_NAME);
        builder.setInstance(new ManagementChannelOpenListenerService(ohfSupplier, esSupplier, eSupplier, rSupplier, channelName, options));
        builder.setInitialMode(onDemand ? ON_DEMAND : ACTIVE);
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
            final ModelControllerOperationHandlerFactory operationHandlerServiceFactory,
            final ServiceName modelControllerName,
            final String channelName,
            final ServiceName executorServiceName,
            final ServiceName scheduledExecutorServiceName) {
        final OptionMap options = OptionMap.EMPTY;
        final ServiceName operationHandlerName = endpointName.append(channelName).append(ModelControllerClientOperationHandlerFactoryService.OPERATION_HANDLER_NAME_SUFFIX);
        final ServiceBuilder<?> builder = serviceTarget.addService(operationHandlerName);
        final Consumer<AbstractModelControllerOperationHandlerFactoryService> serviceConsumer = builder.provides(operationHandlerName);
        final Supplier<ModelController> mcSupplier = builder.requires(modelControllerName);
        final Supplier<ExecutorService> eSupplier = builder.requires(executorServiceName);
        final Supplier<ScheduledExecutorService> seSupplier = builder.requires(scheduledExecutorServiceName);
        builder.setInstance(operationHandlerServiceFactory.newInstance(serviceConsumer, mcSupplier, eSupplier, seSupplier));
        builder.install();

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
        } catch (Resource.NoSuchResourceException ex) {
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
