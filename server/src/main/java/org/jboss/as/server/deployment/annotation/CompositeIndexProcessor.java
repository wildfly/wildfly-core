/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.server.deployment.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.moduleservice.ModuleIndexBuilder;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.Resource;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;

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
                try {
                    Module module = moduleLoader.loadModule(moduleIdentifier);
                    final CompositeIndex additionalIndex = ModuleIndexBuilder.buildCompositeIndex(module);
                    if (additionalIndex != null) {
                        indexes.addAll(additionalIndex.indexes);
                        additionalAnnotationIndexes.put(moduleIdentifier, additionalIndex);
                    } else {
                        final Index index = calculateModuleIndex(module);
                        indexes.add(index);
                    }
                } catch (ModuleLoadException e) {
                    throw new DeploymentUnitProcessingException(e);
                } catch (IOException e) {
                    throw new DeploymentUnitProcessingException(e);
                }
            }
        }
        deploymentUnit.putAttachment(Attachments.ADDITIONAL_ANNOTATION_INDEXES_BY_MODULE, additionalAnnotationIndexes);

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
        Set<ModuleIdentifier> depModuleIdentifiers = new HashSet<>();
        for (ModuleDependency dep: deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION).getAllDependencies()) {
            depModuleIdentifiers.add(dep.getIdentifier());
        }

        DeploymentUnit top = deploymentUnit.getParent()==null?deploymentUnit:deploymentUnit.getParent();
        Map<ModuleIdentifier, DeploymentUnit> res = new HashMap<>();
        AttachmentList<DeploymentUnit> subDeployments = top.getAttachment(Attachments.SUB_DEPLOYMENTS);
        if (subDeployments != null) {
            for (DeploymentUnit subDeployment : subDeployments) {
                ModuleIdentifier moduleIdentifier = subDeployment.getAttachment(Attachments.MODULE_IDENTIFIER);
                if (depModuleIdentifiers.contains(moduleIdentifier)) {
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

    private Index calculateModuleIndex(final Module module) throws ModuleLoadException, IOException {
        final Indexer indexer = new Indexer();
        final PathFilter filter = PathFilters.getDefaultImportFilter();
        final Iterator<Resource> iterator = module.iterateResources(filter);
        while (iterator.hasNext()) {
            Resource resource = iterator.next();
            if(resource.getName().endsWith(".class")) {
                try (InputStream in = resource.openStream()) {
                    indexer.index(in);
                } catch (Exception e) {
                    ServerLogger.DEPLOYMENT_LOGGER.cannotIndexClass(resource.getName(), resource.getURL().toExternalForm(), e);
                }
            }
        }
        return indexer.complete();
    }

    public void undeploy(DeploymentUnit deploymentUnit) {
        deploymentUnit.removeAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
    }
}
