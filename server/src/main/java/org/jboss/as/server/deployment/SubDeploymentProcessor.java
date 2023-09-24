/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.List;
import java.util.function.Consumer;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;

/**
 * Deployment processor responsible to creating deployment unit services for sub-deployment.
 *
 * @author John Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
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
            final PathManager pathManager = deploymentUnit.getAttachment(Attachments.PATH_MANAGER);
            final ServiceName serviceName = Services.deploymentUnitName(deploymentUnit.getName(), childRoot.getRootName());
            final ServiceBuilder<?> sb = serviceTarget.addService(serviceName);
            final Consumer<DeploymentUnit> deploymentUnitConsumer = sb.provides(serviceName);
            final SubDeploymentUnitService service = new SubDeploymentUnitService(deploymentUnitConsumer, childRoot, deploymentUnit, registration, mutableRegistration, resource, capabilityServiceSupport, pathManager, serviceName.getSimpleName());
            sb.setInstance(service);
            sb.install();
            phaseContext.addDeploymentDependency(serviceName, Attachments.SUB_DEPLOYMENTS);
            //we also need a dep on the first phase of the sub deployments
            phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, serviceName.append(ServiceName.of(Phase.STRUCTURE.name())));
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
