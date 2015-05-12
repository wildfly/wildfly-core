/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.controller.parsing.WriteUtils.writeAttribute;

import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.server.controller.resources.SystemPropertyResourceDefinition;
import org.jboss.as.server.operations.SystemPropertyAddHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Parsing and marshalling logic specific to system properties.
 *
 * The contents of this file have been pulled from {@see CommonXml}, see the commit history of that file for true author
 * attribution.
 *
 * Note: This class is only indented to support versions 1, 2, and 3 of the schema, if later major versions of the schema
 * include updates to the types represented by this class then this class should be forked.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SystemPropertiesXml {

    void parseSystemProperties(final XMLExtendedStreamReader reader, final ModelNode address,
            final Namespace expectedNs, final List<ModelNode> updates, boolean standalone) throws XMLStreamException {

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            if (element != Element.PROPERTY) {
                throw unexpectedElement(reader);
            }

            boolean setName = false;
            boolean setValue = false;
            boolean setBoottime = false;
            // Will set OP_ADDR after parsing the NAME attribute
            ModelNode op = Util.getEmptyOperation(SystemPropertyAddHandler.OPERATION_NAME, new ModelNode());
            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {
                final String val = reader.getAttributeValue(i);
                if (!isNoNamespaceAttribute(reader, i)) {
                    throw unexpectedAttribute(reader, i);
                } else {
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));

                    switch (attribute) {
                        case NAME: {
                            if (setName) {
                                throw ParseUtils.duplicateAttribute(reader, NAME);
                            }
                            setName = true;
                            ModelNode addr = new ModelNode().set(address).add(SYSTEM_PROPERTY, val);
                            op.get(OP_ADDR).set(addr);
                            break;
                        }
                        case VALUE: {
                            if (setValue) {
                                throw ParseUtils.duplicateAttribute(reader, VALUE);
                            }
                            setValue = true;
                            SystemPropertyResourceDefinition.VALUE.parseAndSetParameter(val, op, reader);
                            break;
                        }
                        case BOOT_TIME: {
                            if (standalone) {
                                throw unexpectedAttribute(reader, i);
                            }
                            if (setBoottime) {
                                throw ParseUtils.duplicateAttribute(reader, BOOT_TIME);
                            }
                            setBoottime = true;
                            SystemPropertyResourceDefinition.BOOT_TIME.parseAndSetParameter(val, op, reader);
                            break;
                        }
                        default: {
                            throw unexpectedAttribute(reader, i);
                        }
                    }
                }
            }
            requireNoContent(reader);
            if (!setName) {
                throw ParseUtils.missingRequired(reader, Collections.singleton(NAME));
            }

            updates.add(op);
        }
    }

    void writeProperties(final XMLExtendedStreamWriter writer, final ModelNode modelNode, Element element,
            boolean standalone) throws XMLStreamException {
        final List<Property> properties = modelNode.asPropertyList();
        if (properties.size() > 0) {
            writer.writeStartElement(element.getLocalName());
            for (Property prop : properties) {
                writer.writeStartElement(Element.PROPERTY.getLocalName());
                writeAttribute(writer, Attribute.NAME, prop.getName());
                ModelNode sysProp = prop.getValue();
                SystemPropertyResourceDefinition.VALUE.marshallAsAttribute(sysProp, writer);
                if (!standalone) {
                    SystemPropertyResourceDefinition.BOOT_TIME.marshallAsAttribute(sysProp, writer);
                }

                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

}
