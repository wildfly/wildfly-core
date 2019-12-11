package org.jboss.as.server.operations;


import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.remote.AbstractModelControllerOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ModelControllerClientOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ModelControllerOperationHandlerFactory;
import org.jboss.as.remoting.EndpointService;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.ServerService;
import org.jboss.as.server.Services;
import org.jboss.as.server.mgmt.ManagementWorkerService;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.xnio.OptionMap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility class that installs remoting services needed by both the native and HTTP upgrade
 * based connector.
 *
 * @author Stuart Douglas
 */
class NativeManagementServices {

    private static final OptionMap OPTIONS = OptionMap.EMPTY;


    static synchronized void installManagementWorkerService(final ServiceTarget serviceTarget, final ServiceRegistry serviceContainer) {
        //install xnio mgmt worker
        if (serviceContainer.getService(ManagementWorkerService.SERVICE_NAME) == null) {
            ManagementWorkerService.installService(serviceTarget);
        }
    }

    static synchronized void installRemotingServicesIfNotInstalled(final ServiceTarget serviceTarget,
                                                                   final String hostName,
                                                                   final ServiceRegistry serviceContainer) {

        if (serviceContainer.getService(ManagementRemotingServices.MANAGEMENT_ENDPOINT) == null) {

            ManagementChannelRegistryService.addService(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT);

            ManagementRemotingServices.installRemotingManagementEndpoint(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT, hostName, EndpointService.EndpointType.MANAGEMENT, OPTIONS);


            ManagementRemotingServices.installManagementChannelServices(serviceTarget,
                    ManagementRemotingServices.MANAGEMENT_ENDPOINT,
                    new ModelControllerOperationHandlerFactory() {
                        @Override
                        public AbstractModelControllerOperationHandlerFactoryService newInstance(
                                final Consumer<AbstractModelControllerOperationHandlerFactoryService> serviceConsumer,
                                final Supplier<ModelController> modelControllerSupplier,
                                final Supplier<ExecutorService> executorSupplier,
                                final Supplier<ScheduledExecutorService> scheduledExecutorSupplier) {
                            return new ModelControllerClientOperationHandlerFactoryService(serviceConsumer, modelControllerSupplier, executorSupplier, scheduledExecutorSupplier);
                        }
                    },
                    Services.JBOSS_SERVER_CONTROLLER,
                    ManagementRemotingServices.MANAGEMENT_CHANNEL,
                    ServerService.EXECUTOR_CAPABILITY.getCapabilityServiceName(),
                    ServerService.JBOSS_SERVER_SCHEDULED_EXECUTOR);

        }
    }
}
