/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server;


import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;

import java.io.File;

/**
 * Service containing the paths for a server
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerPathManagerService extends PathManagerService {

    public static ServiceController<?> addService(ServiceTarget serviceTarget, ServerPathManagerService service, ServerEnvironment serverEnvironment) {
        ServiceBuilder<?> serviceBuilder = serviceTarget.addService(AbstractControllerService.PATH_MANAGER_CAPABILITY.getCapabilityServiceName(), service).addAliases(SERVICE_NAME);

        // Add environment paths - registering the actual capabilities
        addAbsolutePath(service, serviceTarget, ServerEnvironment.HOME_DIR, serverEnvironment.getHomeDir());
        addAbsolutePath(service, serviceTarget, ServerEnvironment.SERVER_BASE_DIR, serverEnvironment.getServerBaseDir());
        addAbsolutePath(service, serviceTarget, ServerEnvironment.SERVER_CONFIG_DIR, serverEnvironment.getServerConfigurationDir());
        addAbsolutePath(service, serviceTarget, ServerEnvironment.SERVER_DATA_DIR, serverEnvironment.getServerDataDir());
        addAbsolutePath(service, serviceTarget, ServerEnvironment.SERVER_LOG_DIR, serverEnvironment.getServerLogDir());
        addAbsolutePath(service, serviceTarget, ServerEnvironment.SERVER_TEMP_DIR, serverEnvironment.getServerTempDir());
        addAbsolutePath(service, serviceTarget, ServerEnvironment.CONTROLLER_TEMP_DIR, serverEnvironment.getControllerTempDir());

        // Add system paths - registering the actual capabilities
        service.addHardcodedAbsolutePath(serviceTarget, "user.dir", System.getProperty("user.dir"));
        service.addHardcodedAbsolutePath(serviceTarget, "user.home", System.getProperty("user.home"));
        service.addHardcodedAbsolutePath(serviceTarget, "java.home", System.getProperty("java.home"));

        // In the domain mode add a few more paths - registering the actual capabilities
        if(serverEnvironment.getLaunchType() == ServerEnvironment.LaunchType.DOMAIN) {
            if(serverEnvironment.getDomainBaseDir() != null) {
                service.addHardcodedAbsolutePath(serviceTarget, ServerEnvironment.DOMAIN_BASE_DIR, serverEnvironment.getDomainBaseDir().getAbsolutePath());
            }
            if(serverEnvironment.getDomainConfigurationDir() != null) {
                service.addHardcodedAbsolutePath(serviceTarget, ServerEnvironment.DOMAIN_CONFIG_DIR, serverEnvironment.getDomainConfigurationDir().getAbsolutePath());
            }
        }

        return serviceBuilder.install();
    }

    public ServerPathManagerService(CapabilityRegistry capabilityRegistry) {
        super(capabilityRegistry);
    }

    private static void addAbsolutePath(ServerPathManagerService service, ServiceTarget serviceTarget, String name, File path) {
        if (path == null) {
            return;
        }

        service.addHardcodedAbsolutePath(serviceTarget, name, path.getAbsolutePath());
    }

}
