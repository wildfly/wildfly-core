/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.persistence.xml.ResourceXMLElement;
import org.jboss.as.controller.persistence.xml.ResourceXMLElementLocalName;
import org.jboss.as.controller.xml.QNameResolver;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLContent;
import org.jboss.as.controller.xml.XMLElement;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.Namespace;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.function.Functions;

/**
 * A representation of a resource as needed by the XML parser.
 *
 * @author Tomaz Cerar
 * @author Stuart Douglas
 * @deprecated Superseded by {@link ResourceXMLElement}.
 */
@Deprecated(forRemoval = true)
public final class PersistentResourceXMLDescription implements ResourceParser, ResourceMarshaller {

    private final ResourceXMLElement element;

    private PersistentResourceXMLDescription(ResourceXMLElement element) {
        this.element = element;
    }

    public PathElement getPathElement() {
        return this.element.getPathElement();
    }

    public ResourceXMLElement getXMLElement() {
        return this.element;
    }

    /**
     * Parse xml from provided <code>reader</code> and add resulting operations to passed list
     * @param reader xml reader to parse from
     * @param parentAddress address of the parent, used as base for all child elements
     * @param list list of operations where result will be put to.
     * @throws XMLStreamException if any error occurs while parsing
     */
    @Override
    public void parse(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        Map<PathAddress, ModelNode> operations = new LinkedHashMap<>();
        this.element.readElement(reader, Map.entry(parentAddress, operations));
        if (!operations.isEmpty()) {
            for (ModelNode operation : operations.values()) {
                String op = operation.get(ModelDescriptionConstants.OP).asStringOrNull();
                if (op != null) {
                    // Unpack any composite operations
                    if (op.equals(ModelDescriptionConstants.COMPOSITE)) {
                        for (ModelNode step : operation.get(ModelDescriptionConstants.STEPS).asListOrEmpty()) {
                            list.add(step);
                        }
                    } else {
                        list.add(operation);
                    }
                }
            }
        }
    }

    @Override
    public void persist(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
        this.element.writeContent(writer, model);
    }

    public void persist(XMLExtendedStreamWriter writer, ModelNode model, String namespaceURI) throws XMLStreamException {
        this.persist(writer, model);
    }

