/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.util.List;
import java.util.Locale;

import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.filter.PathFilters;

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
                additionalModule.addLocalDependency(ModuleDependency.Builder.of(moduleLoader, deploymentUnit.getAttachment(Attachments.MODULE_NAME)).setImportServices(true).build());
            }
        }


        // If this is a private subdeployment that recorded a Class-Path dependency on an additional module,
        // add a dep on our module to the additional module, to give it a classloading space analogous to
        // if both its archive and the subdeployments' were loadable from a single URLClassloader.
        //
        // For non-private subdeployments we don't need to do this since the top level deployment will have
        // a dep on the subdeployment (via SubDeploymentDependencyProcessor), and it will add it to all
        // additional modules in ModuleSpecProcessor.
        //
        // We don't do this for war subdeployments. This behavior is a workaround for an odd classloading
        // setup involving an appclient jar (which is private). We don't want to change the long-standing
        // behavior of the much more widely used war deployment type without a demonstrated need.
        if (entries != null && moduleSpecification.isPrivateModule() && deploymentUnit.getParent() != null
                && !deploymentUnit.getName().toLowerCase(Locale.ENGLISH).endsWith(".war")) {

            final String moduleIdentifier = deploymentUnit.getAttachment(Attachments.MODULE_NAME);

            // Additional modules are attached to the parent, not the subdeployment
            final List<AdditionalModuleSpecification> parentAdditionalModules =
                    deploymentUnit.getParent().getAttachment(Attachments.ADDITIONAL_MODULES);

            for (String classPathEntry : entries) {
                for (AdditionalModuleSpecification additionalModule : parentAdditionalModules) {
                    if (classPathEntry.equals(additionalModule.getModuleName())) {
                        // This is the same dep setting that SubDeploymentDependencyProcessor would add
                        // to the top level deployment module for a non-private subdeployment, which in turn
                        // gets passed to the additional module in ModuleSpecProcessor
                        ModuleDependency dependency = ModuleDependency.Builder.of(moduleLoader, moduleIdentifier)
                                .setImportServices(true).build();
                        dependency.addImportFilter(PathFilters.acceptAll(), true);
                        additionalModule.addLocalDependency(dependency);
                        break;
                    }
                }
            }
        }
    }

}
