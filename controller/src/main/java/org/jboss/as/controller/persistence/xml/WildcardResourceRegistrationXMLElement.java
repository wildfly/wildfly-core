/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence.xml;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.FeatureFilter;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.WildcardResourceRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.xml.QNameResolver;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLContent;
import org.jboss.as.controller.xml.XMLContentWriter;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.Assert;

/**
 * Encapsulates an XML element for a wildcard resource registration.
 * @author Paul Ferraro
 */
public interface WildcardResourceRegistrationXMLElement extends ResourceRegistrationXMLElement {

    QName getPathValueAttributeName();

    interface Builder extends ResourceRegistrationXMLElement.Builder<WildcardResourceRegistrationXMLElement, Builder> {
        /**
         * Overrides the local name of the attribute used to create the path for this resource.
         * Defaults to {@value ModelDescriptionConstants#NAME} if unspecified.
         * @param localName a attribute local name.
         * @return a reference to this builder.
         */
        Builder withPathValueAttributeLocalName(String localName);

        /**
         * Overrides the local name of the attribute used to create the path for this resource.
         * Defaults to {@value ModelDescriptionConstants#NAME} if unspecified.
         * @param name a attribute local name.
         * @return a reference to this builder.
         */
        Builder withPathValueAttributeName(QName name);
    }

    class DefaultBuilder extends ResourceRegistrationXMLElement.AbstractBuilder<WildcardResourceRegistrationXMLElement, Builder> implements Builder {
        static final AttributeParser NO_OP_PARSER = new AttributeParser() {
            @Override
            public void parseAndSetParameter(AttributeDefinition attribute, String value, ModelNode operation, XMLStreamReader reader) throws XMLStreamException {
            }
        };
        private volatile QName pathValueAttributeName;

        DefaultBuilder(WildcardResourceRegistration registration, FeatureFilter filter, QNameResolver resolver) {
            super(registration, filter, resolver);
            Assert.assertTrue(registration.getPathElement().isWildcard());
            this.pathValueAttributeName = resolver.resolve(ModelDescriptionConstants.NAME);
        }

        @Override
        public Builder withPathValueAttributeLocalName(String localName) {
            return this.withPathValueAttributeName(this.resolve(localName));
        }

        @Override
        public Builder withPathValueAttributeName(QName name) {
            this.pathValueAttributeName = name;
            return this;
        }

        @Override
        protected Builder builder() {
            return this;
        }

