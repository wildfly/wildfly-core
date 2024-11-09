/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Encapsulates a group of XML particles with xs:sequence (i.e. ordered) semantics.
 * @author Paul Ferraro
 */
public interface XMLSequence<RC, WC> extends XMLParticleGroup<RC, WC>, XMLContent<RC, WC> {

    interface Builder<RC, WC> extends XMLParticleGroup.Builder<RC, WC> {
        @Override
        Builder<RC, WC> withCardinality(XMLCardinality cardinality);

        @Override
        Builder<RC, WC> addElement(XMLElement<RC, WC> element);

        @Override
        Builder<RC, WC> addChoice(XMLChoice<RC, WC> choice);

        @Override
        XMLSequence<RC, WC> build();
    }

    class DefaultBuilder<RC, WC> extends XMLParticleGroup.AbstractBuilder<RC, WC, Builder<RC, WC>> implements Builder<RC, WC> {

        @Override
        public XMLSequence<RC, WC> build() {
            List<XMLChoice<RC, WC>> choices = this.getChoices();
            return !choices.isEmpty() ? new DefaultXMLSequence<>(choices, this.getCardinality()) : new DefaultXMLSequence<>(XMLCardinality.NONE, XMLContentReader.empty(), XMLContentWriter.empty());
        }

        @Override
        protected Builder<RC, WC> builder() {
            return this;
        }
    }

    class DefaultXMLSequence<RC, WC> extends DefaultXMLContent<RC, WC> implements XMLSequence<RC, WC> {

        protected DefaultXMLSequence(List<XMLChoice<RC, WC>> choices, XMLCardinality cardinality) {
            super(cardinality, new XMLContentReader<>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
                    Iterator<XMLChoice<RC, WC>> sequence = choices.iterator();
                    XMLChoice<RC, WC> current = XMLChoice.empty();
                    int occurrences = 0;
                    while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
                        QName name = reader.getName();
                        // Try matching w/out namespace (for PersistentResourceXMLDescription compatibility)
                        QName legacyName = new QName(reader.getLocalName());
                        while (!current.getChoices().contains(name) && !current.getChoices().contains(legacyName) && sequence.hasNext()) {
                            // Validate minOccurs
                            if (occurrences < current.getCardinality().getMinOccurs()) {
                                throw ParseUtils.minOccursNotReached(reader, current.getChoices(), current.getCardinality());
                            }
                            current = sequence.next();
                            occurrences = 0;
                        }
                        XMLElementReader<RC> currentReader = current.getReader(name);
                        if (currentReader == null) {
                            // PersistentResourceXMLDescription compatibility
                            currentReader = current.getReader(legacyName);
                            if (currentReader == null) {
                                throw ParseUtils.unexpectedElement(reader, current.getChoices().stream().map(QName::getLocalPart).collect(Collectors.toSet()));
                            }
                        }
                        occurrences += 1;
                        OptionalInt maxOccurs = current.getCardinality().getMaxOccurs();
                        // Validate maxOccurs
                        if (maxOccurs.isPresent() && (occurrences > maxOccurs.getAsInt())) {
                            throw ParseUtils.maxOccursExceeded(reader, current.getChoices(), current.getCardinality());
                        }
                        currentReader.readElement(reader, context);
                    }
                }
            }, new XMLContentWriter.DefaultXMLContentWriter<>(choices));
        }

        DefaultXMLSequence(XMLCardinality cardinality, XMLContentReader<RC> reader, XMLContentWriter<WC> writer) {
            super(cardinality, reader, writer);
        }
    }
}
