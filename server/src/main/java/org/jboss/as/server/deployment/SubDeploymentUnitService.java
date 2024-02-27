/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.ServiceRegistry;

import java.util.function.Consumer;

/**
 * Service responsible for installing the correct services to install a {@link DeploymentUnit}.
 *
 * @author John Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class SubDeploymentUnitService extends AbstractDeploymentUnitService {
    private final ResourceRoot deploymentRoot;
    private final DeploymentUnit parent;
    private final PathManager pathManager;

    public SubDeploymentUnitService(final Consumer<DeploymentUnit> deploymentUnitConsumer, ResourceRoot deploymentRoot, DeploymentUnit parent, ImmutableManagementResourceRegistration registration, final ManagementResourceRegistration mutableRegistration, Resource resource, CapabilityServiceSupport capabilityServiceSupport, PathManager pathManager, final String name) {
        super(deploymentUnitConsumer, registration, mutableRegistration, resource, capabilityServiceSupport, name);
        this.pathManager = pathManager;
        if (deploymentRoot == null) throw ServerLogger.ROOT_LOGGER.deploymentRootRequired();
        this.deploymentRoot = deploymentRoot;
        if (parent == null) throw ServerLogger.ROOT_LOGGER.subdeploymentsRequireParent();
        this.parent = parent;
    }

    protected DeploymentUnit createAndInitializeDeploymentUnit(ServiceRegistry registry) {
        final String deploymentName = deploymentRoot.getRootName();
        final DeploymentUnit deploymentUnit = new DeploymentUnitImpl(parent, deploymentName, registry, parent.getStability());
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_ROOT, deploymentRoot);
        deploymentUnit.putAttachment(Attachments.MODULE_SPECIFICATION, new ModuleSpecification());
        deploymentUnit.putAttachment(DeploymentResourceSupport.REGISTRATION_ATTACHMENT, registration);
        deploymentUnit.putAttachment(DeploymentResourceSupport.MUTABLE_REGISTRATION_ATTACHMENT, mutableRegistration);
        deploymentUnit.putAttachment(DeploymentResourceSupport.DEPLOYMENT_RESOURCE, resource);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_RESOURCE_SUPPORT, new DeploymentResourceSupport(deploymentUnit));
        deploymentUnit.putAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT, capabilityServiceSupport);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_OVERLAY_INDEX, parent.getAttachment(Attachments.DEPLOYMENT_OVERLAY_INDEX));
        deploymentUnit.putAttachment(Attachments.ANNOTATION_INDEX_SUPPORT, parent.getAttachment(Attachments.ANNOTATION_INDEX_SUPPORT));
        deploymentUnit.putAttachment(Attachments.PATH_MANAGER, pathManager);
        return deploymentUnit;
    }

}
