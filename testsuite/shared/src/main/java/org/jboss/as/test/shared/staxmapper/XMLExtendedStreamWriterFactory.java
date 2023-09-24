/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.shared.staxmapper;

import javax.xml.stream.XMLStreamWriter;

import org.jboss.staxmapper.FormattingXMLStreamWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Expose the package protected {@link org.jboss.staxmapper.FormattingXMLStreamWriter} to tests.
 * @author Paul Ferraro
 */
public class XMLExtendedStreamWriterFactory {
    public static XMLExtendedStreamWriter create(XMLStreamWriter writer) throws Exception {
        return new FormattingXMLStreamWriter(writer);
    }
}