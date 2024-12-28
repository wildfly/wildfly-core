/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.util.List;

import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.ModuleLoader;

/**
 * The processor which adds {@code MANIFEST.MF} {@code Class-Path} entries to the module configuration.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Stuart Douglas
 */
public final class ModuleClassPathProcessor implements DeploymentUnitProcessor {

    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);

        final ModuleLoader moduleLoader = deploymentUnit.getAttachment(Attachments.SERVICE_MODULE_LOADER);
        final AttachmentList<String> entries = deploymentUnit.getAttachment(ManifestClassPathProcessor.CLASS_PATH_MODULES);
        if (entries != null) {
            for (String entry : entries) {
                //class path items are always exported to make transitive dependencies work
                moduleSpecification.addLocalDependency(ModuleDependency.Builder.of(moduleLoader, entry).setExport(true).setImportServices(true).build());
            }
        }

        final List<AdditionalModuleSpecification> additionalModules = deploymentUnit.getAttachment(Attachments.ADDITIONAL_MODULES);
        if (additionalModules != null) {
            for (AdditionalModuleSpecification additionalModule : additionalModules) {
                final AttachmentList<String> dependencies = additionalModule.getAttachment(ManifestClassPathProcessor.CLASS_PATH_MODULES);
                if (dependencies == null || dependencies.isEmpty()) {
                    continue;
                }
                // additional modules export any class-path entries
                // this means that a module that references the additional module
                // gets access to the transitive closure of its call-path entries
                for (String entry : dependencies) {
                    additionalModule.addLocalDependency(ModuleDependency.Builder.of(moduleLoader, entry).setExport(true).setImportServices(true).build());
                }
                // add a dependency on the top ear itself for good measure
                additionalModule.addLocalDependency(ModuleDependency.Builder.of(moduleLoader, deploymentUnit.getAttachment(Attachments.MODULE_IDENTIFIER).toString()).setImportServices(true).build());
            }
        }
    }

}
