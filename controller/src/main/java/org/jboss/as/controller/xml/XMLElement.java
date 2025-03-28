/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.Assert;

/**
 * Encapsulates an XML element.
 * @author Paul Ferraro
 * @param <RC> the reader context
 * @param <WC> the writer content
 */
public interface XMLElement<RC, WC> extends XMLContainer<RC, WC> {
    /**
     * The qualified name of this element.
     * @return a qualified name
     */
    QName getName();

    /**
     * Builder of an XML element.
     * @param <RC> the reader context
     * @param <WC> the writer content
     */
    interface Builder<RC, WC> extends XMLContainer.Builder<RC, WC, XMLElement<RC, WC>, Builder<RC, WC>> {
        /**
         * Adds readers and writer for the attributes of this element.
         * @param readers a map of readers per qualified name
         * @param writer a writer of the attributes of this element
         * @return a reference to this builder
         */
        Builder<RC, WC> addAttribute(XMLAttribute<RC, WC> attribute);

        /**
         * Adds readers and writer for the attributes of this element.
         * @param readers a map of readers per qualified name
         * @param writer a writer of the attributes of this element
         * @return a reference to this builder
         */
        default Builder<RC, WC> addAttributes(Iterable<? extends XMLAttribute<RC, WC>> attributes) {
            for (XMLAttribute<RC, WC> attribute : attributes) {
                this.addAttribute(attribute);
            }
            return this;
        }
    }

