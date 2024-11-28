/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.Services;
import org.jboss.as.server.moduleservice.ExtensionIndex;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.filter.PathFilters;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * A processor which adds extension-list resource roots.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ModuleExtensionListProcessor implements DeploymentUnitProcessor {

    public ModuleExtensionListProcessor() {
    }

    /**
     * {@inheritDoc}
     */
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final ModuleLoader moduleLoader = deploymentUnit.getAttachment(Attachments.SERVICE_MODULE_LOADER);
        final ServiceController<?> controller = phaseContext.getServiceRegistry().getRequiredService(Services.JBOSS_DEPLOYMENT_EXTENSION_INDEX);
        final ExtensionIndex index = (ExtensionIndex) controller.getValue();
        final List<ResourceRoot> allResourceRoots = DeploymentUtils.allResourceRoots(deploymentUnit);
        final Set<ServiceName> nextPhaseDeps = new HashSet<ServiceName>();

        final Set<ModuleIdentifier> allExtensionListEntries = new LinkedHashSet<>();

        for (ResourceRoot resourceRoot : allResourceRoots) {
            final AttachmentList<ExtensionListEntry> entries = resourceRoot.getAttachment(Attachments.EXTENSION_LIST_ENTRIES);
            if (entries != null) {
                for (ExtensionListEntry entry : entries) {
                    final ModuleIdentifier extension = index.findExtension(entry.getName(), entry.getSpecificationVersion(),
                            entry.getImplementationVersion(), entry.getImplementationVendorId());
                    if (extension != null) {
                        allExtensionListEntries.add(extension);
                    } else {
                        ServerLogger.DEPLOYMENT_LOGGER.cannotFindExtensionListEntry(entry, resourceRoot);
                    }
                }
            }
        }

        if (deploymentUnit.getParent() != null) {
            //we also need to get all our parents extension list entries
            for (ResourceRoot resourceRoot : DeploymentUtils.allResourceRoots(deploymentUnit.getParent())) {
                final AttachmentList<ExtensionListEntry> entries = resourceRoot.getAttachment(Attachments.EXTENSION_LIST_ENTRIES);
                if (entries != null) {
                    for (ExtensionListEntry entry : entries) {
                        final ModuleIdentifier extension = index.findExtension(entry.getName(), entry.getSpecificationVersion(),
                                entry.getImplementationVersion(), entry.getImplementationVendorId());
                        if (extension != null) {
                            allExtensionListEntries.add(extension);
                        }
                        //we don't log not found to prevent multiple messages
                    }
                }
            }
        }

        for (ModuleIdentifier extension : allExtensionListEntries) {
            ModuleDependency dependency = new ModuleDependency(moduleLoader, extension, false, false, true, true);
            dependency.addImportFilter(PathFilters.getMetaInfSubdirectoriesFilter(), true);
            dependency.addImportFilter(PathFilters.getMetaInfFilter(), true);
            moduleSpecification.addLocalDependency(dependency);
            nextPhaseDeps.add(ServiceModuleLoader.moduleSpecServiceName(extension.toString()));
        }


        final List<AdditionalModuleSpecification> additionalModules = deploymentUnit.getAttachment(Attachments.ADDITIONAL_MODULES);
        if (additionalModules != null) {
            for (AdditionalModuleSpecification additionalModule : additionalModules) {
                for (ResourceRoot resourceRoot : additionalModule.getResourceRoots()) {
                    final AttachmentList<ExtensionListEntry> entries = resourceRoot
                            .getAttachment(Attachments.EXTENSION_LIST_ENTRIES);
                    if (entries != null) {

                        for (ExtensionListEntry entry : entries) {
                            final ModuleIdentifier extension = index.findExtension(entry.getName(), entry
                                    .getSpecificationVersion(), entry.getImplementationVersion(), entry
                                    .getImplementationVendorId());
                            if (extension != null) {
                                moduleSpecification.addLocalDependency(new ModuleDependency(moduleLoader, extension, false, false,
                                        true, false));
                                nextPhaseDeps.add(ServiceModuleLoader.moduleSpecServiceName(extension.toString()));
                            } else {
                                ServerLogger.DEPLOYMENT_LOGGER.cannotFindExtensionListEntry(entry, resourceRoot);
                            }
                        }
                    }
                }
            }
        }
        for (ServiceName dep : nextPhaseDeps) {
            phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, dep);
        }

    }

}
