/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.io.InputStream;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.server.Utils;
import org.jboss.as.server.loaders.ResourceLoader;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.moduleservice.ModuleIndexBuilder;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.Indexer;
import org.jboss.modules.Resource;

/**
 * Utility class for indexing a resource root
 */
public class ResourceRootIndexer {

    /**
     * Creates and attaches the annotation index to a resource root, if it has not already been attached
     */
    public static void indexResourceRoot(final ResourceRoot resourceRoot) throws DeploymentUnitProcessingException {
        if (resourceRoot.getAttachment(Attachments.ANNOTATION_INDEX) != null) {
            return;
        }

        Resource indexFile = resourceRoot.getLoader().getResource(ModuleIndexBuilder.INDEX_LOCATION);
        if (indexFile != null) {
            try {
                IndexReader reader = new IndexReader(indexFile.openStream());
                resourceRoot.putAttachment(Attachments.ANNOTATION_INDEX, reader.read());
                ServerLogger.DEPLOYMENT_LOGGER.tracef("Found and read index at: %s", indexFile);
                return;
            } catch (Exception e) {
                ServerLogger.DEPLOYMENT_LOGGER.cannotLoadAnnotationIndex(indexFile.getName());
            }
        }

        // if this flag is present and set to false then do not index the resource
        Boolean shouldIndexResource = resourceRoot.getAttachment(Attachments.INDEX_RESOURCE_ROOT);
        if (shouldIndexResource != null && !shouldIndexResource) {
            return;
        }

        final List<String> indexIgnorePathList = resourceRoot.getAttachment(Attachments.INDEX_IGNORE_PATHS);
        final Set<String> indexIgnorePaths;
        if (indexIgnorePathList != null && !indexIgnorePathList.isEmpty()) {
            indexIgnorePaths = new HashSet<>(indexIgnorePathList);
        } else {
            indexIgnorePaths = null;
        }

        final ResourceLoader loader = resourceRoot.getLoader();
        final Indexer indexer = new Indexer();
        try {
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            InputStream is = null;
            String resourcePath;
            String resourceName;
            int pathSeparatorIndex;
            while (resources.hasNext()) {
                resource = resources.next();
                resourceName = resource.getName();
                if (!resourceName.endsWith(".class")) continue;
                pathSeparatorIndex = resourceName.lastIndexOf("/");
                resourcePath = pathSeparatorIndex != -1 ? resourceName.substring(0, pathSeparatorIndex) : null; // TODO
                if (indexIgnorePaths != null && indexIgnorePaths.contains(resourcePath)) continue;
                try {
                    indexer.index(is = resource.openStream());
                } catch (Exception e) {
                    ServerLogger.DEPLOYMENT_LOGGER.cannotIndexClass(resource.getName(), loader.getRootName(), e);
                } finally {
                    Utils.safeClose(is);
                }
            }
            final Index index = indexer.complete();
            resourceRoot.putAttachment(Attachments.ANNOTATION_INDEX, index);
            ServerLogger.DEPLOYMENT_LOGGER.tracef("Generated index for archive %s", loader.getRootName());
        } catch (Throwable t) {
            throw ServerLogger.ROOT_LOGGER.deploymentIndexingFailed(t);
        }
    }
}
