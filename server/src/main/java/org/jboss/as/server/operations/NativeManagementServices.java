package org.jboss.as.server.operations;

import org.jboss.as.controller.remote.ModelControllerClientOperationHandlerFactoryService;
import org.jboss.as.remoting.EndpointService;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.ServerService;
import org.jboss.as.server.Services;
import org.jboss.as.server.mgmt.ManagementWorkerService;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.xnio.OptionMap;

/**
 * Utility class that installs remoting services needed by both the native and HTTP upgrade
 * based connector.
 *
 * @author Stuart Douglas
 */
class NativeManagementServices {

    private static final OptionMap OPTIONS = OptionMap.EMPTY;

    static synchronized void installRemotingServicesIfNotInstalled(final ServiceTarget serviceTarget,
                                                                   final String hostName,
                                                                   final ServiceRegistry serviceContainer) {

        if (serviceContainer.getService(ManagementRemotingServices.MANAGEMENT_ENDPOINT) == null) {

            //install xnio mgmt worker
            ManagementWorkerService.installService(serviceTarget);


            ManagementChannelRegistryService.addService(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT);

            ManagementRemotingServices.installRemotingManagementEndpoint(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT, hostName, EndpointService.EndpointType.MANAGEMENT, OPTIONS);


            ManagementRemotingServices.installManagementChannelServices(serviceTarget,
                    ManagementRemotingServices.MANAGEMENT_ENDPOINT,
                    new ModelControllerClientOperationHandlerFactoryService(),
                    Services.JBOSS_SERVER_CONTROLLER,
                    ManagementRemotingServices.MANAGEMENT_CHANNEL,
                    Services.JBOSS_SERVER_EXECUTOR,
                    ServerService.JBOSS_SERVER_SCHEDULED_EXECUTOR);

        }
    }
}
