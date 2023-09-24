/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.service;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.ServicesAttachment;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceActivator;

/**
 * Deployment processor that adds required dependencies for executing service activators.
 *
 * @author John Bailey
 */
public class ServiceActivatorDependencyProcessor implements DeploymentUnitProcessor {

    private static final ModuleDependency MSC_DEP = new ModuleDependency(Module.getBootModuleLoader(), ModuleIdentifier.create("org.jboss.msc"), false, false, false, false);

    /**
     * Add the dependencies if the deployment contains a service activator loader entry.
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final ResourceRoot deploymentRoot = phaseContext.getDeploymentUnit().getAttachment(Attachments.DEPLOYMENT_ROOT);
        final ModuleSpecification moduleSpecification = phaseContext.getDeploymentUnit().getAttachment(
                Attachments.MODULE_SPECIFICATION);
        if(deploymentRoot == null)
            return;
        final ServicesAttachment servicesAttachments = phaseContext.getDeploymentUnit().getAttachment(Attachments.SERVICES);
        if(servicesAttachments != null && !servicesAttachments.getServiceImplementations(ServiceActivator.class.getName()).isEmpty()) {
            moduleSpecification.addSystemDependency(MSC_DEP);
        }
    }

}
