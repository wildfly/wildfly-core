/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Encapsulates a group of XML particles with xs:all (i.e. unordered) semantics.
 * @author Paul Ferraro
 */
public interface XMLAll<RC, WC> extends XMLElementGroup<RC, WC>, XMLContent<RC, WC> {

    interface Builder<RC, WC> extends XMLElementGroup.Builder<RC, WC, XMLAll<RC, WC>, Builder<RC, WC>> {
    }

    class DefaultBuilder<RC, WC> extends XMLElementGroup.AbstractBuilder<RC, WC, XMLAll<RC, WC>, Builder<RC, WC>> implements Builder<RC, WC> {

        @Override
        public XMLAll<RC, WC> build() {
            Collection<XMLElement<RC, WC>> elements = this.getElements();
            return !elements.isEmpty() ? new DefaultXMLAll<>(elements, this.getCardinality()) : new DefaultXMLAll<>(XMLCardinality.NONE, XMLContentReader.empty(), XMLContentWriter.empty());
        }

        @Override
        protected Builder<RC, WC> builder() {
            return this;
        }
    }

    class DefaultXMLAll<RC, WC> extends DefaultXMLParticle<RC, WC> implements XMLAll<RC, WC> {

        protected DefaultXMLAll(Collection<XMLElement<RC, WC>> elements, XMLCardinality cardinality) {
            this(elements, cardinality, new TreeMap<>(QNameResolver.COMPARATOR));
        }

        private DefaultXMLAll(Collection<XMLElement<RC, WC>> elements, XMLCardinality cardinality, Map<QName, XMLElement<RC, WC>> choices) {
            super(cardinality, new XMLContentReader<>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
                    Map<XMLElement<RC, WC>, AtomicInteger> occurrences = new TreeMap<>(Comparator.comparing(XMLElement::getName, QNameResolver.COMPARATOR));
                    for (XMLElement<RC, WC> element : elements) {
                        occurrences.put(element, new AtomicInteger(0));
                    }
                    while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
                        QName name = reader.getName();
                        XMLElement<RC, WC> element = choices.get(name);
                        if (element == null) {
                            // Try matching w/out namespace (for PersistentResourceXMLDescription compatibility)
                            name = new QName(reader.getLocalName());
                            element = choices.get(name);
                            if (element == null) {
                                throw ParseUtils.unexpectedElement(reader, choices.keySet().stream().map(QName::getLocalPart).collect(Collectors.toSet()));
                            }
                        }
                        AtomicInteger occurrence = occurrences.get(element);
                        // Validate maxOccurs
                        OptionalInt maxOccurs = element.getCardinality().getMaxOccurs();
                        if (maxOccurs.isPresent() && (occurrence.getPlain() >= maxOccurs.getAsInt())) {
                            throw ParseUtils.maxOccursExceeded(reader, Set.of(name), element.getCardinality());
                        }
                        occurrence.setPlain(occurrence.getPlain() + 1);
                        element.getReader().readElement(reader, context);
                    }
                    // Validate minOccurs
                    for (Map.Entry<XMLElement<RC, WC>, AtomicInteger> entry : occurrences.entrySet()) {
                        XMLElement<RC, WC> element = entry.getKey();
                        AtomicInteger occurrence = entry.getValue();
                        if (occurrence.getPlain() < element.getCardinality().getMinOccurs()) {
                            throw ParseUtils.minOccursNotReached(reader, Set.of(element.getName()), element.getCardinality());
                        }
                    }
                }
            }, new XMLContentWriter.DefaultXMLContentWriter<>(elements));
            for (XMLElement<RC, WC> element : elements) {
                if (choices.put(element.getName(), element) != null) {
                    throw ControllerLogger.ROOT_LOGGER.duplicateElements(element.getName());
                }
            }
        }

        DefaultXMLAll(XMLCardinality cardinality, XMLContentReader<RC> reader, XMLContentWriter<WC> writer) {
            super(cardinality, reader, writer);
        }
    }
}
