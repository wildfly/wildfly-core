/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLMapper;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Class that defers loading parsed extension data until a later time, this allows parse-time system properties to
 * be evaluated before any extensions are loaded
 */
public class DeferredExtensionContext {

    private final ModuleLoader moduleLoader;
    private final ExtensionRegistry extensionRegistry;
    private final ExecutorService bootExecutor;
    private final List<ExtensionData> extensions = new ArrayList<>();
    private boolean loaded;

    public DeferredExtensionContext(ModuleLoader moduleLoader, ExtensionRegistry extensionRegistry, ExecutorService bootExecutor) {
        this.moduleLoader = moduleLoader;
        this.extensionRegistry = extensionRegistry;
        this.bootExecutor = bootExecutor;
    }


    public void load() throws XMLStreamException {
        if (!loaded) {
            loaded = true;

            final Map<String, Future<Void>> loadFutures = bootExecutor != null
                    ? new LinkedHashMap<String, Future<Void>>() : null;

            for(ExtensionData extension : extensions) {
                String moduleName = extension.moduleName;
                XMLMapper xmlMapper = extension.xmlMapper;
                if (loadFutures != null) {
                    // Load the module asynchronously
                    Callable<Void> callable = new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            loadModule(moduleName, xmlMapper);
                            return null;
                        }
                    };
                    Future<Void> future = bootExecutor.submit(callable);
                    loadFutures.put(moduleName, future);
                } else {
                    // Load the module from this thread
                    loadModule(moduleName, xmlMapper);
                }
            }

            if (loadFutures != null) {
                for (Map.Entry<String, Future<Void>> entry : loadFutures.entrySet()) {

                    try {
                        entry.getValue().get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw ControllerLogger.ROOT_LOGGER.moduleLoadingInterrupted(entry.getKey());
                    } catch (ExecutionException e) {
                        throw ControllerLogger.ROOT_LOGGER.failedToLoadModule(e, entry.getKey());
                    }
                }
            }
        }

    }

    private void loadModule(final String moduleName, final XMLMapper xmlMapper) throws XMLStreamException {
        // Register element handlers for this extension
        try {
            final Module module = moduleLoader.loadModule(moduleName);
            boolean initialized = false;
            for (final Extension extension : module.loadService(Extension.class)) {
                ClassLoader oldTccl = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(extension.getClass());
                try {
                    extensionRegistry.initializeParsers(extension, moduleName, xmlMapper);
                } finally {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
                }
                if (!initialized) {
                    initialized = true;
                }
            }
            if (!initialized) {
                throw ControllerLogger.ROOT_LOGGER.notFound("META-INF/services/", Extension.class.getName(), module.getName());
            }
        } catch (final ModuleLoadException e) {
            throw ControllerLogger.ROOT_LOGGER.failedToLoadModule(e);
        }
    }

    public void addExtension(String moduleName, XMLMapper xmlMapper) {
        extensions.add(new ExtensionData(moduleName, xmlMapper));
    }

    private class ExtensionData {
        final String moduleName;
        final XMLMapper xmlMapper;

        private ExtensionData(String moduleName, XMLMapper xmlMapper) {
            this.moduleName = moduleName;
            this.xmlMapper = xmlMapper;
        }
    }
}
