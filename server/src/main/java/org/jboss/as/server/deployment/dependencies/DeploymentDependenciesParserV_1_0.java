/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.dependencies;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.jbossallxml.JBossAllXMLParser;
import org.jboss.staxmapper.XMLExtendedStreamReader;

import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

/**
 * @author Stuart Douglas
 */
class DeploymentDependenciesParserV_1_0 implements JBossAllXMLParser<DeploymentDependencies> {

    public static final DeploymentDependenciesParserV_1_0 INSTANCE = new DeploymentDependenciesParserV_1_0();
    public static final String NAMESPACE_1_0 = "urn:jboss:deployment-dependencies:1.0";


    enum Element {
        JBOSS_DEPLOYMENT_DEPENDENCIES,
        DEPENDENCY,

        // default unknown element
        UNKNOWN;

        private static final Map<QName, Element> elements;

        static {
            Map<QName, Element> elementsMap = new HashMap<QName, Element>();
            elementsMap.put(new QName(NAMESPACE_1_0, "jboss-deployment-dependencies"), Element.JBOSS_DEPLOYMENT_DEPENDENCIES);
            elementsMap.put(new QName(NAMESPACE_1_0, "dependency"), Element.DEPENDENCY);
            elements = elementsMap;
        }

        static Element of(QName qName) {
            QName name;
            if (qName.getNamespaceURI().equals("")) {
                name = new QName(NAMESPACE_1_0, qName.getLocalPart());
            } else {
                name = qName;
            }
            final Element element = elements.get(name);
            return element == null ? UNKNOWN : element;
        }
    }

    enum Attribute {
        UNKNOWN(null),

        NAME("name"),;

        private final String name;

        Attribute(final String name) {
            this.name = name;
        }

        /**
         * Get the local name of this attribute.
         *
         * @return the local name
         */
        public String getLocalName() {
            return name;
        }

        private static final Map<String, Attribute> MAP;

        static {
            final Map<String, Attribute> map = new HashMap<String, Attribute>();
            for (Attribute element : values()) {
                final String name = element.getLocalName();
                if (name != null)
                    map.put(name, element);
            }
            MAP = map;
        }

        public static Attribute forName(String localName) {
            final Attribute element = MAP.get(localName);
            return element == null ? UNKNOWN : element;
        }

        @Override
        public String toString() {
            return getLocalName();
        }
    }

    @Override
    public DeploymentDependencies parse(final XMLExtendedStreamReader reader, final DeploymentUnit deploymentUnit) throws XMLStreamException {
        final DeploymentDependencies dependencies = new DeploymentDependencies();
        final int count = reader.getAttributeCount();
        if (count != 0) {
            throw ParseUtils.unexpectedAttribute(reader, 0);
        }
        // xsd:sequence
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return dependencies;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    final Element element = Element.of(reader.getName());
                    switch (element) {
                        case DEPENDENCY:
                            parseDependency(reader, dependencies);
                            break;
                        default:
                            throw ParseUtils.unexpectedElement(reader);
                    }
                    break;
                }
            }
        }
        throw ParseUtils.unexpectedEndElement(reader);
    }

    private void parseDependency(final XMLExtendedStreamReader reader, final DeploymentDependencies dependencies) throws XMLStreamException {

        final int count = reader.getAttributeCount();
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME:
                    name = value;
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        dependencies.getDependencies().add(name);

        if (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
            throw unexpectedElement(reader);
        }
    }
}
