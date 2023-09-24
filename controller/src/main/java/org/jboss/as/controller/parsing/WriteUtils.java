/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Utility methods for writing XML.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class WriteUtils {

    private static final char[] NEW_LINE = new char[]{'\n'};

    private WriteUtils() {
    }

    public static void writeAttribute(XMLExtendedStreamWriter writer, Attribute attribute, String value)
            throws XMLStreamException {
        writer.writeAttribute(attribute.getLocalName(), value);
    }

    public static void writeElement(final XMLExtendedStreamWriter writer, final Element element) throws XMLStreamException {
        writer.writeStartElement(element.getLocalName());
    }

    public static void writeSingleElement(final XMLExtendedStreamWriter writer, final Element element, final Attribute attribute, final ModelNode subModel) throws XMLStreamException {
        writer.writeEmptyElement(element.getLocalName());
        writeAttribute(writer, attribute, subModel.asString());
    }

    public static void writeListAsMultipleElements(final XMLExtendedStreamWriter writer, final Element element, Attribute attribute, final ModelNode subModel) throws XMLStreamException {
        final List<ModelNode> list = subModel.asList();
        for (final ModelNode node : list) {
            writer.writeEmptyElement(element.getLocalName());
            writeAttribute(writer, attribute, node.asString());
        }
    }

    public static void writeNewLine(XMLExtendedStreamWriter writer) throws XMLStreamException {
        writer.writeCharacters(NEW_LINE, 0, 1);
    }
}
