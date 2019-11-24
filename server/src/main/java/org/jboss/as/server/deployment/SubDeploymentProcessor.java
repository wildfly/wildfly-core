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

import java.util.List;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deployment.module.AdditionalModuleCoordinator;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.services.security.AbstractVaultReader;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;

/**
 * Deployment processor responsible to creating deployment unit services for sub-deployment.
 *
 * @author John Bailey
 */
public class SubDeploymentProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentResourceRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);

        if(deploymentUnit.getParent() != null && ExplodedDeploymentMarker.isExplodedDeployment(deploymentUnit.getParent())) {
            if (deploymentResourceRoot.getRoot().isDirectory()) {
                ExplodedDeploymentMarker.markAsExplodedDeployment(deploymentUnit);
            }
        }

        final AdditionalModuleCoordinator additionalModuleCoordinator;
        if (deploymentUnit.getParent() == null) {
            additionalModuleCoordinator = new AdditionalModuleCoordinator(deploymentUnit);
            phaseContext.putAttachment(Attachments.ADDITIONAL_MODULES_COORDINATOR, additionalModuleCoordinator);
        } else {
            additionalModuleCoordinator = phaseContext.getAttachment(Attachments.ADDITIONAL_MODULES_COORDINATOR);
        }

        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
        final List<ResourceRoot> childRoots = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        for (final ResourceRoot childRoot : childRoots) {
            if (childRoot == deploymentResourceRoot || !SubDeploymentMarker.isSubDeployment(childRoot)) {
                continue;
            }

            final Resource resource = DeploymentResourceSupport.getOrCreateSubDeployment(childRoot.getRootName(), deploymentUnit);
            final ImmutableManagementResourceRegistration registration = deploymentUnit.getAttachment(DeploymentResourceSupport.REGISTRATION_ATTACHMENT);
            final ManagementResourceRegistration mutableRegistration =  deploymentUnit.getAttachment(DeploymentResourceSupport.MUTABLE_REGISTRATION_ATTACHMENT);
            final CapabilityServiceSupport capabilityServiceSupport = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
            final AbstractVaultReader vaultReader = deploymentUnit.getAttachment(Attachments.VAULT_READER_ATTACHMENT_KEY);
            final PathManager pathManager = deploymentUnit.getAttachment(Attachments.PATH_MANAGER);
            final SubDeploymentUnitService service = new SubDeploymentUnitService(childRoot, deploymentUnit, registration,
                    mutableRegistration, resource, capabilityServiceSupport, vaultReader, pathManager);

            final ServiceName serviceName = Services.deploymentUnitName(deploymentUnit.getName(), childRoot.getRootName());

            serviceTarget.addService(serviceName, service)
                    .addDependency(Services.JBOSS_DEPLOYMENT_CHAINS, DeployerChains.class, service.getDeployerChainsInjector())
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .install();
            phaseContext.addDeploymentDependency(serviceName, Attachments.SUB_DEPLOYMENTS);
            //we also need a dep on the first phase of the sub deployments
            phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, serviceName.append(ServiceName.of(Phase.STRUCTURE.name())));

            additionalModuleCoordinator.recordSubdeployment();
        }
    }

    @Override
    public void undeploy(final DeploymentUnit deploymentUnit) {
        final ServiceRegistry serviceRegistry = deploymentUnit.getServiceRegistry();
        final List<ResourceRoot> childRoots = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        for (final ResourceRoot childRoot : childRoots) {
            if (!SubDeploymentMarker.isSubDeployment(childRoot)) {
                continue;
            }
            final ServiceName serviceName = Services.deploymentUnitName(deploymentUnit.getName(), childRoot.getRootName());
            final ServiceController<?> serviceController = serviceRegistry.getService(serviceName);
            if (serviceController != null) {
                serviceController.setMode(ServiceController.Mode.REMOVE);
            }
        }
        deploymentUnit.removeAttachment(Attachments.SUB_DEPLOYMENTS);
    }
}
