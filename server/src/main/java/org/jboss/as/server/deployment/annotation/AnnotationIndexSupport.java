/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.moduleservice.ModuleIndexBuilder;
import org.jboss.jandex.Indexer;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.Resource;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.wildfly.common.Assert;

/**
 * Utility class used by {@link CompositeIndexProcessor} to assist with
 * processing annotation indices for a deployment.
 */
public final class AnnotationIndexSupport {

    private final ConcurrentMap<String, Lock> indexLocks = new ConcurrentHashMap<>();
    private final Map<String, CompositeIndex> indexCache = new ConcurrentHashMap<>();

    /**
     * Gets the annotation indices for the resources in the module with the given id.
     * @param moduleId the id of the module to be indexed. Cannot be {@code null}.
     * @param moduleLoader {@link ModuleLoader} to use to load the module. Cannot be {@code null}.
     * @return the indices. Will not return {@code null}.
     * @throws DeploymentUnitProcessingException if a problem occurs obtaining the indices
     */
    CompositeIndex getAnnotationIndices(String moduleId, ModuleLoader moduleLoader) throws DeploymentUnitProcessingException {
        Assert.checkNotNullParam("moduleId", moduleId);
        Assert.checkNotNullParam("moduleLoader", moduleLoader);

        CompositeIndex result;

        // See if we have this one in the cache
        result = indexCache.get(moduleId);
        if (result == null) {
            // Nope. Only one thread at a time can create the index for this module id
            Lock lock = getModuleIndexLock(moduleId);
            try {
                lock.lockInterruptibly();
                try {
                    // Check in case they were cached while we waited for the lock
                    result = indexCache.get(moduleId);
                    if (result == null) {
                        // Nope. We build and cache the indices
                        result = indexModule(moduleId, moduleLoader);
                        indexCache.put(moduleId, result);
                    }
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Unlikely but maybe it was cached while we waited for the lock
                result = indexCache.get(moduleId);
                if (result == null) {
                    throw ServerLogger.DEPLOYMENT_LOGGER.staticModuleIndexingFailed(moduleId, e);
                }
            }
        } else {
            ServerLogger.DEPLOYMENT_LOGGER.debugf("Using cached annotation indices for static module %s", moduleId);
        }
        return result;
    }

    private Lock getModuleIndexLock(String moduleId) {
        Lock lock = indexLocks.get(moduleId);
        if (lock == null) {
            lock = new ReentrantLock();
            Lock existing = indexLocks.putIfAbsent(moduleId, lock);
            lock = existing == null ? lock : existing;
        }
        return lock;
    }

    static CompositeIndex indexModule(String moduleIdentifier, ModuleLoader moduleLoader) throws DeploymentUnitProcessingException {
        ServerLogger.DEPLOYMENT_LOGGER.debugf("Creating annotation index for static module %s", moduleIdentifier);
        try {
            CompositeIndex result;
            Module module = moduleLoader.loadModule(moduleIdentifier);
            // If the module resources include any MANIFEST/jandex.idx files, we only index those resources
            // A module with any jandex.idx files must have them in all resources where index calculation is wanted.
            final CompositeIndex additionalIndex = ModuleIndexBuilder.buildCompositeIndex(module);
            if (additionalIndex != null) {
                result = additionalIndex;
            } else {
                // No MANIFEST/jandex.idx files. The fact that we were calle indicates some deployment
                // wants this module indexed even though it has no jandex.idx files, so we process
                // all class files in the module resources.
                result = calculateModuleIndex(module);
            }
            return result;
        } catch (ModuleLoadException | IOException e) {
            throw ServerLogger.DEPLOYMENT_LOGGER.staticModuleIndexingFailed(moduleIdentifier, e);
        }
    }

    private static CompositeIndex calculateModuleIndex(final Module module) throws ModuleLoadException, IOException {
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
        return new CompositeIndex(Collections.singleton(indexer.complete()));
    }
}
