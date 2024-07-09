/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerServiceInitialization;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.as.controller.management.Capabilities;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.installationmanager.InstallationManagerFinder;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Initializes the standalone and domain mode management resources for the installation manager. The initialization is based on
 * the ability of find a valid implementation of the installation manager in the installation manager class loader. The
 * installation manager resource is not registered for embedded servers, since it is necessary to perform a restart to apply any
 * candidate installation.
 */
public final class InstMgrInitialization implements ModelControllerServiceInitialization {

    @Override
    public void initializeStandalone(ServiceTarget target, ManagementModel managementModel, ProcessType processType) {
        if (processType == ProcessType.EMBEDDED_SERVER) {
            return;
        }

        Optional<InstallationManagerFactory> im = InstallationManagerFinder.reloadAndFind();
        if (im.isPresent()) {
            final InstallationManagerFactory imf = im.get();
            final InstMgrService imService = createImService(target, imf);
            managementModel.getRootResource().registerChild(InstMgrResourceDefinition.getPath(InstMgrConstants.TOOL_NAME), PlaceholderResource.INSTANCE);
            managementModel.getRootResourceRegistration().registerSubModel(new InstMgrResourceDefinition(imf, imService));
        }
    }

    @Override
    public void initializeDomain(ServiceTarget target, ManagementModel managementModel) {
        // Not available
    }

    @Override
    public void initializeHost(ServiceTarget target, ManagementModel managementModel, String hostName, ProcessType processType) {
        if (processType == ProcessType.EMBEDDED_HOST_CONTROLLER) {
            return;
        }

        Optional<InstallationManagerFactory> im = InstallationManagerFinder.reloadAndFind();
        if (im.isPresent()) {
            final PathElement host = PathElement.pathElement(HOST, hostName);
            final ManagementResourceRegistration hostRegistration = managementModel.getRootResourceRegistration()
                    .getSubModel(PathAddress.EMPTY_ADDRESS.append(host));
            final Resource hostResource = managementModel.getRootResource().getChild(host);
            if (hostResource == null) {
                // this is generally only the case when an embedded HC has been started with an empty config, but
                // /host=foo:add() has not yet been invoked, so we have no
                // real hostname yet.
                return;
            }
            final InstallationManagerFactory imf = im.get();
            final InstMgrService imService = createImService(target, imf);
            hostResource.registerChild(InstMgrResourceDefinition.getPath(InstMgrConstants.TOOL_NAME), PlaceholderResource.INSTANCE);
            hostRegistration.registerSubModel(new InstMgrResourceDefinition(imf, imService));
        }
    }

    private InstMgrService createImService(ServiceTarget target, InstallationManagerFactory imf) {
        ServiceName serviceName = InstMgrResourceDefinition.INSTALLATION_MANAGER_CAPABILITY.getCapabilityServiceName();
        ServiceBuilder<?> serviceBuilder = target.addService(serviceName);
        Consumer<InstMgrService> consumer = serviceBuilder.provides(serviceName);
        Supplier<PathManager> pathManagerSupplier = serviceBuilder.requires(ServiceNameFactory.resolveServiceName(PathManager.SERVICE_DESCRIPTOR));
        Supplier<Executor> executorSupplier = serviceBuilder.requires(ServiceNameFactory.resolveServiceName(Capabilities.MANAGEMENT_EXECUTOR));

        InstMgrService imService = new InstMgrService(imf, pathManagerSupplier, executorSupplier, consumer);
        serviceBuilder.setInstance(imService).setInitialMode(ServiceController.Mode.PASSIVE).install();
        return imService;
    }
}
