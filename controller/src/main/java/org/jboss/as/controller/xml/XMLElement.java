/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Encapsulates an XML element.
 * @param <RC> the reader context
 * @param <WC> the writer content
 * @author Paul Ferraro
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
    }

    /**
     * Creates an element whose content should be ignored, if present.
     * @param <RC> the reader context
     * @param <WC> the writer content
     * @param name the qualified name of ignored element
     * @return an element whose content should be ignored.
     */
    static <RC, WC> XMLElement<RC, WC> ignore(QName name) {
        XMLContentReader<RC> reader = new XMLContentReader<>() {
            @Override
            public void readElement(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
                if (!reader.isStartElement() || !reader.getName().equals(name)) {
                    throw ParseUtils.unexpectedElement(reader, Set.of(name.getLocalPart()));
                }
                ControllerLogger.ROOT_LOGGER.elementIgnored(name);
                this.skipElement(reader);
                if (!reader.isEndElement() || !reader.getName().equals(name)) {
                    throw ParseUtils.unexpectedElement(reader);
                }
            }

            private void skipElement(XMLExtendedStreamReader reader) throws XMLStreamException {
                while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
                    this.skipElement(reader);
                }
            }
        };
        return new DefaultXMLElement<>(name, XMLCardinality.Single.OPTIONAL, reader, XMLContentWriter.empty());
    }

    /**
     * Applies an element wrapper to the specified content.
     * @param <RC> the reader context
     * @param <WC> the writer content
     * @param name the qualified name of the wrapper element
     * @param choice the XML content to wrap
     * @return an element that reads/writes the wrapped content.
     */
    static <RC, WC> XMLElement<RC, WC> wrap(QName name, XMLChoice<RC, WC> choice) {
        return new DefaultBuilder<RC, WC>(name).withCardinality(choice.getCardinality()).withContent(choice).build();
    }

    /**
     * Applies an element wrapper to the specified element.
     * @param <RC> the reader context
     * @param <WC> the writer content
     * @param name the qualified name of the wrapper element
     * @param choice the XML content to wrap
     * @return an element that reads/writes the wrapped content.
     */
    static <RC, WC> XMLElement<RC, WC> wrap(QName name, XMLElement<RC, WC> element) {
        return new DefaultBuilder<RC, WC>(name).withCardinality(element.getCardinality()).withContent(XMLChoice.singleton(element)).build();
    }

    class DefaultBuilder<RC, WC> extends XMLContainer.AbstractBuilder<RC, WC, XMLElement<RC, WC>, Builder<RC, WC>> implements Builder<RC, WC> {
        private final QName name;

        DefaultBuilder(QName name) {
            this.name = name;
        }

        @Override
        protected XMLElement.Builder<RC, WC> builder() {
            return this;
        }

        @Override
        public XMLElement<RC, WC> build() {
            QName name = this.name;
            XMLContent<RC, WC> content = this.getContent();
            XMLContentReader<RC> reader = new XMLContentReader<>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
                    // Validate entry criteria
                    // Try matching w/out namespace (for PersistentResourceXMLDescription compatibility)
                    if (!reader.isStartElement() || (!reader.getName().equals(name) && !reader.getLocalName().equals(name.getLocalPart()))) {
                        throw ParseUtils.unexpectedElement(reader, Set.of(name.getLocalPart()));
                    }
                    ParseUtils.requireNoAttributes(reader);
                    content.getReader().readElement(reader, context);
                    // Validate exit criteria
                    // Try matching w/out namespace (for PersistentResourceXMLDescription compatibility)
                    if (!reader.isEndElement() || (!reader.getName().equals(name) && !reader.getLocalName().equals(name.getLocalPart()))) {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                }
            };
            XMLContentWriter<WC> writer = new XMLContentWriter<>() {
                @Override
                public void writeContent(XMLExtendedStreamWriter writer, WC value) throws XMLStreamException {
                    if (name.getNamespaceURI() != XMLConstants.NULL_NS_URI) {
                        writer.writeStartElement(name.getNamespaceURI(), name.getLocalPart());
                    } else {
                        // PersistentResourceXMLDescription compatibility
                        writer.writeStartElement(name.getLocalPart());
                    }
                    content.getWriter().writeContent(writer, value);
                    writer.writeEndElement();
                }

                @Override
                public boolean isEmpty(WC value) {
                    return content.getWriter().isEmpty(value);
                }
            };
            return new DefaultXMLElement<>(this.name, this.getCardinality(), reader, writer);
        }
    }

    class DefaultXMLElement<RC, WC> extends DefaultXMLParticle<RC, WC> implements XMLElement<RC, WC> {
        private final QName name;
        private final XMLCardinality cardinality;
        private final XMLContentReader<RC> reader;

        protected DefaultXMLElement(QName name, XMLCardinality cardinality, XMLContentReader<RC> reader, XMLContentWriter<WC> writer) {
            super(cardinality, reader, writer);
            this.name = name;
            this.cardinality = cardinality;
            this.reader = reader;
        }

        @Override
        public QName getName() {
            return this.name;
        }

        @Override
        public XMLCardinality getCardinality() {
            return this.cardinality;
        }

        @Override
        public XMLContentReader<RC> getReader() {
            return this.reader;
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
            return this.getName().toString();
        }
    }
}
