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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.jboss.staxmapper.XMLMapper;

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

    private final DeferredExtensionContext deferredExtensionContext;

    public ExtensionXml(DeferredExtensionContext deferredExtensionContext) {
        this.deferredExtensionContext = deferredExtensionContext;
        this.moduleLoader = null;
        this.bootExecutor = null;
        this.extensionRegistry = null;
    }
    public ExtensionXml(final ModuleLoader loader, final ExecutorService executorService, final ExtensionRegistry extensionRegistry) {
        this.moduleLoader = loader;
        this.bootExecutor = executorService;
        this.extensionRegistry = extensionRegistry;
        this.deferredExtensionContext = null;
    }

    public void writeExtensions(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {
        Set<String> keys = modelNode.keys();
        if (keys.size() > 0) {
            if (isOrderExtensions()) {
                keys = new TreeSet<>(keys);
            }
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
        DeferredExtensionContext ctx = this.deferredExtensionContext;
        if(ctx == null) {
            ctx = new DeferredExtensionContext(moduleLoader, extensionRegistry, bootExecutor);
        }
        long start = System.currentTimeMillis();

        requireNoAttributes(reader);

        final Set<String> found = new HashSet<String>();

        final XMLMapper xmlMapper = reader.getXMLMapper();

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
            ctx.addExtension(moduleName, xmlMapper);
            addExtensionAddOperation(address, list, moduleName);
        }

        if(deferredExtensionContext == null) {
            ctx.load();
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

    public static boolean isOrderExtensions() {
        // TODO perhaps make this configurable
        return true;
    }

}
