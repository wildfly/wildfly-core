/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence.xml;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLContentReader;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Reads XML content into a resource operation.
 */
public class ResourceAttributesXMLContentReader implements XMLContentReader<ModelNode> {

    private final Map<QName, Map.Entry<AttributeDefinition, AttributeParser>> attributes;

    ResourceAttributesXMLContentReader(Map<QName, AttributeDefinition> attributes, Function<AttributeDefinition, AttributeParser> parsers) {
        this.attributes = attributes.isEmpty() ? Map.of() : new HashMap<>();
        // Collect only those attributes that will parse as an XML attribute
        for (Map.Entry<QName, AttributeDefinition> entry : attributes.entrySet()) {
            AttributeDefinition attribute = entry.getValue();
            AttributeParser parser = parsers.apply(attribute);
            if (!parser.isParseAsElement()) {
                this.attributes.put(entry.getKey(), Map.entry(attribute, parser));
            }
        }
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
        Set<QName> distinctAttributes = new TreeSet<>(Comparator.comparing(QName::toString));
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            QName name = reader.getAttributeName(i);
            String localName = name.getLocalPart();
            if (!distinctAttributes.add(name)) {
                throw ParseUtils.duplicateAttribute(reader, localName);
            }
            if (name.getNamespaceURI().equals(XMLConstants.NULL_NS_URI)) {
                // Inherit namespace of element, if unspecified
                name = new QName(reader.getNamespaceURI(), localName);
            }
            Map.Entry<AttributeDefinition, AttributeParser> entry = this.attributes.get(name);
            if (entry == null) {
                // Try matching w/out namespace (for PersistentResourceXMLDescription compatibility)
                entry = this.attributes.get(new QName(localName));
                if (entry == null) {
                    throw ParseUtils.unexpectedAttribute(reader, i, this.attributes.keySet().stream().map(QName::getLocalPart).collect(Collectors.toSet()));
                }
            }
            AttributeDefinition attribute = entry.getKey();
            AttributeParser parser = entry.getValue();
            parser.parseAndSetParameter(attribute, reader.getAttributeValue(i), operation, reader);
        }
    }

    @Override
    public XMLCardinality getCardinality() {
        return this.attributes.values().stream().<AttributeDefinition>map(Map.Entry::getKey).noneMatch(AttributeDefinition::isRequired) ? XMLCardinality.Single.OPTIONAL : XMLCardinality.Single.REQUIRED;
    }
}
