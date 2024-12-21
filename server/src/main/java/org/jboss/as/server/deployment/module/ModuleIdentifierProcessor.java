/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.vfs.VirtualFile;

/**
 * Deployment processor that generates module identifiers for the deployment and attaches it.
 *
 * @author Stuart Douglas
 */
public class ModuleIdentifierProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        final DeploymentUnit parent = deploymentUnit.getParent();
        final DeploymentUnit topLevelDeployment = parent == null ? deploymentUnit : parent;
        final VirtualFile toplevelRoot = topLevelDeployment.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot();
        final ModuleIdentifier moduleIdentifier = createModuleIdentifier(deploymentUnit.getName(), deploymentRoot, topLevelDeployment, toplevelRoot, deploymentUnit.getParent() == null);
        deploymentUnit.putAttachment(Attachments.MODULE_IDENTIFIER, moduleIdentifier);
        deploymentUnit.putAttachment(Attachments.MODULE_NAME, moduleIdentifier.toString());
    }

    public static ModuleIdentifier createModuleIdentifier(final String deploymentUnitName, final ResourceRoot deploymentRoot, final DeploymentUnit topLevelDeployment, final VirtualFile toplevelRoot, final boolean topLevel) {
        // generate the module identifier for the deployment
        final ModuleIdentifier moduleIdentifier;
        if (topLevel) {
            moduleIdentifier = ModuleIdentifier.create(ServiceModuleLoader.MODULE_PREFIX + deploymentUnitName);
        } else {
            String relativePath = deploymentRoot.getRoot().getPathNameRelativeTo(toplevelRoot);
            moduleIdentifier = ModuleIdentifier.create(ServiceModuleLoader.MODULE_PREFIX + topLevelDeployment.getName() + '.' + relativePath.replace('/', '.'));
        }
        return moduleIdentifier;
    }

}
