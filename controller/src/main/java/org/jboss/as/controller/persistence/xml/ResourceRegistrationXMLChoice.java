/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence.xml;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.FeatureFilter;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.xml.QNameResolver;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLContentWriter;
import org.jboss.as.controller.xml.XMLParticle;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.Assert;

/**
 * Encapsulates an XML choice for a wildcard resource registration and its overrides.
 * @author Paul Ferraro
 */
public interface ResourceRegistrationXMLChoice extends ResourceXMLChoice {

    interface Builder extends XMLParticle.Builder<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode, ResourceRegistrationXMLChoice, Builder> {
        /**
         * Adds an override element to this choice.
         * @param element an override element
         * @return a reference to this builder
         */
        Builder addElement(SingletonResourceRegistrationXMLElement element);
    }

    static ResourceRegistrationXMLChoice singleton(WildcardResourceRegistrationXMLElement element) {
        return new DefaultResourceRegistrationXMLChoice(Set.of(element.getName()), element.getCardinality(), element.getReader(), element.getWriter(), element.getStability());
    }

    class DefaultBuilder extends XMLParticle.AbstractBuilder<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode, ResourceRegistrationXMLChoice, Builder> implements Builder {
        // Special case comparator that only compares path values
        static final Comparator<PathElement> PATH_COMPARATOR = Comparator.comparing(PathElement::getValue);
        private final WildcardResourceRegistrationXMLElement element;
        private final Map<PathElement, ResourceRegistrationXMLElement> overrides = new TreeMap<>(PATH_COMPARATOR);
        private final FeatureFilter filter;

        DefaultBuilder(WildcardResourceRegistrationXMLElement element, FeatureFilter filter) {
            this.element = element;
            this.filter = filter;
        }

        @Override
        protected Builder builder() {
            return this;
        }

        @Override
        public Builder addElement(SingletonResourceRegistrationXMLElement element) {
            if (this.filter.enables(element)) {
                PathElement path = element.getPathElement();
                Assert.assertFalse(path.isWildcard());
                Assert.assertTrue(path.getKey().equals(this.element.getPathElement().getKey()));
                if (this.overrides.putIfAbsent(path, element) != null) {
                    throw ControllerLogger.ROOT_LOGGER.duplicatePathElement(path);
                }
            }
            return this;
        }

        @Override
        public ResourceRegistrationXMLChoice build() {
            if (this.overrides.isEmpty()) return singleton(this.element);

            PathElement wildcardPath = this.element.getPathElement();
            QName pathValueAttributeName = this.element.getPathValueAttributeName();

            Function<PathElement, ResourceRegistrationXMLElement> elements = path -> this.overrides.getOrDefault(path, this.element);
            Map<QName, Map<PathElement, ResourceRegistrationXMLElement>> mappedChoices = new TreeMap<>(QNameResolver.COMPARATOR);
            for (ResourceRegistrationXMLElement override : this.overrides.values()) {
                Map<PathElement, ResourceRegistrationXMLElement> choiceElements = mappedChoices.get(override.getName());
                if (choiceElements == null) {
                    choiceElements = new TreeMap<>(PATH_COMPARATOR);
                    mappedChoices.put(override.getName(), choiceElements);
                }
                choiceElements.put(override.getPathElement(), override);
            }
            Map<QName, Function<PathElement, ResourceRegistrationXMLElement>> choices = new TreeMap<>(QNameResolver.COMPARATOR);
            for (Map.Entry<QName, Map<PathElement, ResourceRegistrationXMLElement>> entry : mappedChoices.entrySet()) {
                QName name = entry.getKey();
                choices.put(name, name.equals(this.element.getName()) ? path -> entry.getValue().getOrDefault(path, this.element) : entry.getValue()::get);
            }

            XMLElementReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> reader = new XMLElementReader<>() {
                private ResourceRegistrationXMLElement getChoice(XMLExtendedStreamReader reader) throws XMLStreamException {
                    Function<PathElement, ResourceRegistrationXMLElement> choice = choices.get(reader.getName());
                    if (choice == null) {
                        // Match w/out namespace for PersistentResourceXMLDescription compatibility
                        choice = choices.get(new QName(reader.getLocalName()));
                    }
                    String value = reader.getAttributeValue(null, pathValueAttributeName.getLocalPart());
                    if (value == null) {
                        throw ParseUtils.missingRequired(reader, pathValueAttributeName.getLocalPart());
                    }
                    return (choice != null) ? choice.apply(PathElement.pathElement(wildcardPath.getKey(), value)) : null;
                }

                @Override
                public void readElement(XMLExtendedStreamReader reader, Map.Entry<PathAddress, Map<PathAddress, ModelNode>> context) throws XMLStreamException {
                    int occurrences = 0;
                    ResourceRegistrationXMLElement choice = this.getChoice(reader);
                    if (choice == null) {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                    int maxOccurs = choice.getCardinality().getMaxOccurs().orElse(Integer.MAX_VALUE);
                    do {
                        // Break if not a new occurrence of the current choice
                        if ((occurrences > 0) && this.getChoice(reader) != choice) {
                            break;
                        }
                        occurrences += 1;
                        // Validate maxOccurs
                        if (occurrences > maxOccurs) {
                            throw ParseUtils.maxOccursExceeded(reader, Set.of(choice.getName()), choice.getCardinality());
                        }
                        choice.getReader().readElement(reader, context);
                    } while (reader.nextTag() != XMLStreamConstants.END_ELEMENT);
                    // Validate minOccurs
                    if (occurrences < choice.getCardinality().getMinOccurs()) {
                        throw ParseUtils.maxOccursExceeded(reader, Set.of(choice.getName()), choice.getCardinality());
                    }
                }
            };
            XMLContentWriter<ModelNode> writer = new XMLContentWriter<>() {
                @Override
                public void writeContent(XMLExtendedStreamWriter writer, ModelNode parent) throws XMLStreamException {
                    String key = wildcardPath.getKey();
                    if (parent.hasDefined(key)) {
                        for (Property property : parent.get(key).asPropertyListOrEmpty()) {
                            String value = property.getName();
                            ModelNode model = property.getValue();

                            PathElement path = PathElement.pathElement(key, value);
                            ModelNode parentWrapper = new ModelNode();
                            parentWrapper.get(path.getKeyValuePair()).set(model);
                            elements.apply(path).getWriter().writeContent(writer, parentWrapper);
                        }
                    }
                }

                @Override
                public boolean isEmpty(ModelNode parent) {
                    return !parent.hasDefined(wildcardPath.getKey()) || parent.get(wildcardPath.getKey()).asPropertyListOrEmpty().isEmpty();
                }
            };
            return new DefaultResourceRegistrationXMLChoice(choices.keySet(), this.getCardinality(), reader, writer, this.element.getStability());
        }
    }

    class DefaultResourceRegistrationXMLChoice extends DefaultXMLChoice<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> implements ResourceRegistrationXMLChoice {

        DefaultResourceRegistrationXMLChoice(Set<QName> names, XMLCardinality cardinality, XMLElementReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> reader, XMLContentWriter<ModelNode> writer, Stability stability) {
            super(names, cardinality, reader, writer, stability);
        }
    }
}
