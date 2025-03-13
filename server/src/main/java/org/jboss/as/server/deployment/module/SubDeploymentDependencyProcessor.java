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
        final String moduleIdentifier = deploymentUnit.getAttachment(Attachments.MODULE_NAME);

        if (deploymentUnit.getParent() != null) {
            final String parentModule = parent.getAttachment(Attachments.MODULE_NAME);
            if (parentModule != null) {
                // access to ear classes
                ModuleDependency moduleDependency = ModuleDependency.Builder.of(moduleLoader, parentModule).setImportServices(true).build();
                moduleDependency.addImportFilter(PathFilters.acceptAll(), true);
                moduleSpec.addLocalDependency(moduleDependency);
            }
        }

        // make the deployment content available to any additional modules
        for (AdditionalModuleSpecification module : deploymentUnit.getAttachmentList(Attachments.ADDITIONAL_MODULES)) {
            module.addLocalDependency(ModuleDependency.Builder.of(moduleLoader, moduleIdentifier).setImportServices(true).build());
        }

        final List<DeploymentUnit> subDeployments = parent.getAttachmentList(Attachments.SUB_DEPLOYMENTS);
        final List<ModuleDependency> accessibleModules = new ArrayList<>();
        for (DeploymentUnit subDeployment : subDeployments) {
            final ModuleSpecification subModule = subDeployment.getAttachment(Attachments.MODULE_SPECIFICATION);
            if (!subModule.isPrivateModule() && (!parentModuleSpec.isSubDeploymentModulesIsolated() || subModule.isPublicModule())) {
                String identifier = subDeployment.getAttachment(Attachments.MODULE_NAME);
                ModuleDependency dependency = ModuleDependency.Builder.of(moduleLoader, identifier).setImportServices(true).build();
                dependency.addImportFilter(PathFilters.acceptAll(), true);
                accessibleModules.add(dependency);
            }
        }
        for (ModuleDependency dependency : accessibleModules) {
            if (!dependency.getDependencyModule().equals(moduleIdentifier)) {
                moduleSpec.addLocalDependency(dependency);
            }
        }
    }

}
