/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.Set;

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
