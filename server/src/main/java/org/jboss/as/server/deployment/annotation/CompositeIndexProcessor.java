/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.annotation;

import java.lang.ref.Reference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.deployment.module.AdditionalModuleSpecification;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.jandex.Index;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;

/**
 * Processor responsible for creating and attaching a {@link CompositeIndex} for a deployment.
 * <p/>
 * This must run after the {@link org.jboss.as.server.deployment.module.ManifestDependencyProcessor}
 *
 * @author John Bailey
 * @author Stuart Douglas
 */
public class CompositeIndexProcessor implements DeploymentUnitProcessor {

    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleLoader moduleLoader = deploymentUnit.getAttachment(Attachments.SERVICE_MODULE_LOADER);
        final Reference<AnnotationIndexSupport> indexSupportRef = deploymentUnit.getAttachment(Attachments.ANNOTATION_INDEX_SUPPORT);
        assert indexSupportRef != null;

        final Boolean computeCompositeIndex = deploymentUnit.getAttachment(Attachments.COMPUTE_COMPOSITE_ANNOTATION_INDEX);
        if (computeCompositeIndex != null && !computeCompositeIndex) {
            return;
        }
        DeploymentUnit top = deploymentUnit.getParent() == null ? deploymentUnit : deploymentUnit.getParent();
        Map<ModuleIdentifier, AdditionalModuleSpecification> additionalModuleSpecificationMap = new HashMap<>();
        for(AdditionalModuleSpecification i : top.getAttachmentList(Attachments.ADDITIONAL_MODULES)) {
            additionalModuleSpecificationMap.put(i.getModuleIdentifier(), i);
        }
        Map<ModuleIdentifier, CompositeIndex> additionalAnnotationIndexes = new HashMap<ModuleIdentifier, CompositeIndex>();
        final List<ModuleIdentifier> additionalModuleIndexes = deploymentUnit.getAttachmentList(Attachments.ADDITIONAL_ANNOTATION_INDEXES);
        final List<Index> indexes = new ArrayList<Index>();

        Map<ModuleIdentifier, DeploymentUnit> subdeploymentDependencies = buildSubdeploymentDependencyMap(deploymentUnit);

        for (final ModuleIdentifier moduleIdentifier : additionalModuleIndexes) {
            AdditionalModuleSpecification additional = additionalModuleSpecificationMap.get(moduleIdentifier);
            if(additional != null) {
                // This module id refers to a deployment-specific module created based on a MANIFEST.MF Class-Path entry
                // or jboss-deployment-structure.xml or equivalent jboss-all.xml content. Obtain indexes from its resources.
                final List<Index> moduleIndexes = new ArrayList<>();
                for(ResourceRoot resource : additional.getResourceRoots()) {
                    ResourceRootIndexer.indexResourceRoot(resource);
                    Index indexAttachment = resource.getAttachment(Attachments.ANNOTATION_INDEX);
                    if(indexAttachment != null) {
                        indexes.add(indexAttachment);
                        moduleIndexes.add(indexAttachment);
                    }
                }
                if (!moduleIndexes.isEmpty()) {
                    additionalAnnotationIndexes.put(moduleIdentifier, new CompositeIndex(moduleIndexes));
                }
            } else if (subdeploymentDependencies.containsKey(moduleIdentifier)) {
                // This module id refers to a subdeployment. Find the indices for its resources.
                List<ResourceRoot> resourceRoots = subdeploymentDependencies.get(moduleIdentifier).getAttachment(Attachments.RESOURCE_ROOTS);
                final List<ResourceRoot> allResourceRoots = new ArrayList<>();
                if (resourceRoots != null) {
                    allResourceRoots.addAll(resourceRoots);
                }
                final ResourceRoot deploymentRoot = subdeploymentDependencies.get(moduleIdentifier).getAttachment(Attachments.DEPLOYMENT_ROOT);
                if (ModuleRootMarker.isModuleRoot(deploymentRoot)) {
                    allResourceRoots.add(deploymentRoot);
                }
                final List<Index> moduleIndexes = new ArrayList<>();
                for (ResourceRoot resourceRoot : allResourceRoots) {
                    Index index = resourceRoot.getAttachment(Attachments.ANNOTATION_INDEX);
                    if (index != null) {
                        indexes.add(index);
                        moduleIndexes.add(index);
                    }
                }
                if (!moduleIndexes.isEmpty()) {
                    additionalAnnotationIndexes.put(moduleIdentifier, new CompositeIndex(moduleIndexes));
                }
            } else {
                // This module id refers to a module external to the deployment. Get the indices from the support object.
                CompositeIndex externalModuleIndexes;
                AnnotationIndexSupport annotationIndexSupport = indexSupportRef.get();
                if (annotationIndexSupport != null) {
                    externalModuleIndexes = annotationIndexSupport.getAnnotationIndices(moduleIdentifier.toString(), moduleLoader);
                } else {
                    // This implies the DeploymentUnitService was restarted after the original operation that held
                    // the strong ref to the AnnotationIndexSupport. So we can't benefit from caching. Just calculate
                    // the indices without worrying about caching.
                    externalModuleIndexes = AnnotationIndexSupport.indexModule(moduleIdentifier.toString(), moduleLoader);
                }
                indexes.addAll(externalModuleIndexes.indexes);
                additionalAnnotationIndexes.put(moduleIdentifier, externalModuleIndexes);
            }
        }
        deploymentUnit.putAttachment(Attachments.ADDITIONAL_ANNOTATION_INDEXES_BY_MODULE, additionalAnnotationIndexes);
        // Attach an additional map keyed by name. Next release this key will be the only map attached.
        Map<String, CompositeIndex> additionalIndexesByName = new HashMap<>(additionalAnnotationIndexes.size());
        for (Map.Entry<ModuleIdentifier, CompositeIndex> entry : additionalAnnotationIndexes.entrySet()) {
            additionalIndexesByName.put(entry.getKey().toString(), entry.getValue());
        }
        deploymentUnit.putAttachment(Attachments.ADDITIONAL_ANNOTATION_INDEXES_BY_MODULE_NAME,
                // This should have always been an immutable map
                Collections.unmodifiableMap(additionalIndexesByName));