    public void persistChildren(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates builder for passed path element
     * @param pathElement for which we are creating builder
     * @return PersistentResourceXMLBuilder
     * @deprecated Use {@link Factory#builder(PathElement)} from {@link #factory(PersistentSubsystemSchema)} instead.
     */
    @Deprecated
    public static PersistentResourceXMLBuilder builder(final PathElement pathElement) {
        return new PersistentResourceXMLBuilder(new LegacyBuilder(pathElement).withElementLocalName(pathElement.isWildcard() ? ResourceXMLElementLocalName.KEY : ResourceXMLElementLocalName.VALUE));
    }

    /**
     * Creates builder for passed path element
     *
     * @param pathElement for which we are creating builder
     * @param namespaceURI xml namespace to use for this resource, usually used for top level elements such as subsystems
     * @return PersistentResourceXMLBuilder
     * @deprecated Use {@link Factory#builder(PathElement)} from {@link #factory(PersistentSubsystemSchema)} instead.
     */
    @Deprecated
    public static PersistentResourceXMLBuilder builder(final PathElement pathElement, final String namespaceURI) {
        return new PersistentResourceXMLBuilder(new LegacyBuilder(pathElement, namespaceURI).withElementLocalName(ResourceXMLElementLocalName.KEY));
    }

    /**
     * Creates builder for the given subsystem path and namespace.
     *
     * @param path a subsystem path element
     * @param namespace the subsystem namespace
     * @return a builder for creating a {@link PersistentResourceXMLDescription}.
     * @deprecated Use {@link Factory#builder(PathElement)} from {@link #factory(PersistentSubsystemSchema)} instead.
     */
    @Deprecated
    public static PersistentResourceXMLBuilder builder(PathElement path, Namespace namespace) {
        return builder(path, namespace.getUri());
    }

    /**
     * Creates builder for passed path element
     *
     * @param elementName name of xml element that is used as decorator
     * @return PersistentResourceXMLBuilder
     * @since 4.0
     */
    public static PersistentResourceXMLBuilder decorator(final String elementName) {
        // Create build only to collect children
        ResourceXMLElement.Builder builder = new LegacyBuilder(PathElement.pathElement(elementName));
        return new PersistentResourceXMLBuilder(builder) {
            @Override
            public PersistentResourceXMLDescription build() {
                List<Supplier<PersistentResourceXMLDescription>> childBuilders = this.children;
                List<ResourceXMLElement> children = new ArrayList<>(childBuilders.size());
                for (Supplier<PersistentResourceXMLDescription> childBuilder : childBuilders) {
                    children.add(childBuilder.get().element);
                }
                XMLElement<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> element = XMLElement.wrap(new QName(elementName), XMLContent.all(children), XMLCardinality.Single.OPTIONAL);
                return new PersistentResourceXMLDescription(new ResourceXMLElement() {
                    @Override
                    public PathElement getPathElement() {
                        return null;
                    }

                    @Override
                    public QName getName() {
                        return element.getName();
                    }

                    @Override
                    public void readElement(XMLExtendedStreamReader reader, Map.Entry<PathAddress, Map<PathAddress, ModelNode>> context) throws XMLStreamException {
                        element.readElement(reader, context);
                    }

                    @Override
                    public XMLCardinality getCardinality() {
                        return element.getCardinality();
                    }

                    @Override
                    public void writeContent(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
                        element.writeContent(writer, model);
                    }

                    @Override
                    public boolean isEmpty(ModelNode model) {
                        return element.isEmpty(model);
                    }
                });
            }
        };
    }

    static class LegacyBuilder extends ResourceXMLElement.DefaultBuilder {
        private static final FeatureFilter ALWAYS = new FeatureFilter() {
            @Override
            public <F extends Feature> boolean enables(F feature) {
                return true;
            }
        };

        LegacyBuilder(PathElement path) {
            super(ALWAYS, new ResourceDescription() {
                @Override
                public PathElement getPathElement() {
                    return path;
                }
            }, QName::new);
        }

        LegacyBuilder(PathElement path, String namespaceURI) {
            super(ALWAYS, new ResourceDescription() {
                @Override
                public PathElement getPathElement() {
                    return path;
                }
            }, new QNameResolver() {
                @Override
                public QName resolveQName(String localName) {
                    return new QName(namespaceURI, localName);
                }
            });
        }
    }

    /**
     * Creates a factory for creating a {@link PersistentResourceXMLDescription} builders for the specified subsystem schema.
     * @param <S> the schema type
     * @param schema a subsystem schema
     * @return a factory for creating a {@link PersistentResourceXMLDescription} builders
     */
    public static <S extends PersistentSubsystemSchema<S>> Factory factory(S schema) {
        ResourceXMLElement.Builder.Factory factory = ResourceXMLElement.Builder.Factory.newInstance(schema);
        return new Factory() {
            @Override
            public Builder builder(ResourceRegistration registration) {
                PathElement path = registration.getPathElement();
                boolean subsystem = path.getKey().equals(ModelDescriptionConstants.SUBSYSTEM);
                ResourceXMLElement.Builder builder = subsystem ? factory.createBuilder(SubsystemResourceDescription.of(registration, List.of())) : factory.createBuilder(ResourceDescription.of(registration, List.of()));
                if (!subsystem && !path.isWildcard()) {
                    // Override default naming strategy according to previous PersistentResourceXMLDescription logic
                    builder.withElementLocalName(ResourceXMLElementLocalName.VALUE);
                }
                return new PersistentResourceXMLBuilder(builder);
            }
        };
    }

    /**
     * Factory for creating a {@link PersistentResourceXMLDescription} builder.
     * @deprecated Superseded by {@link ResourceXMLElement.Builder.Factory}.
     */
    @Deprecated(forRemoval = true)
    public static interface Factory {
        /**
         * Creates a builder for the resource registered at the specified path.
         * @param path a path element
         * @return a builder of a {@link PersistentResourceXMLDescription}
         */
        default Builder builder(PathElement path) {
            return builder(ResourceRegistration.of(path));
        }

        /**
         * Creates a builder for the specified resource registration.
         * @param path a resource registration
         * @return a builder of a {@link PersistentResourceXMLDescription}
         */
        Builder builder(ResourceRegistration registration);
    }

    /**
     * Builds a {@link PersistentResourceXMLDescription}.
     */
    public static interface Builder {
        Builder addChild(PersistentResourceXMLDescription description);

        Builder addAttribute(AttributeDefinition attribute);

        Builder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller);

        Builder addAttributes(AttributeDefinition... attributes);

        Builder addAttributes(Stream<? extends AttributeDefinition> attributes);

        Builder addAttributes(Stream<? extends AttributeDefinition> attributes, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller);

        Builder setXmlWrapperElement(String xmlWrapperElement);

        Builder setXmlElementName(String xmlElementName);

        Builder setNoAddOperation(boolean noAddOperation);

        Builder setAdditionalOperationsGenerator(AdditionalOperationsGenerator additionalOperationsGenerator);

        Builder setUseElementsForGroups(boolean useElementsForGroups);

        Builder setNameAttributeName(String nameAttributeName);

        PersistentResourceXMLDescription build();
    }

    public static class PersistentResourceXMLBuilder implements Builder {
        private final ResourceXMLElement.Builder builder;
        private volatile String wrapperElementLocalName = null;
        final List<Supplier<PersistentResourceXMLDescription>> children = new LinkedList<>();
        private volatile Map<AttributeDefinition, AttributeParser> parsers = Map.of();
        private volatile Map<AttributeDefinition, AttributeMarshaller> marshallers = Map.of();

        private PersistentResourceXMLBuilder(ResourceXMLElement.Builder builder) {
            this.builder = builder;
        }

        public PersistentResourceXMLBuilder addChild(PersistentResourceXMLBuilder builder) {
            this.children.add(builder::build);
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addChild(PersistentResourceXMLDescription description) {
            this.children.add(Functions.constantSupplier(description));
            return this;
        }

        /*
         * Adds custom parser for child element
         * @param xmlElementName name of xml element that will be hand off to custom parser
         * @param parser custom parser
         * @return this builder
         * @since 4.0
         *//*
        public PersistentResourceXMLBuilder addChild(final String xmlElementName, ResourceParser parser, ResourceMarshaller marshaller) {
            this.customChildParsers.put(xmlElementName, parser);
            this.marshallers.add(marshaller);
            return this;
        }*/

        @Override
        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute) {
            this.builder.includeAttribute(attribute);
            return this;
        }

        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser) {
            if (attribute.getParser() != attributeParser) {
                if (this.parsers.isEmpty()) {
                    this.parsers = new HashMap<>();
                }
                this.parsers.put(attribute, attributeParser);
            }
            return this.addAttribute(attribute);
        }

        @Override
        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller) {
            if (attribute.getMarshaller() != attributeMarshaller) {
                if (this.marshallers.isEmpty()) {
                    this.marshallers = new HashMap<>();
                }
                this.marshallers.put(attribute, attributeMarshaller);
            }
            return this.addAttribute(attribute, attributeParser);
        }

