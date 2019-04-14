/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
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
            int maxInitializationTasks = extensionRegistry.getMaxParallelBootExtensionTasks();
            final GroupLoadTask[] loadTasks = bootExecutor != null && maxInitializationTasks > 1
                    ? new GroupLoadTask[maxInitializationTasks] : null;

            int taskIdx = -1;
            for(ExtensionData extension : extensions) {
                String moduleName = extension.moduleName;
                XMLMapper xmlMapper = extension.xmlMapper;
                if (loadTasks != null) {
                    // Load the module asynchronously
                    if (taskIdx == maxInitializationTasks - 1) {
                        taskIdx = 0;
                    } else {
                        taskIdx++;
                    }
                    LoadTask loadTask = new LoadTask(moduleName, xmlMapper);
                    if (loadTasks[taskIdx] == null) {
                        loadTasks[taskIdx] = new GroupLoadTask(loadTask);
                    } else {
                        loadTasks[taskIdx].loadTasks.add(loadTask);
                    }
                } else {
                    // Load the module from this thread
                    XMLStreamException xse = loadModule(moduleName, xmlMapper);
                    if (xse != null) {
                        throw xse;
                    }
                }
            }

            if (loadTasks != null) {
                for (GroupLoadTask loadTask : loadTasks) {
                    if (loadTask != null) {
                        loadTask.execute(bootExecutor);
                    }
                }
                for (GroupLoadTask loadTask : loadTasks) {
                    if (loadTask != null) {
                        try {
                            XMLStreamException xse = loadTask.future.get();
                            if (xse != null) {
                                throw xse;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw ControllerLogger.ROOT_LOGGER.moduleLoadingInterrupted(loadTask.currentModule);
                        } catch (ExecutionException e) {
                            throw ControllerLogger.ROOT_LOGGER.failedToLoadModule(e, loadTask.currentModule);
                        }
                    }
                }
            }
        }

    }

    private XMLStreamException loadModule(final String moduleName, final XMLMapper xmlMapper) throws XMLStreamException {
        // Register element handlers for this extension
        try {
            final Module module = moduleLoader.loadModule(ModuleIdentifier.fromString(moduleName));
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
            return null;
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

    private class LoadTask implements Callable<XMLStreamException> {

        private final String moduleName;
        private final XMLMapper xmlMapper;

        private LoadTask(String moduleName, XMLMapper xmlMapper) {
            this.moduleName = moduleName;
            this.xmlMapper = xmlMapper;
        }

        @Override
        public XMLStreamException call() throws Exception {
            return loadModule(moduleName, xmlMapper);
        }
    }

    private static class GroupLoadTask implements Callable<XMLStreamException> {
        private final List<LoadTask> loadTasks = new ArrayList<>();
        private volatile String currentModule;
        private volatile Future<XMLStreamException> future;

        private GroupLoadTask(LoadTask first) {
            this.currentModule = first.moduleName;
            loadTasks.add(first);
        }

        @Override
        public XMLStreamException call() throws Exception {

            for (LoadTask task : loadTasks) {
                currentModule = task.moduleName;
                XMLStreamException ex = task.call();
                if (ex != null) {
                    return ex;
                }
            }
            return null;
        }

        private void execute(ExecutorService executorService) {
            future = executorService.submit(this);
        }
    }
}
