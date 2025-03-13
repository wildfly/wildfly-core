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
        final String moduleIdentifier = createModuleIdentifierAsString(deploymentUnit.getName(), deploymentRoot, topLevelDeployment, toplevelRoot, deploymentUnit.getParent() == null);
        deploymentUnit.putAttachment(Attachments.MODULE_NAME, moduleIdentifier);
    }

    /**
     * Create a module identifier for the deployment.
     *
     * @param deploymentUnitName The name of the deployment unit
     * @param deploymentRoot     The deployment root
     * @param topLevelDeployment The top level deployment
     * @param toplevelRoot       The top level root
     * @param topLevel           {@code true} if the deployment is a top level deployment, {@code false} otherwise
     * @return the module identifier that represents the deployment
     * @deprecated Use {@link #createModuleIdentifierAsString(String, ResourceRoot, DeploymentUnit, VirtualFile, boolean)} instead
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    public static ModuleIdentifier createModuleIdentifier(final String deploymentUnitName, final ResourceRoot deploymentRoot, final DeploymentUnit topLevelDeployment, final VirtualFile toplevelRoot, final boolean topLevel) {
        return ModuleIdentifier.create(createModuleIdentifierAsString(deploymentUnitName, deploymentRoot, topLevelDeployment, toplevelRoot, topLevel));
    }

    /**
     * Create a module identifier for the deployment.
     *
     * @param deploymentUnitName The name of the deployment unit
     * @param deploymentRoot     The deployment root
     * @param topLevelDeployment The top level deployment
     * @param toplevelRoot       The top level root
     * @param topLevel           {@code true} if the deployment is a top level deployment, {@code false} otherwise
     * @return the module identifier that represents the deployment
     */
    public static String createModuleIdentifierAsString(final String deploymentUnitName, final ResourceRoot deploymentRoot, final DeploymentUnit topLevelDeployment, final VirtualFile toplevelRoot, final boolean topLevel) {
        // generate the module identifier for the deployment
        final String moduleIdentifier;
        if (topLevel) {
            moduleIdentifier = ServiceModuleLoader.MODULE_PREFIX + deploymentUnitName;
        } else {
            String relativePath = deploymentRoot.getRoot().getPathNameRelativeTo(toplevelRoot);
            moduleIdentifier = ServiceModuleLoader.MODULE_PREFIX + topLevelDeployment.getName() + '.' + relativePath.replace('/', '.');
        }
        return moduleIdentifier;
    }

}
