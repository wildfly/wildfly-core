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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.WriteUtils;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Parsing and marshalling logic specific to deployments.
 *
 * The contents of this file have been pulled from {@see CommonXml}, see the commit history of that file for true author
 * attribution.
 *
 * Note: This class is only indented to support versions 1, 2, and 3 of the schema, if later major versions of the schema
 * include updates to the types represented by this class then this class should be forked.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DeploymentsXml {

    void parseDeployments(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs,
            final List<ModelNode> list, final Set<Attribute> allowedAttributes, final Set<Element> allowedElements,
            boolean validateUniqueRuntimeNames) throws XMLStreamException {
        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();
        final Set<String> runtimeNames = validateUniqueRuntimeNames ? new HashSet<String>() : null;

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            Element deployment = Element.forName(reader.getLocalName());
            if (Element.DEPLOYMENT != deployment) {
                throw unexpectedElement(reader);
            }
            final ModelNode deploymentAdd = Util.getEmptyOperation(ADD, null);

            // Handle attributes
            String uniqueName = null;
            String runtimeName = null;
            boolean enabled = false;
            Set<Attribute> requiredAttributes = EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME);
            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {
                final String value = reader.getAttributeValue(i);
                if (!isNoNamespaceAttribute(reader, i)) {
                    throw unexpectedAttribute(reader, i);
                } else {
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                    if (!allowedAttributes.contains(attribute)) {
                        throw unexpectedAttribute(reader, i);
                    }
                    requiredAttributes.remove(attribute);
                    switch (attribute) {
                        case NAME: {
                            if (!names.add(value)) {
                                throw duplicateNamedElement(reader, value);
                            }
                            uniqueName = value;
                            break;
                        }
                        case RUNTIME_NAME: {
                            DeploymentAttributes.RUNTIME_NAME.parseAndSetParameter(value, deploymentAdd, reader);
                            runtimeName = value;
                            break;
                        }
                        case ENABLED: {
                            DeploymentAttributes.ENABLED.parseAndSetParameter(value, deploymentAdd, reader);
                            enabled = deploymentAdd.get(DeploymentAttributes.ENABLED.getName()).asBoolean(false);
                            break;
                        }
                        default:
                            throw unexpectedAttribute(reader, i);
                    }
                }
            }
            if (requiredAttributes.size() > 0) {
                throw missingRequired(reader, requiredAttributes);
            }

            if (validateUniqueRuntimeNames && enabled && runtimeName != null && !runtimeNames.add(runtimeName)) {
                throw duplicateNamedElement(reader, runtimeName);
            }

            final ModelNode deploymentAddress = address.clone().add(DEPLOYMENT, uniqueName);
            deploymentAdd.get(OP_ADDR).set(deploymentAddress);

            // Deal with the mismatch between the xsd default value for 'enabled' and the mgmt API value
            if (allowedAttributes.contains(Attribute.ENABLED)
                    && !deploymentAdd.hasDefined(DeploymentAttributes.ENABLED.getName())) {
                deploymentAdd.get(DeploymentAttributes.ENABLED.getName()).set(true);
            }

            // Handle elements
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                requireNamespace(reader, expectedNs);
                final Element element = Element.forName(reader.getLocalName());
                if (!allowedElements.contains(element)) {
                    throw unexpectedElement(reader);
                }
                switch (element) {
                    case CONTENT:
                        parseContentType(reader, deploymentAdd);
                        break;
                    case FS_ARCHIVE:
                        parseFSBaseType(reader, deploymentAdd, true);
                        break;
                    case FS_EXPLODED:
                        parseFSBaseType(reader, deploymentAdd, false);
                        break;
                    default:
                        throw unexpectedElement(reader);
                }
            }

            list.add(deploymentAdd);
        }
    }

    private void parseContentType(final XMLExtendedStreamReader reader, final ModelNode parent) throws XMLStreamException {
        final ModelNode content = parent.get("content").add();
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            final String value = reader.getAttributeValue(i);
            switch (attribute) {
                case SHA1:
                    try {
                        content.get(HASH).set(HashUtil.hexStringToByteArray(value));
                    } catch (final Exception e) {
                        throw ControllerLogger.ROOT_LOGGER.invalidSha1Value(e, value, attribute.getLocalName(), reader.getLocation());
                    }
                    break;
                case ARCHIVE:
                    DeploymentAttributes.CONTENT_ARCHIVE.getParser().parseAndSetParameter(DeploymentAttributes.CONTENT_ARCHIVE, value, content, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        // Handle elements
        requireNoContent(reader);
    }

    private void parseFSBaseType(final XMLExtendedStreamReader reader, final ModelNode parent, final boolean isArchive)
            throws XMLStreamException {
        final ModelNode content = parent.get("content").add();
        content.get(ARCHIVE).set(isArchive);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            final String value = reader.getAttributeValue(i);
            switch (attribute) {
                case PATH:
                    content.get(PATH).set(value);
                    break;
                case RELATIVE_TO:
                    content.get(RELATIVE_TO).set(value);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        // Handle elements
        requireNoContent(reader);
    }

    static void writeContentItem(final XMLExtendedStreamWriter writer, final ModelNode contentItem)
            throws XMLStreamException {
        if (contentItem.has(HASH)) {
            WriteUtils.writeElement(writer, Element.CONTENT);
            WriteUtils.writeAttribute(writer, Attribute.SHA1, HashUtil.bytesToHexString(contentItem.require(HASH).asBytes()));
            DeploymentAttributes.CONTENT_ARCHIVE.getMarshaller().marshallAsAttribute(DeploymentAttributes.CONTENT_ARCHIVE, contentItem, true, writer);
            writer.writeEndElement();
        } else {
            if (contentItem.require(ARCHIVE).asBoolean()) {
                WriteUtils.writeElement(writer, Element.FS_ARCHIVE);
            } else {
                WriteUtils.writeElement(writer, Element.FS_EXPLODED);
            }
            WriteUtils.writeAttribute(writer, Attribute.PATH, contentItem.require(PATH).asString());
            if (contentItem.has(RELATIVE_TO))
                WriteUtils.writeAttribute(writer, Attribute.RELATIVE_TO, contentItem.require(RELATIVE_TO).asString());
            writer.writeEndElement();
        }
    }
}
