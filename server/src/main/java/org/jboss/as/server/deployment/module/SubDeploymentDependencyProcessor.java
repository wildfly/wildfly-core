/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.filter.PathFilters;

/**
 * Processor that set up a module dependency on the parent module
 *
 * @author Stuart Douglas
 */
public class SubDeploymentDependencyProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final DeploymentUnit parent = deploymentUnit.getParent() == null ? deploymentUnit : deploymentUnit.getParent();


        final ModuleSpecification parentModuleSpec = parent.getAttachment(Attachments.MODULE_SPECIFICATION);

        final ModuleSpecification moduleSpec = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final ModuleLoader moduleLoader = deploymentUnit.getAttachment(Attachments.SERVICE_MODULE_LOADER);
        final ModuleIdentifier moduleIdentifier = deploymentUnit.getAttachment(Attachments.MODULE_IDENTIFIER);

        if (deploymentUnit.getParent() != null) {
            final ModuleIdentifier parentModule = parent.getAttachment(Attachments.MODULE_IDENTIFIER);
            if (parentModule != null) {
                // access to ear classes
                ModuleDependency moduleDependency = new ModuleDependency(moduleLoader, parentModule, false, false, true, false);
                moduleDependency.addImportFilter(PathFilters.acceptAll(), true);
                moduleSpec.addLocalDependency(moduleDependency);
            }
        }

        // make the deployment content available to any additional modules
        for (AdditionalModuleSpecification module : deploymentUnit.getAttachmentList(Attachments.ADDITIONAL_MODULES)) {
            module.addLocalDependency(new ModuleDependency(moduleLoader, moduleIdentifier, false, false, true, false));
        }

        final List<DeploymentUnit> subDeployments = parent.getAttachmentList(Attachments.SUB_DEPLOYMENTS);
        final List<ModuleDependency> accessibleModules = new ArrayList<ModuleDependency>();
        for (DeploymentUnit subDeployment : subDeployments) {
            final ModuleSpecification subModule = subDeployment.getAttachment(Attachments.MODULE_SPECIFICATION);
            if (!subModule.isPrivateModule() && (!parentModuleSpec.isSubDeploymentModulesIsolated() || subModule.isPublicModule())) {
                ModuleIdentifier identifier = subDeployment.getAttachment(Attachments.MODULE_IDENTIFIER);
                ModuleDependency dependency = new ModuleDependency(moduleLoader, identifier, false, false, true, false);
                dependency.addImportFilter(PathFilters.acceptAll(), true);
                accessibleModules.add(dependency);
            }
        }
        for (ModuleDependency dependency : accessibleModules) {
            if (!dependency.getIdentifier().equals(moduleIdentifier)) {
                moduleSpec.addLocalDependency(dependency);
            }
        }
    }

}
