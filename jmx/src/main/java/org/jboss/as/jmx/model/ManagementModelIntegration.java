/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jmx.model;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerServiceInitialization;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Provides integration between the JMX layer and the core management layer beyond what is possible
 * via the Extension interface.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ManagementModelIntegration implements ModelControllerServiceInitialization {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("jmx", "management", "integration");

    @Override
    public void initializeStandalone(ServiceTarget target, ManagementModel managementModel, ProcessType processType, PathManager pathManager) {
        ManagementModelProvider provider =
                new ManagementModelProvider(new ResourceAndRegistration(managementModel));
        target.addService(SERVICE_NAME, provider).setInitialMode(ServiceController.Mode.ON_DEMAND).install();
    }

    @Override
    public void initializeDomain(ServiceTarget target, ManagementModel managementModel) {
        // not relevant to domain model;
    }

    @Override
    public void initializeHost(ServiceTarget target, ManagementModel managementModel, String hostName, ProcessType processType, PathManager pathManager) {
        // not relevant to host controller;
        ManagementModelProvider provider =
                new ManagementModelProvider(new ResourceAndRegistration(managementModel));
        target.addService(SERVICE_NAME, provider).setInitialMode(ServiceController.Mode.ON_DEMAND).install();
    }

    static final class ResourceAndRegistration {
        private final ManagementModel managementModel;

        private ResourceAndRegistration(final ManagementModel managementModel) {
            this.managementModel = managementModel;
        }

        Resource getResource() {
            return managementModel.getRootResource();
        }

        ImmutableManagementResourceRegistration getRegistration() {
            return managementModel.getRootResourceRegistration();
        }
    }

    public final class ManagementModelProvider implements Service<ManagementModelProvider> {
        private final ResourceAndRegistration resourceAndRegistration;

        private ManagementModelProvider(ResourceAndRegistration resourceAndRegistration) {
            this.resourceAndRegistration = resourceAndRegistration;
        }

        @Override
        public void start(StartContext startContext) throws StartException {
            // no-op
        }

        @Override
        public void stop(StopContext stopContext) {
            // no-op;
        }

        @Override
        public ManagementModelProvider getValue() throws IllegalStateException, IllegalArgumentException {
            return this;
        }

        // CRITICAL -- cannot be made protected or public!
        ResourceAndRegistration getResourceAndRegistration() {
            return resourceAndRegistration;
        }
    }
}
