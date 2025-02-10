/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence.xml;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.FeatureFilter;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.xml.QNameResolver;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLContent;
import org.jboss.as.controller.xml.XMLContentReader;
import org.jboss.as.controller.xml.XMLContentWriter;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.function.Functions;

/**
 * Encapsulate an XML element for a subsystem resource.
 * @author Paul Ferraro
 */
public interface ResourceXMLElement extends ResourceRegistration, ResourceModelXMLElement {

    Optional<QName> getPathValueAttributeName();

    interface Builder extends ResourceModelXMLElement.Builder {
        @Override
        Builder withCardinality(XMLCardinality cardinality);

        @Override
        Builder withContent(XMLContent<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> content);

        @Override
        Builder withParsers(Map<AttributeDefinition, AttributeParser> parsers);

        @Override
        Builder withMarshallers(Map<AttributeDefinition, AttributeMarshaller> marshallers);

        @Override
        Builder addAttributes(Iterable<AttributeDefinition> attributes);

        @Override
        Builder ignoreAttribute(String localName);

        @Override
        Builder ignoreAttribute(QName name);

        /**
         * Overrides the key used to index the generated operation.
         * @return a reference to this builder.
         */
        ResourceXMLElement.Builder withOperationKey(PathElement key);

        /**
         * Indicates that this resource is required to be present.
         * This is a convenience method that delegates to {@link #withCardinality(XMLCardinality)}.
         * @return a reference to this builder.
         */
        ResourceXMLElement.Builder require();

        /**
         * Overrides the local name of the attribute used to create the path for this resource.
         * Defaults to {@value ModelDescriptionConstants#NAME} if unspecified.
         * @param localName a attribute local name.
         * @return a reference to this builder.
         */
        ResourceXMLElement.Builder withPathValueAttributeLocalName(String localName);

        /**
         * Overrides the local name of the attribute used to create the path for this resource.
         * Defaults to {@value ModelDescriptionConstants#NAME} if unspecified.
         * @param name a attribute local name.
         * @return a reference to this builder.
         */
        ResourceXMLElement.Builder withPathValueAttributeName(QName name);

        /**
         * Overrides the element local name of this resource.
         * @param localName the local element name override.
         * @return a reference to this builder.
         */
        ResourceXMLElement.Builder withElementLocalName(String localName);

        /**
         * Overrides the logic used to determine the element local name of this resource.
         * @see {@link ResourceXMLElementLocalName}
         * @param localName a function returning the element local name for a given path.
         * @return a reference to this builder.
         */
        ResourceXMLElement.Builder withElementLocalName(Function<PathElement, String> localName);

        /**
         * Overrides the logic used to determine the local element name of this resource.
         * @see {@link ResourceXMLElementLocalName}
         * @param function a function returning the qualified element name for a given path.
         * @return a reference to this builder.
         */
        default ResourceXMLElement.Builder withElementName(QName name) {
            return this.withElementName(new Function<>() {
                @Override
                public QName apply(PathElement path) {
                    return name;
                }
            });
        }

        /**
         * Overrides the logic used to determine the element local name of this resource.
         * @see {@link ResourceXMLElementLocalName}
         * @param name a function returning the qualified element name for a given path.
         * @return a reference to this builder.
         */
        ResourceXMLElement.Builder withElementName(Function<PathElement, QName> name);

        /**
         * Indicates that this element can be omitted if all of its attributes are undefined and any child resources are also empty.
         * @return a reference to this builder.
         */
        ResourceXMLElement.Builder omitIfEmpty();

        /**
         * Indicates that the operation associated with this resource should be discarded.
         * @return a reference to this builder.
         */
        default ResourceXMLElement.Builder thenDiscardOperation() {
            BiConsumer<Map<PathAddress, ModelNode>, PathAddress> remove = Map::remove;
            return this.withOperationTransformation(remove);
        }

