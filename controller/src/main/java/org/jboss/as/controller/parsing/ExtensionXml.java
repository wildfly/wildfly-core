/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.jboss.staxmapper.XMLMapper;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Parsing and marshalling logic related to the {@code extension} element in standalone.xml and domain.xml.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ExtensionXml {

    private final ModuleLoader moduleLoader;
    private final ExecutorService bootExecutor;
    private final ExtensionRegistry extensionRegistry;

    /**
     * Create a parser/marshaller for extension elements.
     *
     * @param loader loader for modules declared in the extension elements. Cannot be {@code null}
     * @param executorService executor to handle concurrent
     *                        {@link Extension#initializeParsers(ExtensionParsingContext) extension initialization}
     *                        tasks. May be {@code null} in which case no concurrent initialization will be done
     * @param extensionRegistry registry for initialized extensions. Cannot be {@code null}
     */
    public ExtensionXml(final ModuleLoader loader, final ExecutorService executorService, final ExtensionRegistry extensionRegistry) {
        this.moduleLoader = loader;
        this.bootExecutor = executorService;
        this.extensionRegistry = extensionRegistry;
    }

    public void writeExtensions(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {
        Set<String> keys = modelNode.keys();
        if (keys.size() > 0) {
            writer.writeStartElement(Element.EXTENSIONS.getLocalName());
            for (final String extension : keys) {
                writer.writeEmptyElement(Element.EXTENSION.getLocalName());
                writer.writeAttribute(Attribute.MODULE.getLocalName(), extension);
            }
            writer.writeEndElement();
        }
    }

    public void parseExtensions(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list)
            throws XMLStreamException {

        long start = System.currentTimeMillis();

        requireNoAttributes(reader);

        final Set<String> found = new HashSet<String>();

        final XMLMapper xmlMapper = reader.getXMLMapper();

        int exRegMax = extensionRegistry.getMaxParallelBootTasks();
        // We do twice as many tasks as the subsystem mgmt ops can since mgmt ops use 2 threads per chunk
        int maxInitializationTasks = exRegMax > 1 ? exRegMax * 2 : exRegMax;
        final GroupLoadTask[] loadTasks = bootExecutor != null && maxInitializationTasks > 1
                ? new GroupLoadTask[maxInitializationTasks] : null;

        int taskIdx = -1;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            if (element != Element.EXTENSION) {
                throw unexpectedElement(reader);
            }

            // One attribute && require no content
            final String moduleName = readStringAttributeElement(reader, Attribute.MODULE.getLocalName());

            if (!found.add(moduleName)) {
                // duplicate module name
                throw ControllerLogger.ROOT_LOGGER.duplicateExtensionElement(Element.EXTENSION.getLocalName(), Attribute.MODULE.getLocalName(), moduleName, reader.getLocation());
            }

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

            addExtensionAddOperation(address, list, moduleName);
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

        long elapsed = System.currentTimeMillis() - start;
        if (ROOT_LOGGER.isDebugEnabled()) {
            ROOT_LOGGER.debugf("Parsed extensions in [%d] ms", elapsed);
        }
    }

    private void addExtensionAddOperation(ModelNode address, List<ModelNode> list, String moduleName) {
        final ModelNode add = new ModelNode();
        add.get(OP_ADDR).set(address).add(EXTENSION, moduleName);
        add.get(OP).set(ADD);
        list.add(add);
    }

    private XMLStreamException loadModule(final String moduleName, final XMLMapper xmlMapper) throws XMLStreamException {
        // Register element handlers for this extension
        try {
            final Module module = moduleLoader.loadModule(ModuleIdentifier.fromString(moduleName));
            boolean initialized = false;
            for (final Extension extension : module.loadService(Extension.class)) {
                ClassLoader oldTccl = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(extension.getClass());
                try {
                    extension.initializeParsers(extensionRegistry.getExtensionParsingContext(moduleName, xmlMapper));
                } finally {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
                }
                if (!initialized) {
                    initialized = true;
                }
            }
            if (!initialized) {
                throw ControllerLogger.ROOT_LOGGER.notFound("META-INF/services/", Extension.class.getName(), module.getIdentifier());
            }
            return null;
        } catch (final ModuleLoadException e) {
            throw ControllerLogger.ROOT_LOGGER.failedToLoadModule(e);
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