        final List<ResourceRoot> allResourceRoots = new ArrayList<ResourceRoot>();
        final List<ResourceRoot> resourceRoots = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        for (ResourceRoot resourceRoot : resourceRoots) {
            // do not add child sub deployments to the composite index
            if (!SubDeploymentMarker.isSubDeployment(resourceRoot) && ModuleRootMarker.isModuleRoot(resourceRoot)) {
                allResourceRoots.add(resourceRoot);
            }
        }


        //we merge all Class-Path annotation indexes into the deployments composite index
        //this means that if component defining annotations (e.g. @Stateless) are specified in a Class-Path
        //entry references by two sub deployments this component will be created twice.
        //the spec expects this behaviour, and explicitly warns not to put component defining annotations
        //in Class-Path items
        allResourceRoots.addAll(handleClassPathItems(deploymentUnit));

        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        if (ModuleRootMarker.isModuleRoot(deploymentRoot)) {
            allResourceRoots.add(deploymentRoot);
        }
        for (ResourceRoot resourceRoot : allResourceRoots) {
            Index index = resourceRoot.getAttachment(Attachments.ANNOTATION_INDEX);
            if (index != null) {
                indexes.add(index);
            }
        }
        deploymentUnit.putAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX, new CompositeIndex(indexes));
    }

    private Map<ModuleIdentifier, DeploymentUnit> buildSubdeploymentDependencyMap(DeploymentUnit deploymentUnit) {
        Set<String> depModuleIdentifiers = new HashSet<>();
        for (ModuleDependency dep: deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION).getAllDependencies()) {
            depModuleIdentifiers.add(dep.getDependencyModule());
        }

        DeploymentUnit top = deploymentUnit.getParent()==null?deploymentUnit:deploymentUnit.getParent();
        Map<ModuleIdentifier, DeploymentUnit> res = new HashMap<>();
        AttachmentList<DeploymentUnit> subDeployments = top.getAttachment(Attachments.SUB_DEPLOYMENTS);
        if (subDeployments != null) {
            for (DeploymentUnit subDeployment : subDeployments) {
                ModuleIdentifier moduleIdentifier = subDeployment.getAttachment(Attachments.MODULE_IDENTIFIER);
                if (depModuleIdentifiers.contains(moduleIdentifier.toString())) {
                    res.put(moduleIdentifier, subDeployment);
                }
            }
        }
        return res;
    }

    /**
     * Loops through all resource roots that have been made available transitively via Class-Path entries, and
     * adds them to the list of roots to be processed.
     */
    private Collection<? extends ResourceRoot> handleClassPathItems(final DeploymentUnit deploymentUnit) {
        final Set<ResourceRoot> additionalRoots = new HashSet<ResourceRoot>();
        final ArrayDeque<ResourceRoot> toProcess = new ArrayDeque<ResourceRoot>();
        final List<ResourceRoot> resourceRoots = DeploymentUtils.allResourceRoots(deploymentUnit);
        toProcess.addAll(resourceRoots);
        final Set<ResourceRoot> processed = new HashSet<ResourceRoot>(resourceRoots);

        while (!toProcess.isEmpty()) {
            final ResourceRoot root = toProcess.pop();
            final List<ResourceRoot> classPathRoots = root.getAttachmentList(Attachments.CLASS_PATH_RESOURCE_ROOTS);
            for(ResourceRoot cpRoot : classPathRoots) {
                if(!processed.contains(cpRoot)) {
                    additionalRoots.add(cpRoot);
                    toProcess.add(cpRoot);
                    processed.add(cpRoot);
                }
            }
        }
        return additionalRoots;
    }

    public void undeploy(DeploymentUnit deploymentUnit) {
        deploymentUnit.removeAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
    }
}