        /**
         * Specifies an operation transformation function, applied after this resource and any children are parsed into an {@value ModelDescriptionConstants#ADD} operation.
         * Defaults to {@link UnaryOperator#identity()} if unspecified.
         * If this operator returns null, the {@value ModelDescriptionConstants#ADD} operation will be discarded.
         * @param transformer an operation transformer
         * @return a reference to this builder.
         */
        default ResourceXMLElement.Builder withOperationTransformation(UnaryOperator<ModelNode> transformer) {
            return this.withOperationTransformation(new BiFunction<>() {
                @Override
                public ModelNode apply(PathAddress key, ModelNode operation) {
                    return transformer.apply(operation);
                }
            });
        }

        /**
         * Specifies an operation remapping function, applied after this resource and any children are parsed into an {@value ModelDescriptionConstants#ADD} operation.
         * If this function returns null, the {@value ModelDescriptionConstants#ADD} operation will be discarded.
         * @param remappingFunction a remapping function for the current operation
         * @return a reference to this builder.
         */
        default ResourceXMLElement.Builder withOperationTransformation(BiFunction<PathAddress, ModelNode, ModelNode> remappingFunction) {
            return this.withOperationTransformation(new BiConsumer<>() {
                @Override
                public void accept(Map<PathAddress, ModelNode> operations, PathAddress operationKey) {
                    operations.compute(operationKey, remappingFunction);
                }
            });
        }

        /**
         * Specifies an operation transformation function, applied after this resource and any children are parsed into an {@value ModelDescriptionConstants#ADD} operation.
         * Defaults to {@link Functions#discardingBiConsumer()} if unspecified.
         * @param transformation a consumer accepting all operations and the key of the current operation
         * @return a reference to this builder.
         */
        ResourceXMLElement.Builder withOperationTransformation(BiConsumer<Map<PathAddress, ModelNode>, PathAddress> transformation);

        @Override
        ResourceXMLElement build();
    }

    class DefaultBuilder extends ResourceModelXMLElement.AbstractBuilder<Builder> implements Builder {
        static final AttributeParser NO_OP_PARSER = new AttributeParser() {
            @Override
            public void parseAndSetParameter(AttributeDefinition attribute, String value, ModelNode operation, XMLStreamReader reader) throws XMLStreamException {
            }
        };

        private final ResourceRegistration registration;
        private volatile PathElement operationKey;
        private volatile boolean omitIfEmpty = false;
        private volatile BiConsumer<Map<PathAddress, ModelNode>, PathAddress> operationTransformation = Functions.discardingBiConsumer();
        private volatile Function<PathElement, QName> elementName;
        private volatile Optional<QName> pathValueAttributeName;

        protected DefaultBuilder(ResourceRegistration registration, FeatureFilter filter, QNameResolver resolver) {
            super(filter, resolver, AttributeDefinitionXMLConfiguration.of(resolver));
            this.registration = registration;
            this.operationKey = registration.getPathElement();
            boolean wildcard = this.registration.getPathElement().isWildcard();
            this.withCardinality(wildcard ? XMLCardinality.Unbounded.OPTIONAL : XMLCardinality.Single.OPTIONAL);
            this.elementName = new Function<>() {
                @Override
                public QName apply(PathElement path) {
                    Function<PathElement, String> localName = wildcard ? ResourceXMLElementLocalName.KEY : ResourceXMLElementLocalName.VALUE;
                    return resolver.resolve(localName.apply(path));
                }
            };
            this.pathValueAttributeName = wildcard ? Optional.of(this.resolve(ModelDescriptionConstants.NAME)) : Optional.empty();
        }

        @Override
        protected ResourceXMLElement.Builder builder() {
            return this;
        }

        @Override
        public ResourceXMLElement.Builder withOperationKey(PathElement key) {
            this.operationKey = key;
            return this;
        }

        @Override
        public ResourceXMLElement.Builder withPathValueAttributeLocalName(String localName) {
            return this.withPathValueAttributeName(this.resolve(localName));
        }

