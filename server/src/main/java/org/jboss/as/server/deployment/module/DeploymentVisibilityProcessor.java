/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.ModuleIdentifier;

/**
 * Processor that aggregates all module descriptions visible to the deployment in an EEApplicationClasses structure.
 *
 * @author Stuart Douglas
 */
public class DeploymentVisibilityProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleSpecification moduleSpec = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE_SPECIFICATION);
        final Map<ModuleIdentifier, DeploymentUnit> deployments = new HashMap<ModuleIdentifier, DeploymentUnit>();
        //local classes are always first
        deploymentUnit.addToAttachmentList(Attachments.ACCESSIBLE_SUB_DEPLOYMENTS, deploymentUnit);
        buildModuleMap(deploymentUnit, deployments);

        for (final ModuleDependency dependency : moduleSpec.getAllDependencies()) {
            final DeploymentUnit sub = deployments.get(dependency.getIdentifier());
            if (sub != null) {
                deploymentUnit.addToAttachmentList(Attachments.ACCESSIBLE_SUB_DEPLOYMENTS, sub);
            }
        }
    }

    private void buildModuleMap(final DeploymentUnit deploymentUnit, final Map<ModuleIdentifier, DeploymentUnit> modules) {
        if (deploymentUnit.getParent() == null) {
            final List<DeploymentUnit> subDeployments = deploymentUnit.getAttachmentList(org.jboss.as.server.deployment.Attachments.SUB_DEPLOYMENTS);
            for (final DeploymentUnit sub : subDeployments) {
                final ModuleIdentifier identifier = sub.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE_IDENTIFIER);
                if (identifier != null) {
                    modules.put(identifier, sub);
                }
            }
        } else {
            final DeploymentUnit parent = deploymentUnit.getParent();
            final List<DeploymentUnit> subDeployments = parent.getAttachmentList(org.jboss.as.server.deployment.Attachments.SUB_DEPLOYMENTS);
            //add the parent description
            final ModuleIdentifier parentIdentifier = parent.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE_IDENTIFIER);
            if (parentIdentifier != null) {
                modules.put(parentIdentifier, parent);
            }

            for (final DeploymentUnit sub : subDeployments) {
                if (sub != deploymentUnit) {
                    final ModuleIdentifier identifier = sub.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE_IDENTIFIER);
                    if (identifier != null) {
                        modules.put(identifier, sub);
                    }
                }
            }
        }
    }

}
