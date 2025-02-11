/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.AbstractList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.persistence.xml.ResourceXMLElement;
import org.jboss.as.controller.persistence.xml.ResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.ResourceXMLAll;
import org.jboss.as.controller.persistence.xml.ResourceXMLElementLocalName;
import org.jboss.as.controller.persistence.xml.ResourceXMLParticleFactory;
import org.jboss.as.controller.xml.QNameResolver;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLContentReader;
import org.jboss.as.controller.xml.XMLContentWriter;
import org.jboss.as.controller.xml.XMLElement;
import org.jboss.as.version.Stability;
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
    private static final FeatureFilter ALWAYS = new FeatureFilter() {
        @Override
        public <F extends Feature> boolean enables(F feature) {
            return true;
        }
    };
    private static final ResourceXMLParticleFactory UNQUALIFIED_FACTORY = ResourceXMLParticleFactory.newInstance(ALWAYS, QName::new);

    private final ResourceRegistrationXMLElement element;

    private PersistentResourceXMLDescription(ResourceRegistrationXMLElement element) {
        this.element = element;
    }

    public PathElement getPathElement() {
        return this.element.getPathElement();
    }

    public ResourceRegistrationXMLElement getXMLElement() {
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
        this.element.getReader().readElement(reader, Map.entry(parentAddress, operations));
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
        this.element.getWriter().writeContent(writer, model);
    }

    public void persist(XMLExtendedStreamWriter writer, ModelNode model, String namespaceURI) throws XMLStreamException {
        this.persist(writer, model);
    }

    public void persistChildren(XMLExtendedStreamWriter writer, ModelNode model) {
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
        return new PersistentResourceXMLBuilder(UNQUALIFIED_FACTORY, ResourceRegistration.of(pathElement));
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
        QNameResolver resolver = name -> new QName(namespaceURI, name);
        ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(ALWAYS, resolver);
        return new PersistentResourceXMLBuilder(factory, ResourceRegistration.of(pathElement));
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
        // Create builder only to collect children
        return new PersistentResourceXMLBuilder(UNQUALIFIED_FACTORY, ResourceRegistration.of(PathElement.pathElement(elementName))) {
            @Override
            public PersistentResourceXMLDescription build() {
                List<Supplier<PersistentResourceXMLDescription>> childBuilders = this.children;
                ResourceXMLElement.Builder builder = UNQUALIFIED_FACTORY.element(UNQUALIFIED_FACTORY.resolve(elementName)).withCardinality(XMLCardinality.Single.OPTIONAL);
                // While child element types were ordered (see above), sequence semantics were never enforced (and some subsystems rely on this)
                ResourceXMLAll.Builder contentBuilder = UNQUALIFIED_FACTORY.all();
                for (Supplier<PersistentResourceXMLDescription> childBuilder : childBuilders) {
                    contentBuilder.addElement(childBuilder.get().element);
                }
                ResourceXMLElement element = builder.withContent(contentBuilder.build()).build();
                return new PersistentResourceXMLDescription(new ResourceRegistrationXMLElement() {
                    @Override
                    public PathElement getPathElement() {
                        return null;
                    }

                    @Override
                    public QName getName() {
                        return element.getName();
                    }

                    @Override
                    public XMLContentReader<Entry<PathAddress, Map<PathAddress, ModelNode>>> getReader() {
                        return element.getReader();
                    }

                    @Override
                    public XMLContentWriter<ModelNode> getWriter() {
                        return element.getWriter();
                    }

                    @Override
                    public XMLCardinality getCardinality() {
                        return element.getCardinality();
                    }
                });
            }
        };
    }

    /**
     * Creates a factory for creating a {@link PersistentResourceXMLDescription} builders for the specified subsystem schema.
     * @param <S> the schema type
     * @param schema a subsystem schema
     * @return a factory for creating a {@link PersistentResourceXMLDescription} builders
     */
    public static <S extends PersistentSubsystemSchema<S>> Factory factory(S schema) {
        ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(schema);
        return new Factory() {
            @Override
            public Builder builder(ResourceRegistration registration) {
                return new PersistentResourceXMLBuilder(factory, registration);
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
        private final ResourceXMLParticleFactory factory;
        private final ResourceRegistration registration;
        private volatile String wrapperElementLocalName = null;
        final List<Supplier<PersistentResourceXMLDescription>> children = new LinkedList<>();
        private final List<AttributeDefinition> attributes = new LinkedList<>();
        private final Map<AttributeDefinition, AttributeParser> parsers = new TreeMap<>(Comparator.comparing(AttributeDefinition::getName));
        private final Map<AttributeDefinition, AttributeMarshaller> marshallers = new TreeMap<>(Comparator.comparing(AttributeDefinition::getName));
        private volatile boolean useElementsForGroups = true;
        private volatile String pathValueAttributeLocalName = ModelDescriptionConstants.NAME;
        private volatile BiConsumer<Map<PathAddress, ModelNode>, PathAddress> operationTransformation = Functions.discardingBiConsumer();
        private volatile Function<PathElement, String> elementLocalName = null;
        private volatile boolean discardOperation = false;

        private PersistentResourceXMLBuilder(ResourceXMLParticleFactory factory, ResourceRegistration registration) {
            this.factory = factory;
            this.registration = registration;
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

        @Override
        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute) {
            this.attributes.add(attribute);
            return this;
        }

        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser parser) {
            this.addAttribute(attribute);
            this.parsers.put(attribute, parser);
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser parser, AttributeMarshaller marshaller) {
            this.addAttribute(attribute, parser);
            this.marshallers.put(attribute, marshaller);
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttributes(AttributeDefinition... attributes) {
            for (AttributeDefinition attribute : attributes) {
                this.addAttribute(attribute);
            }
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttributes(Stream<? extends AttributeDefinition> stream) {
            Iterator<? extends AttributeDefinition> attributes = stream.iterator();
            while (attributes.hasNext()) {
                this.addAttribute(attributes.next());
            }
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttributes(Stream<? extends AttributeDefinition> stream, AttributeParser parser, AttributeMarshaller marshaller) {
            Iterator<? extends AttributeDefinition> attributes = stream.iterator();
            while (attributes.hasNext()) {
                this.addAttribute(attributes.next(), parser, marshaller);
            }
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setXmlWrapperElement(final String xmlWrapperElement) {
            this.wrapperElementLocalName = xmlWrapperElement;
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setXmlElementName(final String xmlElementName) {
            this.elementLocalName = path -> xmlElementName;
            return this;
        }

        /**
         * @deprecated Use {@link #setNameAttributeName(String)}
         */
        @Deprecated(forRemoval = true)
        public PersistentResourceXMLBuilder setUseValueAsElementName(final boolean useValueAsElementName) {
            this.elementLocalName = ResourceXMLElementLocalName.VALUE;
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setNoAddOperation(final boolean noAddOperation) {
            this.discardOperation = noAddOperation;
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setAdditionalOperationsGenerator(final AdditionalOperationsGenerator additionalOperationsGenerator) {
            this.operationTransformation = new BiConsumer<>() {
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
            };
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
            this.useElementsForGroups = useElementsForGroups;
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
            this.pathValueAttributeLocalName = nameAttributeName;
            return this;
        }

        @Override
        public PersistentResourceXMLDescription build() {
            ResourceRegistrationXMLElement element = this.build(this.registration.getPathElement(), this.registration.getStability());
            String wrapperLocalName = this.wrapperElementLocalName;
            if (wrapperLocalName == null) return new PersistentResourceXMLDescription(element);
            XMLElement<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> wrapperElement = XMLElement.wrap(this.factory.resolve(wrapperLocalName), element);
            return new PersistentResourceXMLDescription(new ResourceRegistrationXMLElement() {
                @Override
                public PathElement getPathElement() {
                    return element.getPathElement();
                }

                @Override
                public XMLCardinality getCardinality() {
                    return wrapperElement.getCardinality();
                }

                @Override
                public QName getName() {
                    return wrapperElement.getName();
                }

                @Override
                public XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> getReader() {
                    return wrapperElement.getReader();
                }

                @Override
                public XMLContentWriter<ModelNode> getWriter() {
                    return wrapperElement.getWriter();
                }
            });
        }

        private ResourceRegistrationXMLElement build(PathElement path, Stability stability) {
            if (path.isWildcard()) {
                return this.build(this.factory.element(WildcardResourceRegistration.of(path, stability)).withPathValueAttributeLocalName(this.pathValueAttributeLocalName));
            }
            if (path.getKey().equals(ModelDescriptionConstants.SUBSYSTEM)) {
                return this.build(this.factory.element(SubsystemResourceRegistration.of(path.getValue(), stability)));
            }
            return this.build(this.factory.element(SingletonResourceRegistration.of(path, stability)));
        }

        private <E extends ResourceRegistrationXMLElement, B extends ResourceRegistrationXMLElement.Builder<E, B>> E build(B builder) {
            if (this.elementLocalName != null) {
                builder.withElementLocalName(this.elementLocalName);
            }
            if (!this.parsers.isEmpty()) {
                builder.withParsers(this.parsers);
            }
            if (!this.marshallers.isEmpty()) {
                builder.withMarshallers(this.marshallers);
            }
            if (this.discardOperation) {
                builder.thenDiscardOperation();
            }
            builder.withOperationTransformation(this.operationTransformation);
            // PersistentResourceXMLDescription expects child element types in following order:
            // 1. attribute elements
            // 2. attribute group elements
            // 3. child resources
            // However, while child element types were ordered (see above), sequence semantics were never actually enforced (and some subsystems rely on this)
            ResourceXMLAll.Builder contentBuilder = this.factory.all().withParsers(this.parsers).withMarshallers(this.marshallers);
            // Collect attributes per group
            Map<String, List<AttributeDefinition>> groups = this.useElementsForGroups ? new LinkedHashMap<>() : Map.of();
            for (AttributeDefinition attribute : this.attributes) {
                AttributeParser parser = this.parsers.getOrDefault(attribute, attribute.getParser());
                AttributeMarshaller marshaller = this.marshallers.getOrDefault(attribute, attribute.getMarshaller());
                String groupName = this.useElementsForGroups ? attribute.getAttributeGroup() : null;
                if (groupName != null) {
                    List<AttributeDefinition> groupAttributes = groups.get(groupName);
                    if (groupAttributes == null) {
                        groupAttributes = new LinkedList<>();
                        groups.put(groupName, groupAttributes);
                    }
                    groupAttributes.add(attribute);
                } else {
                    if (parser.isParseAsElement() || marshaller.isMarshallableAsElement()) {
                        contentBuilder.addElement(attribute);
                    }
                    if (!parser.isParseAsElement() || !marshaller.isMarshallableAsElement()) {
                        builder.addAttribute(attribute);
                    }
                }
            }
            for (Map.Entry<String, List<AttributeDefinition>> groupEntry : groups.entrySet()) {
                contentBuilder.addElement(groupEntry.getKey(), groupEntry.getValue());
            }
            for (Supplier<PersistentResourceXMLDescription> child : PersistentResourceXMLBuilder.this.children) {
                contentBuilder.addElement(child.get().element);
            }
            // While child element types were ordered (see above), sequence semantics were never enforced (and some subsystems rely on this)
            return builder.withContent(contentBuilder.build()).build();
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
