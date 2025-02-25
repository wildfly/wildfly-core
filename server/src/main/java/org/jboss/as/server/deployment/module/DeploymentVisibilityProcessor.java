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
        final Map<String, DeploymentUnit> deployments = new HashMap<>();
        //local classes are always first
        deploymentUnit.addToAttachmentList(Attachments.ACCESSIBLE_SUB_DEPLOYMENTS, deploymentUnit);
        buildModuleMap(deploymentUnit, deployments);

        for (final ModuleDependency dependency : moduleSpec.getAllDependencies()) {
            final DeploymentUnit sub = deployments.get(dependency.getDependencyModule());
            if (sub != null) {
                deploymentUnit.addToAttachmentList(Attachments.ACCESSIBLE_SUB_DEPLOYMENTS, sub);
            }
        }
    }

    private void buildModuleMap(final DeploymentUnit deploymentUnit, final Map<String, DeploymentUnit> modules) {
        if (deploymentUnit.getParent() == null) {
            final List<DeploymentUnit> subDeployments = deploymentUnit.getAttachmentList(org.jboss.as.server.deployment.Attachments.SUB_DEPLOYMENTS);
            for (final DeploymentUnit sub : subDeployments) {
                final String identifier = sub.getAttachment(Attachments.MODULE_NAME);
                if (identifier != null) {
                    modules.put(identifier, sub);
                }
            }
        } else {
            final DeploymentUnit parent = deploymentUnit.getParent();
            final List<DeploymentUnit> subDeployments = parent.getAttachmentList(org.jboss.as.server.deployment.Attachments.SUB_DEPLOYMENTS);
            //add the parent description
            final String parentIdentifier = parent.getAttachment(Attachments.MODULE_NAME);
            if (parentIdentifier != null) {
                modules.put(parentIdentifier, parent);
            }

            for (final DeploymentUnit sub : subDeployments) {
                if (sub != deploymentUnit) {
                    final String identifier = sub.getAttachment(Attachments.MODULE_NAME);
                    if (identifier != null) {
                        modules.put(identifier, sub);
                    }
                }
            }
        }
    }

}
