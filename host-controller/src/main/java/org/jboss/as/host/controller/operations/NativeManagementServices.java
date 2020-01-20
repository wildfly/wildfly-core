package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.remote.AbstractModelControllerOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ModelControllerClientOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ModelControllerOperationHandlerFactory;
import org.jboss.as.host.controller.DomainModelControllerService;
import org.jboss.as.host.controller.HostControllerService;
import org.jboss.as.host.controller.mgmt.ServerToHostOperationHandlerFactoryService;
import org.jboss.as.remoting.EndpointService;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.mgmt.ManagementWorkerService;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.OptionMap;
import org.xnio.Options;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility class that installs remoting services needed by both the native and HTTP upgrade
 * based connector.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class NativeManagementServices {

    private static final int heartbeatInterval = 15000;

    private static final OptionMap SERVICE_OPTIONS = OptionMap.EMPTY;

    public static final OptionMap CONNECTION_OPTIONS = OptionMap.create(RemotingOptions.HEARTBEAT_INTERVAL, heartbeatInterval,
                                                        Options.READ_TIMEOUT, 45000);

    static synchronized void installManagementWorkerService(final ServiceTarget serviceTarget, final ServiceRegistry serviceContainer) {
        //install xnio mgmt worker
        if (serviceContainer.getService(ManagementWorkerService.SERVICE_NAME) == null) {
            ManagementWorkerService.installService(serviceTarget);
        }
    }

    static synchronized void installRemotingServicesIfNotInstalled(final ServiceTarget serviceTarget,
                                                                   final String hostName,
                                                                   final ServiceRegistry serviceContainer,
                                                                   final boolean onDemand) {

        if (serviceContainer.getService(ManagementRemotingServices.MANAGEMENT_ENDPOINT) == null) {

            ManagementChannelRegistryService.addService(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT);

            ManagementRemotingServices.installRemotingManagementEndpoint(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT,
                    hostName, EndpointService.EndpointType.MANAGEMENT, CONNECTION_OPTIONS);


            ManagementRemotingServices.installManagementChannelOpenListenerService(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT,
                    ManagementRemotingServices.SERVER_CHANNEL,
                    ServerToHostOperationHandlerFactoryService.SERVICE_NAME, SERVICE_OPTIONS, onDemand);

            ManagementRemotingServices.installManagementChannelServices(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT,
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
                    DomainModelControllerService.SERVICE_NAME, ManagementRemotingServices.MANAGEMENT_CHANNEL,
                    DomainModelControllerService.EXECUTOR_CAPABILITY.getCapabilityServiceName(), HostControllerService.HC_SCHEDULED_EXECUTOR_SERVICE_NAME);
        }
    }
}