        @Override
        public ResourceXMLElement.Builder withPathValueAttributeName(QName name) {
            this.pathValueAttributeName = Optional.of(name);
            return this;
        }

        @Override
        public ResourceXMLElement.Builder withElementLocalName(String localName) {
            return this.withElementName(this.resolve(localName));
        }

        @Override
        public ResourceXMLElement.Builder withElementLocalName(Function<PathElement, String> localName) {
            return this.withElementName(localName.andThen(this::resolve));
        }

        @Override
        public ResourceXMLElement.Builder withElementName(Function<PathElement, QName> elementName) {
            this.elementName = elementName;
            return this;
        }

        @Override
        public ResourceXMLElement.Builder require() {
            return this.withCardinality(this.registration.getPathElement().isWildcard() ? XMLCardinality.Unbounded.REQUIRED : XMLCardinality.Single.REQUIRED);
        }

        @Override
        public ResourceXMLElement.Builder omitIfEmpty() {
            this.omitIfEmpty = true;
            return this;
        }

        @Override
        public ResourceXMLElement.Builder withOperationTransformation(BiConsumer<Map<PathAddress, ModelNode>, PathAddress> transformation) {
            this.operationTransformation = this.operationTransformation.andThen(transformation);
            return this;
        }

        private AttributeDefinition ignoredAttributeDefinition(QName name) {
            return ignoredAttributeDefinitionBuilder(UUID.randomUUID().toString()).setAttributeParser(NO_OP_PARSER).setXmlName(name.getLocalPart()).build();
        }

