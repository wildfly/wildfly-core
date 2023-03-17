/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.management;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerServiceInitialization;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstallationManagerService;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;

/**
 * {@code ServiceLoader} based controller integration factory.
 *
 * @author Emanuel Muckenhuber
 */
public final class PatchIntegrationFactory implements ModelControllerServiceInitialization {

    @Override
    public void initializeStandalone(final ServiceTarget serviceTarget, final ManagementModel managementModel, ProcessType processType) {
        initializeCoreServices(serviceTarget, managementModel.getRootResourceRegistration(), managementModel.getRootResource());
    }

    @Override
    public void initializeHost(final ServiceTarget serviceTarget, final ManagementModel managementModel, String hostName, ProcessType processType) {
        final PathElement host = PathElement.pathElement(HOST, hostName);
        final ManagementResourceRegistration hostRegistration = managementModel.getRootResourceRegistration().getSubModel(PathAddress.EMPTY_ADDRESS.append(host));
        final Resource hostResource = managementModel.getRootResource().getChild(host);
        if (hostResource == null) {
            // this is generally only the case when an embedded HC has been started with an empty config, but /host=foo:add() has not yet been invoked, so we have no
            // real hostname yet.
            return;
        }
        initializeCoreServices(serviceTarget, hostRegistration, hostResource);
    }

    protected void initializeCoreServices(final ServiceTarget serviceTarget, final ManagementResourceRegistration resourceRegistration,
                                          final Resource resource) {

        // Install the installation manager service
        final ServiceController<InstallationManager> imController = InstallationManagerService.installService(serviceTarget);

        // Register the patch resource description
        resourceRegistration.registerSubModel(PatchResourceDefinition.INSTANCE);
        // and resource
        PatchResource patchResource = new PatchResource(imController);
        resource.registerChild(PatchResourceDefinition.PATH, patchResource);
    }

    @Override
    public void initializeDomain(final ServiceTarget serviceTarget, final ManagementModel managementModel) {
        // Nothing required here
    }

}
