/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence.xml;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.xml.QNameResolver;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLChoice;
import org.jboss.as.controller.xml.XMLContentReader;
import org.jboss.as.controller.xml.XMLContentWriter;
import org.jboss.as.controller.xml.XMLParticle;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.Assert;

/**
 * Encapsulates an XML choice for a wildcard resource registration and its overrides.
 * @author Paul Ferraro
 */
public interface ResourceRegistrationXMLChoice extends XMLChoice<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> {

    /**
     * Returns the set of resource paths for this choice.
     * @return the set of resource paths for this choice.
     */
    Set<PathElement> getPathElements();

    interface Builder extends XMLParticle.Builder<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode, ResourceRegistrationXMLChoice, Builder> {
        /**
         * Adds an override element to this choice.
         * @param element an override element
         * @return a reference to this builder
         */
        Builder addElement(SingletonResourceRegistrationXMLElement element);
    }

    static ResourceRegistrationXMLChoice singleton(WildcardResourceRegistrationXMLElement element) {
        return new DefaultResourceRegistrationXMLChoice(Set.of(element.getPathElement()), element.getCardinality(), Map.of(element.getName(), element.getReader()), element.getWriter());
    }

    class DefaultBuilder extends XMLParticle.AbstractBuilder<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode, ResourceRegistrationXMLChoice, Builder> implements Builder {
        static final Comparator<PathElement> PATH_COMPARATOR = Comparator.comparing(PathElement::getValue);
        private final WildcardResourceRegistrationXMLElement element;
        private final Map<PathElement, SingletonResourceRegistrationXMLElement> overrides = new TreeMap<>(PATH_COMPARATOR);

        DefaultBuilder(WildcardResourceRegistrationXMLElement element) {
            this.element = element;
        }

        @Override
        protected Builder builder() {
            return this;
        }

        @Override
        public Builder addElement(SingletonResourceRegistrationXMLElement element) {
            PathElement path = element.getPathElement();
            Assert.assertFalse(path.isWildcard());
            Assert.assertTrue(path.getKey().equals(this.element.getPathElement().getKey()));
            if (this.overrides.putIfAbsent(path, element) != null) {
                throw ControllerLogger.ROOT_LOGGER.duplicatePathElement(path);
            }
            return this;
        }

        @Override
        public ResourceRegistrationXMLChoice build() {
            Collection<SingletonResourceRegistrationXMLElement> overrides = this.overrides.values();
            if (overrides.isEmpty()) return singleton(this.element);

            PathElement wildcardPath = this.element.getPathElement();
            QName pathValueAttributeName = this.element.getPathValueAttributeName();

            XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> defaultReader = this.element.getReader();
            XMLContentWriter<ModelNode> defaultWriter = this.element.getWriter();

            Map<QName, Map<PathElement, XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>>>> readers = new TreeMap<>(QNameResolver.COMPARATOR);
            readers.put(this.element.getName(), new TreeMap<>(PATH_COMPARATOR));
            Map<PathElement, XMLContentWriter<ModelNode>> writers = new TreeMap<>(PATH_COMPARATOR);
            writers.put(wildcardPath, defaultWriter);

            for (SingletonResourceRegistrationXMLElement override : this.overrides.values()) {
                QName overrideName = override.getName();
                PathElement overridePath = override.getPathElement();
                Map<PathElement, XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>>> overrideReaders = readers.get(overrideName);
                if (overrideReaders == null) {
                    overrideReaders = new TreeMap<>(PATH_COMPARATOR);
                    readers.put(overrideName, overrideReaders);
                }
                overrideReaders.put(overridePath, override.getReader());
                writers.put(overridePath, override.getWriter());
            }

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
                            writers.getOrDefault(path, defaultWriter).writeContent(writer, parentWrapper);
                        }
                    }
                }

                @Override
                public boolean isEmpty(ModelNode parent) {
                    return !parent.hasDefined(wildcardPath.getKey()) || parent.get(wildcardPath.getKey()).asPropertyListOrEmpty().isEmpty();
                }
            };
            Function<QName, XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>>> reader = new Function<>() {
                @Override
                public XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> apply(QName name) {
                    Map<PathElement, XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>>> overrideReaders = readers.get(name);
                    return (overrideReaders != null) ? new XMLContentReader<>() {
                        @Override
                        public void readElement(XMLExtendedStreamReader reader, Map.Entry<PathAddress, Map<PathAddress, ModelNode>> context) throws XMLStreamException {
                            String value = reader.getAttributeValue(null, pathValueAttributeName.getLocalPart());
                            if (value == null) {
                                throw ParseUtils.missingRequired(reader, pathValueAttributeName.getLocalPart());
                            }
                            overrideReaders.getOrDefault(PathElement.pathElement(wildcardPath.getKey(), value), defaultReader).readElement(reader, context);
                        }
                    } : null;
                }
            };
            return new DefaultResourceRegistrationXMLChoice(writers.keySet(), readers.keySet(), this.getCardinality(), reader, writer);
        }
    }

    class DefaultResourceRegistrationXMLChoice extends DefaultXMLChoice<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> implements ResourceRegistrationXMLChoice {
        private final Set<PathElement> paths;

        DefaultResourceRegistrationXMLChoice(Set<PathElement> paths, XMLCardinality cardinality, Map<QName, XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>>> readers, XMLContentWriter<ModelNode> writer) {
            super(readers.keySet(), cardinality, readers::get, writer);
            this.paths = paths;
        }

        DefaultResourceRegistrationXMLChoice(Set<PathElement> paths, Set<QName> names, XMLCardinality cardinality, Function<QName, XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>>> readers, XMLContentWriter<ModelNode> writer) {
            super(names, cardinality, readers, writer);
            this.paths = paths;
        }

        @Override
        public Set<PathElement> getPathElements() {
            return this.paths;
        }
    }
}
