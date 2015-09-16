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

package org.jboss.as.server.deployment.module;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.as.server.loaders.ResourceLoader;
import org.jboss.as.server.loaders.ResourceLoaders;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachable;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.MountedDeploymentOverlay;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.deployment.annotation.ResourceRootIndexer;
import org.jboss.as.server.moduleservice.ExternalModuleService;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

/**
 * A processor which adds class path entries for each manifest entry.
 * <p/>
 * <p/>
 * <p/>
 * </li>
 * <li>
 * If the Class-Path entry is external to the deployment then it is handled by the external jar service.</li>
 * <li>
 * If the entry refers to a sibling deployment then a dependency is added on that deployment. If this deployment is
 * not present then this deployment will block until it is.</li>
 * <li>
 * If the Class-Path entry points to a jar inside the ear that is not a deployment and not a /lib jar then a reference is added
 * to this jars {@link AdditionalModuleSpecification}</li>
 * </ul>
 *
 * @author Stuart Douglas
 * @author Ales Justin
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ManifestClassPathProcessor implements DeploymentUnitProcessor {

    private static final String[] EMPTY_STRING_ARRAY = {};

    /**
     * We only allow a single deployment at a time to be run through the class path processor.
     * <p/>
     * This is because if multiple sibling deployments reference the same item we need to make sure that they end up
     * with the same external module, and do not both create an external module with the same name.
     */
    public synchronized void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final DeploymentUnit parent = deploymentUnit.getParent();
        final DeploymentUnit topLevelDeployment = parent == null ? deploymentUnit : parent;
        final VirtualFile topLevelRoot = topLevelDeployment.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot(); // TODO: remove
        final ResourceLoader topLevelLoader = topLevelDeployment.getAttachment(Attachments.DEPLOYMENT_ROOT).getLoader();
        final ExternalModuleService externalModuleService = topLevelDeployment.getAttachment(Attachments.EXTERNAL_MODULE_SERVICE);
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);

        //These are resource roots that are already accessible by default
        //such as ear/lib jars an web-inf/lib jars
        final Set<VirtualFile> existingAccessibleRoots = new HashSet<VirtualFile>();
        final Map<VirtualFile, ResourceRoot> subDeployments = new HashMap<VirtualFile, ResourceRoot>();
        for (ResourceRoot root : DeploymentUtils.allResourceRoots(topLevelDeployment)) {
            if (SubDeploymentMarker.isSubDeployment(root)) {
                subDeployments.put(root.getRoot(), root);
            } else if (ModuleRootMarker.isModuleRoot(root)) {
                //top level module roots are already accessible, as they are either
                //ear/lib jars, or jars that are already part of the deployment
                existingAccessibleRoots.add(root.getRoot());
            }
        }

        final ArrayDeque<RootEntry> resourceRoots = new ArrayDeque<RootEntry>();
        if (deploymentUnit.getParent() != null) {
            //top level deployments already had their exiting roots processed above
            for (ResourceRoot root : DeploymentUtils.allResourceRoots(deploymentUnit)) {
                if (ModuleRootMarker.isModuleRoot(root)) {
                    //if this is a sub deployment of an ear we need to make sure we don't
                    //re-add existing module roots as class path entries
                    //this will mainly be WEB-INF/(lib|classes) entries
                    existingAccessibleRoots.add(root.getRoot());
                }
            }
        }

        for (ResourceRoot root : DeploymentUtils.allResourceRoots(deploymentUnit)) {
            //add this to the list of roots to be processed
            resourceRoots.add(new RootEntry(deploymentUnit, root));
        }

        // build a map of the additional module locations
        // note that if a resource root has been added to two different additional modules
        // and is then referenced via a Class-Path entry the behaviour is undefined
        final Map<VirtualFile, AdditionalModuleSpecification> additionalModules = new HashMap<VirtualFile, AdditionalModuleSpecification>();
        for (AdditionalModuleSpecification module : topLevelDeployment.getAttachmentList(Attachments.ADDITIONAL_MODULES)) {
            for (ResourceRoot additionalModuleResourceRoot : module.getResourceRoots()) {
                additionalModules.put(additionalModuleResourceRoot.getRoot(), module);
            }
        }

        //additional resource roots may be added as
        while (!resourceRoots.isEmpty()) {
            final RootEntry entry = resourceRoots.pop();
            final ResourceRoot resourceRoot = entry.resourceRoot;
            final Attachable target = entry.target;

            //if this is a top level deployment we do not want to process sub deployments
            if (SubDeploymentMarker.isSubDeployment(resourceRoot) && resourceRoot != deploymentRoot) {
                continue;
            }

            final String[] items = getClassPathEntries(resourceRoot);
            boolean classPathItemExists, topLevelClassPathItemExists;
            for (final String item : items) {
                if (item.isEmpty() || item.equals(".")) { //a class path of . causes problems and is unnecessary, see WFLY-2725
                    continue;
                }
                if (item.contains("../") || item.endsWith("/")) {
                    ServerLogger.DEPLOYMENT_LOGGER.classPathEntryNotValid(item, resourceRoot.getLoader().getRootName());
                }
                if (item.startsWith("/")) {
                    if (externalModuleService.isValid(item)) {
                        final ModuleIdentifier moduleIdentifier = externalModuleService.addExternalModule(item);
                        target.addToAttachmentList(Attachments.CLASS_PATH_ENTRIES, moduleIdentifier);
                        ServerLogger.DEPLOYMENT_LOGGER.debugf("Resource %s added as external jar %s", item, resourceRoot.getRoot());
                    } else {
                        ServerLogger.DEPLOYMENT_LOGGER.classPathEntryNotValid(item, resourceRoot.getLoader().getRootName());
                    }
                } else {
                    //first try and resolve relative to the manifest resource root
                    final VirtualFile classPathFile = resourceRoot.getRoot().getParent().getChild(item); //TODO: remove
                    classPathItemExists = resourceOrPathExists(resourceRoot.getLoader(), item);
                    //then resolve relative to the deployment root
                    final VirtualFile topLevelClassPathFile = deploymentRoot.getRoot().getParent().getChild(item); //TODO: remove
                    topLevelClassPathItemExists = resourceOrPathExists(deploymentRoot.getLoader(), item);
                    if (classPathItemExists) {
                        //we need to check that this class path item actually lies within the deployment
                        boolean found = false;
                        ResourceLoader loader = resourceRoot.getLoader();
                        while (loader != null) {
                            if (loader == topLevelLoader) {
                                found = true;
                            }
                            loader = loader.getParent();
                        }
                        if (!found) {
                            ServerLogger.DEPLOYMENT_LOGGER.classPathEntryNotValid(item, resourceRoot.getLoader().getRootName());
                        } else {
                            final String relativePath = classPathFile.getPathNameRelativeTo(topLevelRoot);
                            final ResourceLoader itemLoader = createResourceLoader(classPathFile.getName(), resourceRoot.getLoader(), item); // TODO: fix loader name creation
                            handlingExistingClassPathEntry(resourceRoots, topLevelDeployment, relativePath, subDeployments, additionalModules, existingAccessibleRoots, resourceRoot, target, classPathFile, itemLoader);
                        }
                    } else if (topLevelClassPathItemExists) {
                        boolean found = false;
                        ResourceLoader loader = deploymentRoot.getLoader();
                        while (loader != null) {
                            if (loader == topLevelLoader) {
                                found = true;
                            }
                            loader = loader.getParent();
                        }
                        if (!found) {
                            ServerLogger.DEPLOYMENT_LOGGER.classPathEntryNotValid(item, resourceRoot.getLoader().getRootName());
                        } else {
                            final String relativePath = topLevelClassPathFile.getPathNameRelativeTo(topLevelRoot);
                            final ResourceLoader itemLoader = createResourceLoader(topLevelClassPathFile.getName(), deploymentRoot.getLoader(), item); // TODO: fix loader name creation
                            handlingExistingClassPathEntry(resourceRoots, topLevelDeployment, relativePath, subDeployments, additionalModules, existingAccessibleRoots, resourceRoot, target, topLevelClassPathFile, itemLoader);
                        }
                    } else {
                        ServerLogger.DEPLOYMENT_LOGGER.classPathEntryNotValid(item, resourceRoot.getLoader().getRootName());
                    }
                }
            }
        }
    }

    private void handlingExistingClassPathEntry(final ArrayDeque<RootEntry> resourceRoots, final DeploymentUnit topLevelDeployment, final String relativePath, final Map<VirtualFile, ResourceRoot> subDeployments, final Map<VirtualFile, AdditionalModuleSpecification> additionalModules, final Set<VirtualFile> existingAccessibleRoots, final ResourceRoot resourceRoot, final Attachable target, final VirtualFile classPathFile, final ResourceLoader itemLoader) throws DeploymentUnitProcessingException {
        if (existingAccessibleRoots.contains(classPathFile)) {
            ServerLogger.DEPLOYMENT_LOGGER.debugf("Class-Path entry %s in %s ignored, as target is already accessible", classPathFile, resourceRoot.getRoot());
        } else if (additionalModules.containsKey(classPathFile)) {
            final AdditionalModuleSpecification moduleSpecification = additionalModules.get(classPathFile);
            //as class path entries are exported, transitive dependencies will also be available
            target.addToAttachmentList(Attachments.CLASS_PATH_ENTRIES, moduleSpecification.getModuleIdentifier());
        } else if (subDeployments.containsKey(classPathFile)) {
            //now we need to calculate the sub deployment module identifier
            //unfortunately the sub deployment has not been setup yet, so we cannot just
            //get it from the sub deployment directly
            target.addToAttachmentList(Attachments.CLASS_PATH_ENTRIES, createModuleIdentifier(topLevelDeployment, relativePath));
        } else {
            ModuleIdentifier identifier = createAdditionalModule(resourceRoot, topLevelDeployment, relativePath, additionalModules, classPathFile, resourceRoots, itemLoader);
            target.addToAttachmentList(Attachments.CLASS_PATH_ENTRIES, identifier);
        }
    }

    private static ResourceLoader createResourceLoader(final String loaderName, final ResourceLoader parentLoader, final String subresourcePath) throws DeploymentUnitProcessingException {
        try {
            return ResourceLoaders.newResourceLoader(loaderName, parentLoader, subresourcePath);
        } catch (IOException e) {
            throw ServerLogger.DEPLOYMENT_LOGGER.errorCreatingResourceLoader(subresourcePath, e);
        }
    }

    private ModuleIdentifier createAdditionalModule(final ResourceRoot resourceRoot, final DeploymentUnit topLevelDeployment, final String relativePath, final Map<VirtualFile, AdditionalModuleSpecification> additionalModules, final VirtualFile classPathFile, final ArrayDeque<RootEntry> resourceRoots, final ResourceLoader itemLoader) throws DeploymentUnitProcessingException {
        final ResourceRoot root = createResourceRoot(classPathFile, topLevelDeployment, relativePath, itemLoader);
        ModuleIdentifier identifier = createModuleIdentifier(topLevelDeployment, relativePath);
        AdditionalModuleSpecification module = new AdditionalModuleSpecification(identifier, root);
        topLevelDeployment.addToAttachmentList(Attachments.ADDITIONAL_MODULES, module);
        additionalModules.put(classPathFile, module);
        resourceRoot.addToAttachmentList(Attachments.CLASS_PATH_RESOURCE_ROOTS, root);

        //add this to the list of roots to be processed, so transitive class path entries will be respected
        resourceRoots.add(new RootEntry(module, root));
        return identifier;
    }

    private static boolean resourceOrPathExists(final ResourceLoader loader, final String resourceOrPath) {
        return loader.getResource(resourceOrPath) != null || loader.getPaths().contains(resourceOrPath);
    }

    private static ModuleIdentifier createModuleIdentifier(final DeploymentUnit topLevelDeployment, final String relativePath) {
        return ModuleIdentifier.create(ServiceModuleLoader.MODULE_PREFIX + topLevelDeployment.getName() + '.' + relativePath.replace('/', '.'));
    }

    private static String[] getClassPathEntries(final ResourceRoot resourceRoot) {

        final Manifest manifest;
        try {
            manifest = Utils.getManifest(resourceRoot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (manifest == null) {
            // no class path to process!
            return EMPTY_STRING_ARRAY;
        }
        final String classPathString = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        if (classPathString == null) {
            // no entry
            return EMPTY_STRING_ARRAY;
        }
        return classPathString.split("\\s+");
    }

    /**
     * Creates a {@link ResourceRoot} for the passed {@link VirtualFile file} and adds it to the list of {@link ResourceRoot}s
     * in the {@link DeploymentUnit deploymentUnit}
     *
     *
     * @param file           The file for which the resource root will be created
     * @return Returns the created {@link ResourceRoot}
     * @throws java.io.IOException
     */
    private synchronized ResourceRoot createResourceRoot(final VirtualFile file, final DeploymentUnit deploymentUnit, final String relativePath, final ResourceLoader itemLoader) throws DeploymentUnitProcessingException {
        try {
            Map<String, MountedDeploymentOverlay> overlays = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_OVERLAY_LOCATIONS);

            MountedDeploymentOverlay overlay = overlays.get(relativePath);
            Closeable closable = null;
            ResourceLoader loader = itemLoader;
            if(overlay != null) {
                overlay.remountAsZip();
                loader = ResourceLoaders.newResourceLoader(itemLoader.getRootName(), overlay.getFile(), itemLoader.getParent());
            } else if(file.isFile()) {
                closable = VFS.mountZip(file, file);
            }
            final MountHandle mountHandle = new MountHandle(closable);
            final ResourceRoot resourceRoot = new ResourceRoot(loader, file, mountHandle);
            ModuleRootMarker.mark(resourceRoot);
            ResourceRootIndexer.indexResourceRoot(resourceRoot);
            return resourceRoot;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void undeploy(final DeploymentUnit context) {
    }


    private class RootEntry {
        private final ResourceRoot resourceRoot;
        private final Attachable target;


        private RootEntry(final Attachable target, final ResourceRoot resourceRoot) {
            this.target = target;
            this.resourceRoot = resourceRoot;
        }
    }

}