        @Override
        public WildcardResourceRegistrationXMLElement build() {
            ResourceRegistration registration = this.getResourceRegistration();
            PathElement path = registration.getPathElement();
            QName name = this.getElementName().apply(path);
            QName pathValueAttributeName = this.pathValueAttributeName;

            AttributeDefinition pathValueAttribute = ignoredAttributeDefinitionBuilder(UUID.randomUUID().toString()).setAttributeParser(NO_OP_PARSER).setXmlName(pathValueAttributeName.getLocalPart()).build();
            Collection<AttributeDefinition> attributes = Stream.concat(Stream.of(pathValueAttribute), this.getAttributes().stream()).collect(Collectors.toUnmodifiableList());
            AttributeDefinitionXMLConfiguration configuration = this.getConfiguration();

            XMLCardinality cardinality = this.getCardinality();
            XMLElementReader<ModelNode> attributesReader = new ResourceAttributesXMLContentReader(attributes, configuration);
            XMLContentWriter<Property> attributesWriter = new ResourcePropertyAttributesXMLContentWriter(pathValueAttributeName, attributes, configuration);
            XMLContent<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> content = this.getContent();

            XMLElementReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> resourceReader = new ResourceXMLContainerReader(name, attributesReader, content);
            XMLContentWriter<Property> resourceWriter = new ResourceXMLContainerWriter<>(name, attributesWriter, Property::getValue, content);

            BiConsumer<Map<PathAddress, ModelNode>, PathAddress> operationTransformation = this.getOperationTransformation();
            XMLElementReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> elementReader = new XMLElementReader<>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, Map.Entry<PathAddress, Map<PathAddress, ModelNode>> context) throws XMLStreamException {
                    String value = reader.getAttributeValue(null, pathValueAttributeName.getLocalPart());
                    if (value == null) {
                        throw ParseUtils.missingRequired(reader, pathValueAttributeName.getLocalPart());
                    }

                    PathAddress parentOperationKey = context.getKey();
                    Map<PathAddress, ModelNode> operations = context.getValue();

                    ModelNode parentOperation = (parentOperationKey.size() > 0) ? operations.get(parentOperationKey) : null;
                    PathAddress parentAddress = (parentOperation != null) ? PathAddress.pathAddress(parentOperation.get(ModelDescriptionConstants.OP_ADDR)) : PathAddress.EMPTY_ADDRESS;
                    PathElement operationPath = PathElement.pathElement(path.getKey(), value);
                    PathAddress operationAddress = parentAddress.append(operationPath);
                    PathAddress operationKey = parentOperationKey.append(operationPath);
                    ModelNode operation = Util.createAddOperation(operationAddress);
                    operations.put(operationKey, operation);

                    resourceReader.readElement(reader, Map.entry(operationKey, operations));
                    operationTransformation.accept(operations, operationKey);
                }
            };
            XMLContentWriter<ModelNode> elementWriter = new XMLContentWriter<>() {
                @Override
                public void writeContent(XMLExtendedStreamWriter writer, ModelNode parentModel) throws XMLStreamException {
                    String key = path.getKey();
                    if (parentModel.hasDefined(key)) {
                        for (Property property : parentModel.get(key).asPropertyList()) {
                            resourceWriter.writeContent(writer, property);
                        }
                    }
                }

                @Override
                public boolean isEmpty(ModelNode parentModel) {
                    return !parentModel.hasDefined(path.getKey()) || parentModel.get(path.getKey()).asPropertyListOrEmpty().isEmpty();
                }
            };
            return new DefaultWildcardResourceRegistrationXMLElement(registration, name, pathValueAttributeName, cardinality, elementReader, elementWriter);
        }
    }

    class DefaultWildcardResourceRegistrationXMLElement extends DefaultResourceRegistrationXMLElement implements WildcardResourceRegistrationXMLElement {
        private final QName pathValueAttributeName;

        DefaultWildcardResourceRegistrationXMLElement(ResourceRegistration registration, QName name, QName pathValueAttributeName, XMLCardinality cardinality, XMLElementReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> reader, XMLContentWriter<ModelNode> writer) {
            super(registration, name, cardinality, reader, writer);
            this.pathValueAttributeName = pathValueAttributeName;
        }

        @Override
        public QName getPathValueAttributeName() {
            return this.pathValueAttributeName;
        }
    }

    class ResourcePropertyAttributesXMLContentWriter implements XMLContentWriter<Property> {
        private final QName pathValueAttributeName;
        private final XMLContentWriter<ModelNode> attributesWriter;

        ResourcePropertyAttributesXMLContentWriter(QName pathValueAttributeName, Collection<AttributeDefinition> attributes, AttributeDefinitionXMLConfiguration configuration) {
            this.pathValueAttributeName = pathValueAttributeName;
            this.attributesWriter = new ResourceAttributesXMLContentWriter(attributes, configuration);
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter writer, Property property) throws XMLStreamException {
            String value = property.getName();
            String localName = this.pathValueAttributeName.getLocalPart();
            String namespaceURI = this.pathValueAttributeName.getNamespaceURI();
            if (namespaceURI != XMLConstants.NULL_NS_URI) {
                writer.writeAttribute(namespaceURI, localName, value);
            } else {
                // For PersistentResourceXMLDescription compatibility
                writer.writeAttribute(localName, value);
            }
            this.attributesWriter.writeContent(writer, property.getValue());
        }

        @Override
        public boolean isEmpty(Property property) {
            return this.attributesWriter.isEmpty(property.getValue());
        }
    }
}
