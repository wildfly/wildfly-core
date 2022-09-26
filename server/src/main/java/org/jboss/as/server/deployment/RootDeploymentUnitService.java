/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deployment;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deployment.annotation.AnnotationIndexSupport;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayIndex;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.vfs.VirtualFile;

/**
 * The top-level service corresponding to a deployment unit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class RootDeploymentUnitService extends AbstractDeploymentUnitService {
    private final Supplier<DeploymentMountProvider> serverDeploymentRepositorySupplier;
    private final Supplier<PathManager> pathManagerSupplier;
    private final Supplier<VirtualFile> contentsSupplier;
    private final String name;
    private final String managementName;
    private final DeploymentUnit parent;
    private final DeploymentOverlayIndex deploymentOverlays;
    private final WeakReference<AnnotationIndexSupport> annotationIndexSupport;
    private final boolean isExplodedContent;

    /**
     * Construct a new instance.
     *  @param name the deployment unit simple name
     * @param managementName the deployment's domain-wide unique name
     * @param parent the parent deployment unit
     * @param registration the registration
     * @param mutableRegistration the mutable registration
     * @param resource the model
     * @param capabilityServiceSupport support for capability integration
     * @param deploymentOverlays the deployment overlays
     * @param annotationIndexSupport operation-scoped cache of static module annotation indexes
     * @param exploded the deployment has been exploded
     */
    public RootDeploymentUnitService(final Consumer<DeploymentUnit> deploymentUnitConsumer,
                                     final Supplier<DeploymentMountProvider> serverDeploymentRepositorySupplier,
                                     final Supplier<PathManager> pathManagerSupplier,
                                     final Supplier<VirtualFile> contentsSupplier,
                                     final String name, final String managementName, final DeploymentUnit parent,
                                     final ImmutableManagementResourceRegistration registration, final ManagementResourceRegistration mutableRegistration,
                                     final Resource resource, final CapabilityServiceSupport capabilityServiceSupport,
                                     final DeploymentOverlayIndex deploymentOverlays,
                                     final AnnotationIndexSupport annotationIndexSupport,
                                     final boolean exploded) {
        super(deploymentUnitConsumer, registration, mutableRegistration, resource, capabilityServiceSupport);
        assert name != null : "name is null";
        this.serverDeploymentRepositorySupplier = serverDeploymentRepositorySupplier;
        this.pathManagerSupplier = pathManagerSupplier;
        this.contentsSupplier = contentsSupplier;
        this.name = name;
        this.managementName = managementName;
        this.parent = parent;
        this.deploymentOverlays = deploymentOverlays;
        // Store the AnnotationIndexSupport in a weak reference. The OperationContext whose operations resulted in creation
        // of this object will hold a strong ref. Once that OperationContext is gc'd this weak ref
        // can be collected, preventing holding the possibly large indices in memory after completion
        // of the related deployment operations.
        this.annotationIndexSupport = new WeakReference<>(annotationIndexSupport);
        this.isExplodedContent = exploded;
    }

    protected DeploymentUnit createAndInitializeDeploymentUnit(final ServiceRegistry registry) {
        final DeploymentUnit deploymentUnit = new DeploymentUnitImpl(parent, name, registry);
        deploymentUnit.putAttachment(Attachments.MANAGEMENT_NAME, managementName);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_CONTENTS, contentsSupplier.get());
        deploymentUnit.putAttachment(DeploymentResourceSupport.REGISTRATION_ATTACHMENT, registration);
        deploymentUnit.putAttachment(DeploymentResourceSupport.MUTABLE_REGISTRATION_ATTACHMENT, mutableRegistration);
        deploymentUnit.putAttachment(DeploymentResourceSupport.DEPLOYMENT_RESOURCE, resource);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_RESOURCE_SUPPORT, new DeploymentResourceSupport(deploymentUnit));
        deploymentUnit.putAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT, capabilityServiceSupport);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_OVERLAY_INDEX, deploymentOverlays);
        deploymentUnit.putAttachment(Attachments.PATH_MANAGER, pathManagerSupplier.get());
        deploymentUnit.putAttachment(Attachments.ANNOTATION_INDEX_SUPPORT, annotationIndexSupport);
        if(this.isExplodedContent) {
            MountExplodedMarker.setMountExploded(deploymentUnit);
        }

        // Attach the deployment repo
        deploymentUnit.putAttachment(Attachments.SERVER_DEPLOYMENT_REPOSITORY, serverDeploymentRepositorySupplier.get());

        return deploymentUnit;
    }
}
