/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH_CAPABILITY;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;

/**
 * Service containing the paths for a HC/DC
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class HostPathManagerService extends PathManagerService {

    public static ServiceController<?> addService(ServiceTarget serviceTarget, HostPathManagerService service, HostControllerEnvironment hostEnvironment) {
        ServiceBuilder<?> serviceBuilder = serviceTarget.addService(AbstractControllerService.PATH_MANAGER_CAPABILITY.getCapabilityServiceName(), service).addAliases(SERVICE_NAME);

        // Add resources and capabilities for the always-present paths
        service.addHardcodedAbsolutePath(serviceTarget, HostControllerEnvironment.HOME_DIR, hostEnvironment.getHomeDir().getAbsolutePath());
        service.addHardcodedAbsolutePath(serviceTarget, HostControllerEnvironment.DOMAIN_BASE_DIR, hostEnvironment.getDomainBaseDir().getAbsolutePath());
        service.addHardcodedAbsolutePath(serviceTarget, HostControllerEnvironment.DOMAIN_CONFIG_DIR, hostEnvironment.getDomainConfigurationDir().getAbsolutePath());
        service.addHardcodedAbsolutePath(serviceTarget, HostControllerEnvironment.DOMAIN_DATA_DIR, hostEnvironment.getDomainDataDir().getAbsolutePath());
        service.addHardcodedAbsolutePath(serviceTarget, HostControllerEnvironment.DOMAIN_LOG_DIR, hostEnvironment.getDomainLogDir().getAbsolutePath());
        service.addHardcodedAbsolutePath(serviceTarget, HostControllerEnvironment.DOMAIN_TEMP_DIR, hostEnvironment.getDomainTempDir().getAbsolutePath());
        service.addHardcodedAbsolutePath(serviceTarget, HostControllerEnvironment.CONTROLLER_TEMP_DIR, hostEnvironment.getDomainTempDir().getAbsolutePath());

        // Registering the actual standard server path capabilities so server config resources can reference them
        //TODO look if those registrations could be moved to ServerService/DomainModelControllerService.initModel
        registerServerPathCapability(service.localCapRegRef, ServerEnvironment.SERVER_BASE_DIR);
        registerServerPathCapability(service.localCapRegRef, ServerEnvironment.SERVER_CONFIG_DIR);
        registerServerPathCapability(service.localCapRegRef, ServerEnvironment.SERVER_DATA_DIR);
        registerServerPathCapability(service.localCapRegRef, ServerEnvironment.SERVER_LOG_DIR);
        registerServerPathCapability(service.localCapRegRef, ServerEnvironment.SERVER_TEMP_DIR);

        return serviceBuilder.install();
    }

    private static void registerServerPathCapability(CapabilityRegistry capabilityRegistry, String path) {
        capabilityRegistry.registerCapability(
                new RuntimeCapabilityRegistration(PATH_CAPABILITY.fromBaseCapability(path), CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));

    }
    private final CapabilityRegistry localCapRegRef;

    public HostPathManagerService(CapabilityRegistry capabilityRegistry) {
        super(capabilityRegistry);
        this.localCapRegRef = capabilityRegistry;
    }
}