        @Override
        public ResourceXMLElement build() {
            ResourceRegistration registration = this.registration;
            PathElement path = registration.getPathElement();
            PathElement pathKey = this.operationKey;
            QName name = this.elementName.apply(path);

            // Pseudo attribute for the path value
            Optional<QName> pathValueAttributeName = this.pathValueAttributeName;
            AttributeDefinition pathValueAttribute = pathValueAttributeName.map(this::ignoredAttributeDefinition).orElse(null);

            Collection<AttributeDefinition> attributes = (pathValueAttribute != null) ? Stream.concat(Stream.of(pathValueAttribute), this.getAttributes().stream()).toList() : List.copyOf(this.getAttributes());
            AttributeDefinitionXMLConfiguration configuration = this.getConfiguration();

            XMLCardinality cardinality = this.getCardinality();
            XMLContentReader<ModelNode> attributesReader = new ResourceAttributesXMLContentReader(attributes, configuration);
            XMLContentWriter<Property> attributesWriter = new ResourcePropertyAttributesXMLContentWriter(pathValueAttributeName, attributes, configuration);
            ResourceOperationXMLElement<Property> element = new ResourceOperationXMLElement<>(name, cardinality, attributesReader, attributesWriter, Property::getValue, this.getContent());

            BiConsumer<Map<PathAddress, ModelNode>, PathAddress> operationTransformation = this.operationTransformation;
            XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> elementReader = new XMLContentReader<>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, Map.Entry<PathAddress, Map<PathAddress, ModelNode>> context) throws XMLStreamException {
                    PathAddress parentOperationKey = context.getKey();
                    Map<PathAddress, ModelNode> operations = context.getValue();

                    String pathValueAttributeLocalName = pathValueAttributeName.map(QName::getLocalPart).orElse(null);
                    String value = (pathValueAttributeLocalName != null) ? reader.getAttributeValue(null, pathValueAttributeLocalName) : null;
                    if (path.isWildcard() && (value == null)) {
                        throw ParseUtils.missingRequired(reader, pathValueAttributeLocalName);
                    }

                    ModelNode parentOperation = (parentOperationKey.size() > 0) ? operations.get(parentOperationKey) : null;
                    PathAddress parentAddress = (parentOperation != null) ? PathAddress.pathAddress(parentOperation.get(ModelDescriptionConstants.OP_ADDR)) : PathAddress.EMPTY_ADDRESS;
                    PathAddress operationAddress = parentAddress.append(path.isWildcard() ? PathElement.pathElement(path.getKey(), value) : path);
                    PathAddress operationKey = path.isWildcard() ? operationAddress : parentOperationKey.append(pathKey);
                    ModelNode operation = Util.createAddOperation(operationAddress);
                    operations.put(operationKey, operation);

                    element.getReader().readElement(reader, Map.entry(operationKey, operations));
                    operationTransformation.accept(operations, operationKey);
                }
            };
            boolean omitIfEmpty = this.omitIfEmpty;
            XMLContentWriter<ModelNode> elementWriter = new XMLContentWriter<>() {
                @Override
                public void writeContent(XMLExtendedStreamWriter writer, ModelNode parentModel) throws XMLStreamException {
                    String key = path.getKey();
                    if (parentModel.hasDefined(key)) {
                        ModelNode keyModel = parentModel.get(key);

                        List<Property> properties = path.isWildcard() ? keyModel.asPropertyList() : (keyModel.hasDefined(path.getValue()) ? List.of(new Property(path.getValue(), keyModel.get(path.getValue()))) : List.<Property>of());
                        XMLContentWriter<Property> propertyWriter = element.getWriter();
                        for (Property property : properties) {
                            if (!omitIfEmpty || !propertyWriter.isEmpty(property)) {
                                propertyWriter.writeContent(writer, property);
                            }
                        }
                    }
                }

                @Override
                public boolean isEmpty(ModelNode parentModel) {
                    return !parentModel.hasDefined(path.getKey()) || (!path.isWildcard() && !parentModel.hasDefined(path.getKeyValuePair()));
                }
            };
            return new DefaultResourceXMLElement(this.registration, name, pathValueAttributeName, cardinality, elementReader, elementWriter);
        }
    }

    class DefaultResourceXMLElement extends DefaultXMLElement<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> implements ResourceXMLElement {
        private final Optional<QName> pathValueAttributeName;
        private final ResourceRegistration registration;

        DefaultResourceXMLElement(ResourceRegistration registration, QName name, Optional<QName> pathValueAttributeName, XMLCardinality cardinality, XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> reader, XMLContentWriter<ModelNode> writer) {
            super(name, cardinality, reader, writer);
            this.registration = registration;
            this.pathValueAttributeName = pathValueAttributeName;
        }

        @Override
        public Optional<QName> getPathValueAttributeName() {
            return this.pathValueAttributeName;
        }

        @Override
        public PathElement getPathElement() {
            return this.registration.getPathElement();
        }

        @Override
        public Stability getStability() {
            return this.registration.getStability();
        }
    }

    class ResourcePropertyAttributesXMLContentWriter implements XMLContentWriter<Property> {
        private final Optional<QName> pathValueAttributeName;
        private final XMLContentWriter<ModelNode> attributesWriter;

        ResourcePropertyAttributesXMLContentWriter(Optional<QName> pathValueAttributeName, Collection<AttributeDefinition> attributes, AttributeDefinitionXMLConfiguration configuration) {
            this.pathValueAttributeName = pathValueAttributeName;
            this.attributesWriter = new ResourceAttributesXMLContentWriter(attributes, configuration);
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter writer, Property property) throws XMLStreamException {
            QName pathValueAttributeName = this.pathValueAttributeName.orElse(null);
            if (pathValueAttributeName != null) {
                String value = property.getName();
                String localName = pathValueAttributeName.getLocalPart();
                String namespaceURI = pathValueAttributeName.getNamespaceURI();
                if (namespaceURI != XMLConstants.NULL_NS_URI) {
                    writer.writeAttribute(namespaceURI, localName, value);
                } else {
                    // For PersistentResourceXMLDescription compatibility
                    writer.writeAttribute(localName, value);
                }
            }
            this.attributesWriter.writeContent(writer, property.getValue());
        }

        @Override
        public boolean isEmpty(Property property) {
            return this.pathValueAttributeName.isEmpty() && this.attributesWriter.isEmpty(property.getValue());
        }
    }
}