    /**
     * Creates an element whose content should be ignored, if present.
     * @param <RC> the reader context
     * @param <WC> the writer content
     * @param name the qualified name of ignored element
     * @return an element whose content should be ignored.
     */
    static <RC, WC> XMLElement<RC, WC> ignore(QName name, XMLCardinality cardinality) {
        return new DefaultXMLElement<>(name, cardinality, new XMLElementReader<>() {
            @Override
            public void readElement(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
                ControllerLogger.ROOT_LOGGER.elementIgnored(name);
                this.skipElement(reader);
            }

            private void skipElement(XMLExtendedStreamReader reader) throws XMLStreamException {
                while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
                    this.skipElement(reader);
                }
            }
        }, XMLContentWriter.empty(), Stability.DEFAULT);
    }

    class DefaultBuilder<RC, WC> extends XMLContainer.AbstractBuilder<RC, WC, XMLElement<RC, WC>, Builder<RC, WC>> implements Builder<RC, WC>, FeatureRegistry {
        private final QName name;
        private final List<XMLAttribute<RC, WC>> attributes = new LinkedList<>();
        private final Stability stability;

        DefaultBuilder(QName name, Stability stability) {
            this.name = name;
            this.stability = stability;
        }

        @Override
        public Builder<RC, WC> addAttribute(XMLAttribute<RC, WC> attribute) {
            if (this.enables(attribute)) {
                this.attributes.add(attribute);
            }
            return this;
        }

        @Override
        protected XMLElement.Builder<RC, WC> builder() {
            return this;
        }

        @Override
        public XMLElement<RC, WC> build() {
            QName name = this.name;
            Map<QName, XMLAttribute<RC, WC>> attributes = new TreeMap<>(QNameResolver.COMPARATOR);
            for (XMLAttribute<RC, WC> attribute : this.attributes) {
                attributes.put(attribute.getName(), attribute);
            }
            XMLContent<RC, WC> content = this.getContent();
            XMLElementReader<RC> reader = new XMLElementReader<>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
                    // Track occurrence via map removal
                    Map<QName, XMLAttribute<RC, WC>> remaining = new TreeMap<>(QNameResolver.COMPARATOR);
                    remaining.putAll(attributes);
                    for (int i = 0; i < reader.getAttributeCount(); ++i) {
                        QName attributeName = reader.getAttributeName(i);
                        if (attributeName.getNamespaceURI().equals(XMLConstants.NULL_NS_URI) && !reader.getName().getNamespaceURI().equals(XMLConstants.NULL_NS_URI)) {
                            // Inherit namespace of element, if unspecified
                            attributeName = new QName(reader.getName().getNamespaceURI(), attributeName.getLocalPart());
                        }
                        XMLAttribute<RC, WC> attribute = remaining.remove(attributeName);
                        if (attribute == null) {
                            if (attributes.containsKey(attributeName)) {
                                throw ParseUtils.duplicateAttribute(reader, attributeName.getLocalPart());
                            }
                            throw ParseUtils.unexpectedAttribute(reader, i, attributes.keySet());
                        }
                        if (!attribute.getUsage().isEnabled()) {
                            throw ParseUtils.unexpectedAttribute(reader, i);
                        }
                        attribute.getReader().readAttribute(reader, i, context);
                    }
                    if (!remaining.isEmpty()) {
                        Set<QName> missing = new TreeSet<>(QNameResolver.COMPARATOR);
                        for (XMLAttribute<RC, WC> attribute : remaining.values()) {
                            if (attribute.getUsage().isRequired()) {
                                missing.add(attribute.getName());
                            } else {
                                attribute.getReader().whenAbsent(context);
                            }
                        }
                        if (!missing.isEmpty()) {
                            throw ParseUtils.missingRequired(reader, remaining.keySet());
                        }
                    }
                    content.readContent(reader, context);
                }
            };
            XMLContentWriter<WC> writer = new DefaultXMLElementWriter<>(name, XMLContentWriter.composite(attributes.values()), Function.identity(), content);
            return new DefaultXMLElement<>(this.name, this.getCardinality(), reader, writer, this.stability);
        }

        @Override
        public Stability getStability() {
            return this.stability;
        }
    }

    class DefaultXMLElement<RC, WC> extends DefaultXMLParticle<RC, WC> implements XMLElement<RC, WC> {
        private final QName name;

        protected DefaultXMLElement(QName name, XMLCardinality cardinality, XMLElementReader<RC> elementReader, XMLContentWriter<WC> elementWriter, Stability stability) {
            super(cardinality, new XMLElementReader<>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, RC value) throws XMLStreamException {
                    // Validate entry criteria
                    Assert.assertTrue(reader.isStartElement());
                    if (!reader.getName().equals(name)) {
                        throw ParseUtils.unexpectedElement(reader, Set.of(name));
                    }
                    elementReader.readElement(reader, value);
                    // Validate exit criteria
                    if (!reader.isEndElement()) {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                    if (!reader.getName().equals(name)) {
                        throw ParseUtils.unexpectedEndElement(reader);
                    }
                }
            }, elementWriter, stability);
            this.name = name;
        }

        @Override
        public QName getName() {
            return this.name;
        }

        @Override
        public int hashCode() {
            return this.getName().hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof XMLElement)) return false;
            XMLElement<?, ?> element = (XMLElement<?, ?>) object;
            return this.getName().equals(element.getName());
        }

        @Override
        public String toString() {
            return String.format("<xs:element name=\"%s\" %s/>", this.name.getLocalPart(), XMLCardinality.toString(this.getCardinality()));
        }
    }

    class DefaultXMLElementWriter<RC, WC, CC> implements XMLContentWriter<WC> {
        private final QName name;
        private final XMLContentWriter<WC> attributesWriter;
        private final Function<WC, CC> childContentFactory;
        private final XMLContent<RC, CC> childContent;

        public DefaultXMLElementWriter(QName name, XMLContentWriter<WC> attributesWriter, Function<WC, CC> childContentFactory, XMLContent<RC, CC> childContent) {
            this.name = name;
            this.attributesWriter = attributesWriter;
            this.childContentFactory = childContentFactory;
            this.childContent = childContent;
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter writer, WC content) throws XMLStreamException {
            String namespaceURI = this.name.getNamespaceURI();
            writer.writeStartElement(namespaceURI, this.name.getLocalPart());

            // If namespace is not yet bound to any prefix, bind it
            if (writer.getNamespaceContext().getPrefix(namespaceURI) == null) {
                writer.setPrefix(this.name.getPrefix(), namespaceURI);
                writer.writeNamespace(this.name.getPrefix(), namespaceURI);
            }

            this.attributesWriter.writeContent(writer, content);

            this.childContent.writeContent(writer, this.childContentFactory.apply(content));

            writer.writeEndElement();
        }

        @Override
        public boolean isEmpty(WC content) {
            return this.attributesWriter.isEmpty(content) && this.childContent.isEmpty(this.childContentFactory.apply(content));
        }
    }
}
