/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Encapsulates a group of XML particles using xs:choice (i.e. one of) semantics.
 * @param <RC> the reader context
 * @param <WC> the writer content
 * @author Paul Ferraro
 */
public interface XMLChoice<RC, WC> extends XMLParticleGroup<RC, WC>, XMLContent<RC, WC> {
    /**
     * Returns the set of qualified choice names.
     * @return the set of qualified choice names.
     */
    Set<QName> getChoices();

    /**
     * Returns a reader for the specified qualified element name.
     * @return a reader for the specified qualified element name.
     */
    XMLContentReader<RC> getReader(QName name);

    interface Builder<RC, WC> extends XMLParticleGroup.Builder<RC, WC> {
        @Override
        Builder<RC, WC> withCardinality(XMLCardinality cardinality);

        @Override
        Builder<RC, WC> addElement(XMLElement<RC, WC> element);

        @Override
        Builder<RC, WC> addChoice(XMLChoice<RC, WC> choice);

        @Override
        XMLChoice<RC, WC> build();
    }

    /**
     * Returns an empty choice, i.e. with no choices.
     * @param <RC> the reader context
     * @param <WC> the writer content
     */
    static <RC, WC> XMLChoice<RC, WC> empty() {
        return new DefaultXMLChoice<>(XMLCardinality.NONE, Map.of(), XMLContentWriter.empty());
    }

    /**
     * Returns a singleton choice of the specified element.
     * @param <RC> the reader context
     * @param <WC> the writer content
     */
    static <RC, WC> XMLChoice<RC, WC> singleton(XMLElement<RC, WC> element) {
        return new DefaultXMLChoice<>(element.getCardinality(), Map.of(element.getName(), element.getReader()), element.getWriter());
    }

    abstract class AbstractBuilder<RC, WC, B extends Builder<RC, WC>> extends XMLParticleGroup.AbstractBuilder<RC, WC, B> implements Builder<RC, WC> {
        private volatile XMLCardinality cardinality = XMLCardinality.Single.REQUIRED;

        protected AbstractBuilder() {
        }

        @Override
        public B withCardinality(XMLCardinality cardinality) {
            this.cardinality = cardinality;
            return this.builder();
        }

        @Override
        public XMLChoice<RC, WC> build() {
            List<XMLChoice<RC, WC>> choices = this.getChoices();
            return !choices.isEmpty() ? new DefaultXMLChoice<>(choices, this.cardinality) : empty();
        }
    }

    class DefaultBuilder<RC, WC> extends AbstractBuilder<RC, WC, Builder<RC, WC>> {
        DefaultBuilder() {
        }

        @Override
        protected Builder<RC, WC> builder() {
            return this;
        }
    }

    class DefaultXMLChoice<RC, WC> extends DefaultXMLParticle<RC, WC> implements XMLChoice<RC, WC> {
        private final Set<QName> names;
        private final Function<QName, XMLContentReader<RC>> readers;

        protected DefaultXMLChoice(Collection<XMLChoice<RC, WC>> choices, XMLCardinality cardinality) {
            this(choices, cardinality, new TreeMap<>(QNameResolver.COMPARATOR));
        }

        private DefaultXMLChoice(Collection<XMLChoice<RC, WC>> choices, XMLCardinality cardinality, Map<QName, XMLContentReader<RC>> readers) {
            this(cardinality, readers, new XMLContentWriter.DefaultXMLContentWriter<>(choices));
            for (XMLChoice<RC, WC> choice : choices) {
                for (QName name : choice.getChoices()) {
                    XMLContentReader<RC> existing = readers.put(name, choice.getReader(name));
                    if (existing != null) {
                        throw ControllerLogger.ROOT_LOGGER.duplicateElements(name);
                    }
                }
            }
        }

        DefaultXMLChoice(XMLCardinality cardinality, Map<QName, XMLContentReader<RC>> readers, XMLContentWriter<WC> writer) {
            this(readers.keySet(), cardinality, readers::get, writer);
        }

        protected DefaultXMLChoice(Set<QName> names, XMLCardinality cardinality, Function<QName, XMLContentReader<RC>> readers, XMLContentWriter<WC> writer) {
            super(cardinality, new XMLContentReader<RC>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
                    int occurrences = 0;
                    while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
                        QName name = reader.getName();
                        XMLElementReader<RC> choiceReader = readers.apply(name);
                        if (choiceReader == null) {
                            // Try matching w/out namespace (for PersistentResourceXMLDescription compatibility)
                            name = new QName(reader.getLocalName());
                            choiceReader = readers.apply(name);
                            if (choiceReader == null) {
                                throw ParseUtils.unexpectedElement(reader, names.stream().map(QName::getLocalPart).collect(Collectors.toSet()));
                            }
                        }
                        occurrences += 1;
                        OptionalInt maxOccurs = cardinality.getMaxOccurs();
                        // Validate maxOccurs
                        if (maxOccurs.isPresent() && (occurrences > maxOccurs.getAsInt())) {
                            throw ParseUtils.maxOccursExceeded(reader, names, cardinality);
                        }
                        choiceReader.readElement(reader, context);
                    }
                    // Validate minOccurs
                    if (occurrences < cardinality.getMinOccurs()) {
                        throw ParseUtils.minOccursNotReached(reader, names, cardinality);
                    }
                }
            }, writer);
            this.names = names;
            this.readers = readers;
        }

        @Override
        public Set<QName> getChoices() {
            return this.names;
        }

        @Override
        public XMLContentReader<RC> getReader(QName name) {
            return this.readers.apply(name);
        }

        @Override
        public int hashCode() {
            return this.getChoices().hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof XMLChoice)) return false;
            XMLChoice<?, ?> choice = (XMLChoice<?, ?>) object;
            return this.getChoices().equals(choice.getChoices());
        }

        @Override
        public String toString() {
            return this.getChoices().toString();
        }
    }
}