        @Override
        public PersistentResourceXMLBuilder addAttributes(AttributeDefinition... attributes) {
            this.builder.includeAttributes(List.of(attributes));
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttributes(Stream<? extends AttributeDefinition> stream) {
            this.builder.includeAttributes(stream.collect(Collectors.toList()));
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttributes(Stream<? extends AttributeDefinition> stream, AttributeParser parser, AttributeMarshaller attributeMarshaller) {
            Set<AttributeDefinition> attributes = stream.collect(Collectors.toSet());
            if (this.parsers.isEmpty()) {
                this.parsers = new HashMap<>();
            }
            if (this.marshallers.isEmpty()) {
                this.marshallers = new HashMap<>();
            }
            for (AttributeDefinition attribute : attributes) {
                this.parsers.put(attribute, parser);
                this.marshallers.put(attribute, attributeMarshaller);
            }
            this.builder.includeAttributes(attributes);
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setXmlWrapperElement(final String xmlWrapperElement) {
            this.wrapperElementLocalName = xmlWrapperElement;
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setXmlElementName(final String xmlElementName) {
            this.builder.withElementLocalName(xmlElementName);
            return this;
        }

        /**
         * @deprecated Use {@link #setNameAttributeName(String)}
         */
        @Deprecated(forRemoval = true)
        public PersistentResourceXMLBuilder setUseValueAsElementName(final boolean useValueAsElementName) {
            this.builder.withElementLocalName(ResourceXMLElementLocalName.VALUE);
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setNoAddOperation(final boolean noAddOperation) {
            if (noAddOperation) {
                this.builder.thenDiscardOperation();
            }
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setAdditionalOperationsGenerator(final AdditionalOperationsGenerator additionalOperationsGenerator) {
            this.builder.withOperationTransformation(new BiConsumer<>() {
                @Override
                public void accept(Map<PathAddress, ModelNode> operations, PathAddress operationKey) {
                    ModelNode operation = operations.get(operationKey);

                    // Replace original operation with a composite operation so that operation order is consistent with previous implementation
                    ModelNode compositeOperation = Util.createEmptyOperation(ModelDescriptionConstants.COMPOSITE, PathAddress.EMPTY_ADDRESS);
                    ModelNode steps = compositeOperation.get(ModelDescriptionConstants.STEPS);
                    steps.add(operation);
                    operations.put(operationKey, compositeOperation);
                    additionalOperationsGenerator.additionalOperations(operationKey, operation, new AbstractList<ModelNode>() {
                        @Override
                        public int size() {
                            return steps.asList().size();
                        }

                        @Override
                        public ModelNode get(int index) {
                            return steps.asList().get(index);
                        }

                        @Override
                        public boolean add(ModelNode operation) {
                            // Add operation as composite operation step
                            steps.add(operation);
                            return true;
                        }
                    });
                }
            });
            return this;
        }

        /**
         * This method permit to set a forced name for resource created by parser.
         * This is useful when xml tag haven't an attribute defining the name for the resource,
         * but the tag name itself is sufficient to decide the name for the resource
         * For example when you have 2 different tag of the same xsd type representing same resource with different name
         *
         * @param forcedName the name to be forced as resourceName
         * @return the PersistentResourceXMLBuilder itself
         *
         * @deprecated Use an xml attribute to provide the name of the resource.
         */
        @Deprecated(forRemoval = true)
        public PersistentResourceXMLBuilder setForcedName(String forcedName) {
            throw new UnsupportedOperationException();
        }

        /**
         * Sets whether attributes with an {@link org.jboss.as.controller.AttributeDefinition#getAttributeGroup attribute group}
         * defined should be persisted to a child element whose name is the name of the group. Child elements
         * will be ordered based on the order in which attributes are added to this builder. Child elements for
         * attribute groups will be ordered before elements for child resources.
         *
         * @param useElementsForGroups {@code true} if child elements should be used.
         * @return a builder that can be used for further configuration or to build the xml description
         */
        @Override
        public PersistentResourceXMLBuilder setUseElementsForGroups(boolean useElementsForGroups) {
            this.builder.withAttributeGroupElementLocalNames(new UnaryOperator<>() {
                @Override
                public String apply(String groupName) {
                    return useElementsForGroups ? groupName : null;
                }
            });
            return this;
        }

        /**
         * If set to false, default attribute values won't be persisted
         *
         * @param marshallDefault weather default values should be persisted or not.
         * @return builder
         */
        public PersistentResourceXMLBuilder setMarshallDefaultValues(boolean marshallDefault) {
            if (!marshallDefault) {
                throw new UnsupportedOperationException();
            }
            return this;
        }

        /**
         * Sets name for "name" attribute that is used for wildcard resources.
         * It defines name of attribute one resource xml element to be used for such identifier
         * If not set it defaults to "name"
         *
         * @param nameAttributeName xml attribute name to be used for resource name
         * @return builder
         */
        @Override
        public PersistentResourceXMLBuilder setNameAttributeName(String nameAttributeName) {
            this.builder.withPathValueAttributeLocalName(nameAttributeName);
            return this;
        }

        @Override
        public PersistentResourceXMLDescription build() {
            if (!this.parsers.isEmpty()) {
                this.builder.withParsers(this.parsers);
            }
            if (!this.marshallers.isEmpty()) {
                this.builder.withMarshallers(this.marshallers);
            }
            for (Supplier<PersistentResourceXMLDescription> child : PersistentResourceXMLBuilder.this.children) {
                this.builder.addChild(child.get().element);
            }
            ResourceXMLElement element = this.builder.build();
            String wrapperLocalName = this.wrapperElementLocalName;
            if (wrapperLocalName == null) return new PersistentResourceXMLDescription(element);
            XMLElement<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> wrappedElement = XMLElement.wrap(PersistentResourceXMLBuilder.this.builder.resolveQName(wrapperLocalName), element);
            return new PersistentResourceXMLDescription(new ResourceXMLElement() {
                @Override
                public PathElement getPathElement() {
                    return element.getPathElement();
                }

                @Override
                public XMLCardinality getCardinality() {
                    return wrappedElement.getCardinality();
                }

                @Override
                public QName getName() {
                    return wrappedElement.getName();
                }

                @Override
                public void readElement(XMLExtendedStreamReader reader, Map.Entry<PathAddress, Map<PathAddress, ModelNode>> value) throws XMLStreamException {
                    wrappedElement.readElement(reader, value);
                }

                @Override
                public void writeContent(XMLExtendedStreamWriter streamWriter, ModelNode value) throws XMLStreamException {
                    wrappedElement.writeContent(streamWriter, value);
                }

                @Override
                public boolean isEmpty(ModelNode content) {
                    return wrappedElement.isEmpty(content);
                }
            });
        }
    }

    /**
     * Some resources require more operations that just a simple add. This interface provides a hook for these to be plugged in.
     */
    @FunctionalInterface
    public interface AdditionalOperationsGenerator {

        /**
         * Generates any additional operations required by the resource
         *
         * @param address      The address of the resource
         * @param addOperation The add operation for the resource
         * @param operations   The operation list
         */
        void additionalOperations(final PathAddress address, final ModelNode addOperation, final List<ModelNode> operations);
    }

}
