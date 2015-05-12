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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.controller.parsing.WriteUtils.writeAttribute;
import static org.jboss.as.controller.parsing.WriteUtils.writeNewLine;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Parsing and marshalling logic specific to deployment overlays.
 *
 * The contents of this file have been pulled from {@see CommonXml}, see the commit history of that file for true author
 * attribution.
 *
 * Note: This class is only indented to support versions 1, 2, and 3 of the schema, if later major versions of the schema
 * include updates to the types represented by this class then this class should be forked.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DeploymentOverlaysXml {

    void parseDeploymentOverlays(final XMLExtendedStreamReader reader, final Namespace namespace, final ModelNode baseAddress, final List<ModelNode> list, final boolean allowContent, final boolean allowDeployment) throws XMLStreamException {
        requireNoAttributes(reader);

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());

            switch (element) {
                case DEPLOYMENT_OVERLAY:
                    parseDeploymentOverlay(reader, baseAddress, list, allowContent, allowDeployment);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseDeploymentOverlay(final XMLExtendedStreamReader reader, final ModelNode baseAddress, final List<ModelNode> list, final boolean allowContent, final boolean allowDeployment) throws XMLStreamException {

        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        String name = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }


        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }
        ModelNode addr = baseAddress.clone();
        addr.add(DEPLOYMENT_OVERLAY, name);

        final ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).set(addr);
        list.add(op);

        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if(element == Element.CONTENT && allowContent) {
                parseContentOverride(name, reader, baseAddress, list);
            } else if(element == Element.DEPLOYMENT && allowDeployment) {
                parseDeploymentOverlayDeployment(name, reader, baseAddress, list);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void parseContentOverride(final String name, final XMLExtendedStreamReader reader, final ModelNode baseAddress, final List<ModelNode> list) throws XMLStreamException {

        final EnumSet<Attribute> required = EnumSet.of(Attribute.PATH, Attribute.CONTENT);
        String path = null;
        byte[] content = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case PATH: {
                    path = value;
                    break;
                }
                case CONTENT: {
                    content = HashUtil.hexStringToByteArray(value);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        requireNoContent(reader);


        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        final ModelNode address = baseAddress.clone();
        address.add(DEPLOYMENT_OVERLAY, name);
        address.add(CONTENT, path);

        final ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).set(address);
        op.get(CONTENT).get(HASH).set(content);
        list.add(op);

    }

    private void parseDeploymentOverlayDeployment(final String name, final XMLExtendedStreamReader reader, final ModelNode baseAddress, final List<ModelNode> list) throws XMLStreamException {

        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        String depName = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    depName = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        requireNoContent(reader);


        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        final ModelNode address = baseAddress.clone();
        address.add(DEPLOYMENT_OVERLAY, name);
        address.add(DEPLOYMENT, depName);

        final ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).set(address);
        list.add(op);

    }

    void writeDeploymentOverlays(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {

        Set<String> names = modelNode.keys();
        if (names.size() > 0) {
            writer.writeStartElement(Element.DEPLOYMENT_OVERLAYS.getLocalName());
            for (String uniqueName : names) {
                final ModelNode contentItem = modelNode.get(uniqueName);
                writer.writeStartElement(Element.DEPLOYMENT_OVERLAY.getLocalName());
                writeAttribute(writer, Attribute.NAME, uniqueName);

                if (contentItem.hasDefined(CONTENT)) {
                    final ModelNode overridesNode = contentItem.get(CONTENT);

                    final Set<String> overrides = overridesNode.keys();
                    for (final String override : overrides) {
                        final ModelNode overrideNode = overridesNode.get(override);
                        final String content = HashUtil.bytesToHexString(overrideNode.require(CONTENT).asBytes());
                        writer.writeStartElement(Element.CONTENT.getLocalName());
                        writeAttribute(writer, Attribute.PATH, override);
                        writeAttribute(writer, Attribute.CONTENT, content);
                        writer.writeEndElement();
                    }
                }

                if (contentItem.hasDefined(DEPLOYMENT)) {
                    final ModelNode deployments = contentItem.get(DEPLOYMENT);
                    Set<String> deploymentNames = deployments.keys();
                    if (deploymentNames.size() > 0) {
                        for (String deploymentName : deploymentNames) {
                            final ModelNode depNode = deployments.get(deploymentName);
                            writer.writeStartElement(Element.DEPLOYMENT.getLocalName());
                            writeAttribute(writer, Attribute.NAME, deploymentName);
                            writer.writeEndElement();
                        }
                    }
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writeNewLine(writer);
        }
    }

}
