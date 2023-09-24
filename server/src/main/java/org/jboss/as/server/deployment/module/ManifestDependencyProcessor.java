/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.ManifestHelper;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.filter.PathFilters;

/**
 * Deployment unit processor that will extract module dependencies from an and attach them.
 *
 * @author John E. Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author Stuart Douglas
 */
public final class ManifestDependencyProcessor implements DeploymentUnitProcessor {

    private static final String DEPENDENCIES_ATTR = "Dependencies";
    private static final String EXPORT_PARAM = "export";
    private static final String OPTIONAL_PARAM = "optional";
    private static final String SERVICES_PARAM = "services";
    private static final String ANNOTATIONS_PARAM = "annotations";
    private static final String META_INF = "meta-inf";

    /**
     * Process the deployment root for module dependency information.
     *
     * @param phaseContext the deployment unit context
     * @throws org.jboss.as.server.deployment.DeploymentUnitProcessingException
     */
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ServiceModuleLoader deploymentModuleLoader = deploymentUnit.getAttachment(Attachments.SERVICE_MODULE_LOADER);
        final List<ResourceRoot> allResourceRoots = DeploymentUtils.allResourceRoots(deploymentUnit);
        DeploymentUnit top = deploymentUnit.getParent() == null ? deploymentUnit : deploymentUnit.getParent();

        final Set<ModuleIdentifier> additionalModules = new HashSet<>();
        final List<AdditionalModuleSpecification> additionalModuleList = top.getAttachmentList(Attachments.ADDITIONAL_MODULES);
        // Must synchronize on list as subdeployments executing Phase.STRUCTURE may be concurrently modifying it
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (additionalModuleList) {
            for (AdditionalModuleSpecification i : additionalModuleList) {
                additionalModules.add(i.getModuleIdentifier());
            }
        }
        for (final ResourceRoot resourceRoot : allResourceRoots) {
            final Manifest manifest = resourceRoot.getAttachment(Attachments.MANIFEST);
            if (manifest == null)
                continue;

            final String dependencyString = ManifestHelper.getMainAttributeValue(manifest, DEPENDENCIES_ATTR);
            if (dependencyString == null)
                continue;

            if(deploymentUnit.getParent() == null &&
                    SubDeploymentMarker.isSubDeployment(resourceRoot)) {
                //we do not want ears reading sub deployments manifests
                continue;
            }

            final String[] dependencyDefs = dependencyString.split(",");
            for (final String dependencyDef : dependencyDefs) {
                final String trimmed = dependencyDef.trim();
                if(trimmed.isEmpty()) {
                    continue;
                }
                final String[] dependencyParts = trimmed.split(" ");

                final ModuleIdentifier dependencyId = ModuleIdentifier.fromString(dependencyParts[0]);
                final boolean export = containsParam(dependencyParts, EXPORT_PARAM);
                final boolean optional = containsParam(dependencyParts, OPTIONAL_PARAM);
                final boolean services = containsParam(dependencyParts, SERVICES_PARAM);
                final boolean annotations = containsParam(dependencyParts, ANNOTATIONS_PARAM);
                final boolean metaInf = containsParam(dependencyParts, META_INF);
                final ModuleLoader dependencyLoader;
                if (dependencyId.getName().startsWith(ServiceModuleLoader.MODULE_PREFIX)) {
                    dependencyLoader = deploymentModuleLoader;
                } else {
                    dependencyLoader = Module.getBootModuleLoader();
                }
                if(annotations) {
                    deploymentUnit.addToAttachmentList(Attachments.ADDITIONAL_ANNOTATION_INDEXES, dependencyId);
                    if(dependencyLoader == deploymentModuleLoader && !additionalModules.contains(dependencyId)) {
                        //additional modules will not be created till much later, a dep on them would fail
                        phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, ServiceModuleLoader.moduleServiceName(dependencyId));
                    }
                }

                final ModuleDependency dependency = new ModuleDependency(dependencyLoader, dependencyId, optional, export, services, true);
                if(metaInf) {
                    dependency.addImportFilter(PathFilters.getMetaInfSubdirectoriesFilter(), true);
                    dependency.addImportFilter(PathFilters.getMetaInfFilter(), true);
                }
                deploymentUnit.addToAttachmentList(Attachments.MANIFEST_DEPENDENCIES, dependency);
            }
        }

    }

    private boolean containsParam(final String[] parts, final String expected) {
        if (parts.length > 1) {
            for (int i = 1; i < parts.length; i++) {
                if (expected.equals(parts[i])) {
                    return true;
                }
            }
        }
        return false;
    }
}
