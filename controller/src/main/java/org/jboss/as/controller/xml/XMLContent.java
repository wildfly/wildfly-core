/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Encapsulates the content of an XML element.
 * @author Paul Ferraro
 * @param <RC> the reader context
 * @param <WC> the writer content
 */
public interface XMLContent<RC, WC> extends XMLContentWriter<WC> {

    /**
     * Reads element content into the specified reader context.
     * @param reader a StaX reader
     * @param context the reader context
     * @throws XMLStreamException if the input could not be read from the specified reader.
     */
    void readContent(XMLExtendedStreamReader reader, RC context) throws XMLStreamException;

    /**
     * Returns an empty content
     * @param <RC> the reader context
     * @param <WC> the writer context
     * @return an empty content
     */
    static <RC, WC> XMLContent<RC, WC> empty() {
        return new XMLContent<>() {
            @Override
            public void readContent(XMLExtendedStreamReader reader, RC value) throws XMLStreamException {
                ParseUtils.requireNoContent(reader);
            }

            @Override
            public boolean isEmpty(WC content) {
                return true;
            }

            @Override
            public void writeContent(XMLExtendedStreamWriter streamWriter, WC value) throws XMLStreamException {
                // Do nothing
            }
        };
    }

    /**
     * Returns an empty content
     * @param <RC> the reader context
     * @param <WC> the writer context
     * @return an empty content
     */
    static <RC, WC> XMLContent<RC, WC> of(XMLElementGroup<RC, WC> group) {
        return !group.getNames().isEmpty() ? new DefaultXMLContent<>(group) : empty();
    }

    class DefaultXMLContent<RC, WC> implements XMLContent<RC, WC> {
        private final XMLElementGroup<RC, WC> group;

        DefaultXMLContent(XMLElementGroup<RC, WC> group) {
            this.group = group;
        }

        @Override
        public void readContent(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
            int occurrences = 0;
            int maxOccurs = this.group.getCardinality().getMaxOccurs().orElse(Integer.MAX_VALUE);
            if (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
                do {
                    // For PersistentResourceXMLDescription compatibility, try matching w/out namespace
                    if (!this.group.getNames().contains(reader.getName()) && !this.group.getNames().contains(new QName(reader.getLocalName()))) {
                        throw ParseUtils.unexpectedElement(reader, this.group.getNames());
                    }
                    occurrences += 1;
                    // Validate maxOccurs
                    if (occurrences > maxOccurs) {
                        throw ParseUtils.maxOccursExceeded(reader, this.group.getNames(), this.group.getCardinality());
                    }
                    // Consumes 1 or more elements
                    this.group.getReader().readElement(reader, context);
                } while (reader.getEventType() != XMLStreamConstants.END_ELEMENT);
            }
            // Validate minOccurs
            if (occurrences < this.group.getCardinality().getMinOccurs()) {
                throw ParseUtils.minOccursNotReached(reader, this.group.getNames(), this.group.getCardinality());
            }
        }

        @Override
        public boolean isEmpty(WC content) {
            return this.group.getWriter().isEmpty(content);
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter writer, WC content) throws XMLStreamException {
            this.group.getWriter().writeContent(writer, content);
        }
    }
}
