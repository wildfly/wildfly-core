package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.remote.ModelControllerClientOperationHandlerFactoryService;
import org.jboss.as.host.controller.DomainModelControllerService;
import org.jboss.as.host.controller.HostControllerService;
import org.jboss.as.host.controller.mgmt.ServerToHostOperationHandlerFactoryService;
import org.jboss.as.remoting.EndpointService;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Utility class that installs remoting services needed by both the native and HTTP upgrade
 * based connector.
 *
 * @author Stuart Douglas
 */
public class NativeManagementServices {

    private static final int heartbeatInterval = 15000;

    private static final OptionMap SERVICE_OPTIONS = OptionMap.EMPTY;

    public static final OptionMap CONNECTION_OPTIONS = OptionMap.create(RemotingOptions.HEARTBEAT_INTERVAL, heartbeatInterval,
                                                        Options.READ_TIMEOUT, 45000);

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
                    new ModelControllerClientOperationHandlerFactoryService(),
                    DomainModelControllerService.SERVICE_NAME, ManagementRemotingServices.MANAGEMENT_CHANNEL,
                    HostControllerService.HC_EXECUTOR_SERVICE_NAME, HostControllerService.HC_SCHEDULED_EXECUTOR_SERVICE_NAME);
        }
    }
}
