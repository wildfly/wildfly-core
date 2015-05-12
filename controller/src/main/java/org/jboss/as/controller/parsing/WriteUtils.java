/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
