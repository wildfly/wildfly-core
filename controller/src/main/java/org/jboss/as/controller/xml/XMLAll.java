/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.FeatureFilter;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.wildfly.common.Assert;

/**
 * Encapsulates a group of XML particles with xs:all (i.e. unordered) semantics.
 * @author Paul Ferraro
 * @param <RC> the reader context
 * @param <WC> the writer context
 */
public interface XMLAll<RC, WC> extends XMLElementGroup<RC, WC> {

    interface Builder<RC, WC> extends XMLElementGroup.Builder<RC, WC, XMLAll<RC, WC>, Builder<RC, WC>> {
    }

    class DefaultBuilder<RC, WC> extends XMLElementGroup.AbstractBuilder<RC, WC, XMLAll<RC, WC>, Builder<RC, WC>> implements Builder<RC, WC> {
        DefaultBuilder(FeatureFilter filter) {
            super(filter);
        }

        @Override
        public Builder<RC, WC> addElement(XMLElement<RC, WC> element) {
            return super.addElement(element);
        }

        @Override
        public XMLAll<RC, WC> build() {
            return new DefaultXMLAll<>(this.getElements(), this.getCardinality());
        }

        @Override
        protected Builder<RC, WC> builder() {
            return this;
        }
    }

    class DefaultXMLAll<RC, WC> extends DefaultXMLElementGroup<RC, WC> implements XMLAll<RC, WC> {

        protected DefaultXMLAll(Collection<XMLElement<RC, WC>> elements, XMLCardinality cardinality) {
            this(new TreeSet<>(QNameResolver.COMPARATOR), elements, cardinality);
        }

        private DefaultXMLAll(Set<QName> names, Collection<XMLElement<RC, WC>> elements, XMLCardinality cardinality) {
            this(names, cardinality, new XMLElementReader<>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
                    Assert.assertTrue(reader.isStartElement());
                    // Track occurrences via set removal since xs:all elements may not occur more than once
                    Map<QName, XMLElement<RC, WC>> remaining = new TreeMap<>(QNameResolver.COMPARATOR);
                    for (XMLElement<RC, WC> element : elements) {
                        remaining.put(element.getName(), element);
                    }
                    do {
                        XMLElement<RC, WC> element = remaining.remove(reader.getName());
                        if (element == null) {
                            // Try matching w/out namespace for PersistentResourceXMLDescription compatibility
                            element = remaining.remove(new QName(reader.getLocalName()));
                            if (element == null) {
                                break;
                            }
                        }
                        element.getReader().readElement(reader, context);
                    } while (reader.nextTag() != XMLStreamConstants.END_ELEMENT);
                    // Validate that any remaining elements are optional
                    if (!remaining.isEmpty()) {
                        Set<QName> required = new TreeSet<>(QNameResolver.COMPARATOR);
                        for (XMLElement<RC, WC> element : remaining.values()) {
                            if (element.getCardinality().getMinOccurs() > 0) {
                                required.add(element.getName());
                            }
                        }
                        if (!required.isEmpty()) {
                            throw ParseUtils.minOccursNotReached(reader, required, XMLCardinality.Single.REQUIRED);
                        }
                    }
                }
            }, new CompositeXMLContentWriter<>(elements));
            for (XMLElement<RC, WC> element : elements) {
                if (!names.add(element.getName())) {
                    throw ControllerLogger.ROOT_LOGGER.duplicateElements(element.getName());
                }
            }
        }

        protected DefaultXMLAll(Set<QName> names, XMLCardinality cardinality, XMLElementReader<RC> reader, XMLContentWriter<WC> writer) {
            super(names, cardinality, reader, writer, Stability.DEFAULT);
        }
    }
}
