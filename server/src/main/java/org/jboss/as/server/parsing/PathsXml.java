/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Parsing and marshalling logic specific to vault definitions.
 *
 * The contents of this file have been pulled from {@see CommonXml}, see the commit history of that file for true author
 * attribution.
 *
 * Note: This class is only indented to support versions 1, 2, and 3 of the schema, if later major versions of the schema
 * include updates to the types represented by this class then this class should be forked.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class PathsXml {

    /**
     * The restricted path names.
     */
    protected static final Set<String> RESTRICTED_PATHS;

    static {

        final HashSet<String> set = new HashSet<String>(10);
        // Define the restricted path names.
        set.add("jboss.home");
        set.add("jboss.home.dir");
        set.add("user.home");
        set.add("user.dir");
        set.add("java.home");
        set.add("jboss.server.base.dir");
        set.add("jboss.server.data.dir");
        set.add("jboss.server.log.dir");
        set.add("jboss.server.temp.dir");
        // NOTE we actually don't create services for the following
        // however the names remain restricted for use in the configuration
        set.add("jboss.modules.dir");
        set.add("jboss.server.deploy.dir");
        set.add("jboss.domain.servers.dir");
        RESTRICTED_PATHS = Collections.unmodifiableSet(set);
    }

    void parsePaths(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs,
            final List<ModelNode> list, final boolean requirePath) throws XMLStreamException {
        final Set<String> pathNames = new HashSet<String>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            requireNamespace(reader, expectedNs);

            switch (element) {
                case PATH: {
                    parsePath(reader, address, list, requirePath, pathNames);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parsePath(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list,
            final boolean requirePath, final Set<String> defined) throws XMLStreamException {
        String name = null;
        ModelNode path = null;
        String relativeTo = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME: {
                        name = value.trim();
                        if (RESTRICTED_PATHS.contains(value)) {
                            throw ControllerLogger.ROOT_LOGGER.reserved(name, reader.getLocation());
                        }
                        if (!defined.add(name)) {
                            throw ControllerLogger.ROOT_LOGGER.alreadyDefined(name, reader.getLocation());
                        }
                        break;
                    }
                    case PATH: {
                        path = ParseUtils.parsePossibleExpression(value);
                        break;
                    }
                    case RELATIVE_TO: {
                        relativeTo = value;
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }
        if (name == null) {
            throw missingRequired(reader, Collections.singleton(Attribute.NAME));
        }
        if (requirePath && path == null) {
            throw missingRequired(reader, Collections.singleton(Attribute.PATH));
        }
        requireNoContent(reader);
        final ModelNode update = new ModelNode();
        update.get(OP_ADDR).set(address).add(ModelDescriptionConstants.PATH, name);
        update.get(OP).set(ADD);
        // update.get(NAME).set(name);
        if (path != null)
            update.get(PATH).set(path);
        if (relativeTo != null)
            update.get(RELATIVE_TO).set(relativeTo);
        list.add(update);
    }

    void writePaths(final XMLExtendedStreamWriter writer, final ModelNode node, final boolean namedPath) throws XMLStreamException {
        List<Property> paths = node.asPropertyList();

        for (Iterator<Property> it = paths.iterator(); it.hasNext(); ) {
            ModelNode path = it.next().getValue();

            if (!path.isDefined()) {
                //The runtime resources for the hardcoded paths don't appear in the model
                it.remove();
            }
        }

        if (!paths.isEmpty()) {
            writer.writeStartElement(Element.PATHS.getLocalName());

            for (final Property path : paths) {
                final ModelNode value = path.getValue();
                writer.writeEmptyElement(Element.PATH.getLocalName());
                writer.writeAttribute(Attribute.NAME.getLocalName(), path.getName());
                if (!namedPath || value.get(PATH).isDefined()) {
                    writer.writeAttribute(Attribute.PATH.getLocalName(), value.get(PATH).asString());
                }
                if (value.has(RELATIVE_TO) && value.get(RELATIVE_TO).isDefined()) {
                    writer.writeAttribute(Attribute.RELATIVE_TO.getLocalName(), value.get(RELATIVE_TO).asString());
                }
            }
            writer.writeEndElement();
        }
    }

}
