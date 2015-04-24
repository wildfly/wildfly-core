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

package org.jboss.as.controller;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
public class NestGroupedAttributesXMLDescription extends AbstractAttributesXMLDescription {

    final Node root = new Node(null);

    @Override
    public void registerAttribute(final AttributeDefinition attribute) {
        if (attribute.getAttributeGroup() == null) {
            if (attribute instanceof PropertiesAttributeDefinition) {
                registerPropertyAttribute(attribute);
            } else {
                root.addAttribute(attribute);
            }
        } else {
            registerGroupedAttribute(attribute);
        }
    }

    private void registerGroupedAttribute(final AttributeDefinition attribute) {
        Node n = root;
        for (final String element : attribute.getAttributeGroup()) {
            if (n == root) {
                tags.add(element);
            }
            Node t = n.getChild(element);
            if (t == null) {
                t = n.addChild(element);
            }
            n = t;
        }
        n.addAttribute(attribute);
    }

    @Override
    public void parseTag(final String tag, final XMLExtendedStreamReader reader,
            final Map<String, AttributeParser> attributeParsers, ModelNode operation) throws XMLStreamException {
        final Node node = root.getChild(tag);
        if (node != null) {
            parseNode(reader, attributeParsers, node, operation);
        } else {
            parseProperty(tag, reader, operation);
        }
    }

    private void parseNode(final XMLExtendedStreamReader reader, final Map<String, AttributeParser> attributeParsers,
                           final Node node, final ModelNode operation) throws XMLStreamException {
        final Map<String, AttributeDefinition> attributes = node.getAttributes();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            final String attributeName = reader.getAttributeLocalName(i);
            final String value = reader.getAttributeValue(i);
            if (attributes.containsKey(attributeName)) {
                final AttributeDefinition attribute = attributes.get(attributeName);
                final AttributeParser parser = attributeParsers.containsKey(attributeName) ? attributeParsers.get(attributeName)
                        : attribute.getParser();
                assert parser != null;
                parser.parseAndSetParameter(attribute, value, operation, reader);
            } else {
                throw ParseUtils.unexpectedAttribute(reader, i, attributes.keySet());
            }
        }
        while (reader.hasNext()) {
            if (reader.nextTag() == XMLStreamConstants.END_ELEMENT) {
                // break the loop at the end of the parent element
                if (node.element.equals(reader.getLocalName())) {
                    break;
                }
                // else continue to the next children
                continue;
            }
            final Node child = node.getChild(reader.getLocalName());
            if (child != null) {
                parseNode(reader, attributeParsers, child, operation);
            } else {
                throw ParseUtils.unexpectedElement(reader, node.children.keySet());
            }
        }
    }

    @Override
    public boolean willCreateTags(final ModelNode resourceModel, final boolean marshallDefault) {
        final boolean groupedAttributesTags = (checkNodeXML(root, resourceModel, marshallDefault) == NodeXML.NESTED_TAGS);
        final boolean propertiesTags = willCreatePropertyTags(resourceModel);
        return groupedAttributesTags || propertiesTags;
    }

    @Override
    public void persist(final XMLExtendedStreamWriter writer, final ModelNode resourceModel,
            final Map<String, AttributeMarshaller> attributeMarshallers, final boolean marshallDefault) throws XMLStreamException {
        final Collection<AttributeDefinition> rootAttributes = root.getAttributes().values();
        persistAttributes(rootAttributes, writer, resourceModel, attributeMarshallers, marshallDefault);
        for (final Node node : root.getChildren()) {
            persistNode(node, writer, resourceModel, attributeMarshallers, marshallDefault);
        }
        final Collection<PropertiesAttributeDefinition> properties = propertyAttributes.values();
        persistAttributes(properties, writer, resourceModel, attributeMarshallers, marshallDefault);
    }

    private void persistNode(final Node node, final XMLExtendedStreamWriter writer, final ModelNode resourceModel,
            final Map<String, AttributeMarshaller> attributeMarshallers, final boolean marshallDefault)
            throws XMLStreamException {
        final NodeXML nodeXML = checkNodeXML(node, resourceModel, marshallDefault);
        if (nodeXML == NodeXML.NESTED_TAGS) {
            writer.writeStartElement(node.getElement());
        } else if (nodeXML == NodeXML.EMPTY_TAG) {
            writer.writeEmptyElement(node.getElement());
        }

        final Collection<AttributeDefinition> nodeAttributes = node.getAttributes().values();
        persistAttributes(nodeAttributes, writer, resourceModel, attributeMarshallers, marshallDefault);
        for (final Node child : node.getChildren()) {
            persistNode(child, writer, resourceModel, attributeMarshallers, marshallDefault);
        }

        if (nodeXML == NodeXML.NESTED_TAGS) {
            writer.writeEndElement();
        }
    }

    private NodeXML checkNodeXML(final Node node, final ModelNode model, final boolean marshalDefault){
        for(final Node child: node.getChildren()){
            if(checkNodeXML(child, model, marshalDefault)!=NodeXML.NONE){
                return NodeXML.NESTED_TAGS;
            }
        }
        for(final AttributeDefinition attribute: node.getAttributes().values()){
            final AttributeMarshaller marshaller=attribute.getAttributeMarshaller();
            if(marshaller.isMarshallable(attribute, model, marshalDefault)){
                return NodeXML.EMPTY_TAG;
            }
        }
        return NodeXML.NONE;
    }

    @Override
    public Map<String, AttributeDefinition> getRootAttributes() {
        return root.getAttributes();
    }

    private class Node {
        private final String element;
        private final Map<String, Node> children = new LinkedHashMap<>();
        private final Map<String, AttributeDefinition> attributes = new LinkedHashMap<>();

        public Node(final String element) {
            this.element = element;
        }

        public String getElement() {
            return element;
        }

        public Node getChild(final String child) {
            return children.get(child);
        }

        public Collection<Node> getChildren() {
            return children.values();
        }

        public Node addChild(final String name) {
            final Node child = new Node(name);
            children.put(name, child);
            return child;
        }

        public Map<String, AttributeDefinition> getAttributes() {
            return attributes;
        }

        public void addAttribute(final AttributeDefinition attribute) {
            attributes.put(attribute.getXmlName(), attribute);
        }
     }

    private enum NodeXML {
        NESTED_TAGS,
        EMPTY_TAG,
        NONE
    }
}