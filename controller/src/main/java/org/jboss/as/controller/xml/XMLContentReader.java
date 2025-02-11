/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * A readable model group of XML content.
 * @param <C> the reader context
 */
public interface XMLContentReader<C> extends XMLElementReader<C> {

    /**
     * Returns an empty content reader.
     * @param <C> the reader context
     * @return an empty content reader.
     */
    static <C> XMLContentReader<C> empty() {
        return new XMLContentReader<>() {
            @Override
            public void readElement(XMLExtendedStreamReader reader, C value) throws XMLStreamException {
                ParseUtils.requireNoContent(reader);
            }
        };
    }
}
