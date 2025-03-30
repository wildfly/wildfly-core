/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.wildfly.common.Assert;

/**
 * Adds absentee element handling to an {link org.jboss.staxmapper.XMLElementReader}.
 * @author Paul Ferraro
 * @param <C> the reader context type
 */
public interface XMLElementReader<C> extends org.jboss.staxmapper.XMLElementReader<C>, XMLComponentReader<C> {

    @Override
    default void whenAbsent(C context) {
        // Do nothing
    }

    /**
     * Returns a new reader whose context is mapped from the specified function.
     * @param mapper a context mapping function
     * @param <T> the mapped context type
     * @return a new writer whose context is mapped from the specified function.
     */
    default <T> XMLElementReader<T> map(Function<T, C> mapper) {
        return new XMLElementReader<>() {
            @Override
            public void readElement(XMLExtendedStreamReader reader, T context) throws XMLStreamException {
                XMLElementReader.this.readElement(reader, mapper.apply(context));
            }

            @Override
            public void whenAbsent(T context) {
                XMLElementReader.this.whenAbsent(mapper.apply(context));
            }
        };
    }

    /**
     * Returns a new reader whose context is generated from the specified factory and applied via the specified consumer.
     * @param contextFactory supplier of the read context
     * @param applicator application of the read context
     * @param <T> the mapped context type
     * @return a new writer whose context is generated from the specified factory and applied via the specified consumer.
     */
    default <T> XMLElementReader<T> withContext(Supplier<C> contextFactory, BiConsumer<T, C> applicator) {
        return new XMLElementReader<>() {
            @Override
            public void readElement(XMLExtendedStreamReader reader, T parentContext) throws XMLStreamException {
                C context = contextFactory.get();
                XMLElementReader.this.readElement(reader, context);
                applicator.accept(parentContext, context);
            }

            @Override
            public void whenAbsent(T parentContext) {
                C context = contextFactory.get();
                XMLElementReader.this.whenAbsent(context);
                applicator.accept(parentContext, context);
            }
        };
    }

    /**
     * Decorates an {@link XMLElementReader} with entry/exit criteria validation.
     * @param <C> the read context
     * @param name the name of the expected element
     * @param elementReader the reader of an element
     * @return a validating reader
     */
    static <C> XMLElementReader<C> validate(QName expected, XMLElementReader<C> elementReader) {
        return new XMLElementReader<>() {
            @Override
            public void readElement(XMLExtendedStreamReader reader, C value) throws XMLStreamException {
                // Validate entry criteria
                Assert.assertTrue(reader.isStartElement());
                if (!reader.getName().equals(expected)) {
                    throw ParseUtils.unexpectedElement(reader, Set.of(expected));
                }
                elementReader.readElement(reader, value);
                // Validate exit criteria
                if (!reader.isEndElement()) {
                    throw ParseUtils.unexpectedElement(reader);
                }
                if (!reader.getName().equals(expected)) {
                    throw ParseUtils.unexpectedEndElement(reader);
                }
            }

            @Override
            public void whenAbsent(C context) {
                elementReader.whenAbsent(context);
            }
        };
    }
}
