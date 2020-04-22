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


package org.jboss.as.logging;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.logging.CommonAttributes.FILTER_PATTERN;
import static org.jboss.as.logging.CommonAttributes.MAX_INCLUSIVE;
import static org.jboss.as.logging.CommonAttributes.MAX_LEVEL;
import static org.jboss.as.logging.CommonAttributes.MIN_INCLUSIVE;
import static org.jboss.as.logging.CommonAttributes.MIN_LEVEL;
import static org.jboss.as.logging.CommonAttributes.NAME;
import static org.jboss.as.logging.CommonAttributes.PROPERTIES;
import static org.jboss.as.logging.CommonAttributes.REPLACEMENT;
import static org.jboss.as.logging.CommonAttributes.REPLACE_ALL;

import java.util.Collections;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.logging.filters.Filters;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class LoggingSubsystemParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>> {
    static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(LoggingResourceDefinition.SUBSYSTEM_PATH);

    /**
     * Appends the key and value to the address and sets the address on the operation.
     *
     * @param operation the operation to set the address on
     * @param base      the base address
     * @param key       the key for the new address
     * @param value     the value for the new address
     */
    static void addOperationAddress(final ModelNode operation, final PathAddress base, final String key, final String value) {
        operation.get(OP_ADDR).set(base.append(key, value).toModelNode());
    }

    /**
     * Reads the single {@code name} attribute from an element.
     *
     * @param reader the reader to use
     *
     * @return the value of the {@code name} attribute
     *
     * @throws XMLStreamException if the {@code name} attribute is not present, there is more than one attribute on the
     *                            element or there is content within the element.
     */
    static String readNameAttribute(final XMLExtendedStreamReader reader) throws XMLStreamException {
        return readStringAttributeElement(reader, Attribute.NAME.getLocalName());
    }

    /**
     * Reads the single {@code value} attribute from an element.
     *
     * @param reader the reader to use
     *
     * @return the value of the {@code value} attribute
     *
     * @throws XMLStreamException if the {@code value} attribute is not present, there is more than one attribute on the
     *                            element or there is content within the element.
     */
    static String readValueAttribute(final XMLExtendedStreamReader reader) throws XMLStreamException {
        return readStringAttributeElement(reader, Attribute.VALUE.getLocalName());
    }

    /**
     * Parses a property element.
     * <p>
     * Example of the expected XML:
     * <code>
     * <pre>
     *         &lt;properties&gt;
     *             &lt;property name=&quot;propertyName&quot; value=&quot;propertyValue&quot;/&gt;
     *         &lt;/properties&gt;
     *     </pre>
     * </code>
     *
     * The {@code name} attribute is required. If the {@code value} attribute is not present an
     * {@linkplain org.jboss.dmr.ModelNode UNDEFINED ModelNode} will set on the operation.
     * </p>
     *
     * @param operation the operation to add the parsed properties to
     * @param reader    the reader to use
     *
     * @throws XMLStreamException if a parsing error occurs
     */
    static void parsePropertyElement(final ModelNode operation, final XMLExtendedStreamReader reader) throws XMLStreamException {
        parsePropertyElement(operation, reader, PROPERTIES.getName());
    }

    /**
     * Parses a property element.
     * <p>
     *
     * The {@code name} attribute is required. If the {@code value} attribute is not present an
     * {@linkplain org.jboss.dmr.ModelNode UNDEFINED ModelNode} will set on the operation.
     * </p>
     *
     * @param operation   the operation to add the parsed properties to
     * @param reader      the reader to use
     * @param wrapperName the name of the attribute that wraps the key and value attributes
     *
     * @throws XMLStreamException if a parsing error occurs
     */
    static void parsePropertyElement(final ModelNode operation, final XMLExtendedStreamReader reader, final String wrapperName) throws XMLStreamException {
        while (reader.nextTag() != END_ELEMENT) {
            final int cnt = reader.getAttributeCount();
            String name = null;
            String value = null;
            for (int i = 0; i < cnt; i++) {
                requireNoNamespaceAttribute(reader, i);
                final String attrValue = reader.getAttributeValue(i);
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME: {
                        name = attrValue;
                        break;
                    }
                    case VALUE: {
                        value = attrValue;
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
            if (name == null) {
                throw missingRequired(reader, Collections.singleton(Attribute.NAME.getLocalName()));
            }
            operation.get(wrapperName).add(name, (value == null ? new ModelNode() : new ModelNode(value)));
            if (reader.nextTag() != END_ELEMENT) {
                throw unexpectedElement(reader);
            }
        }
    }

    /**
     * A helper to parse the deprecated {@code filter} for schema versions {@code 1.0} and {@code 1.1}. This parses the
     * XML and creates a {@code filter-spec} expression. The expression is set as the value for the {@code filter-spec}
     * attribute on the operation.
     *
     * @param operation the operation to add the parsed filter to
     * @param attribute the attribute for the filter-spec attribute
     * @param reader    the reader used to read the filter
     *
     * @throws XMLStreamException if a parsing error occurs
     */
    static void parseFilter(final ModelNode operation, final AttributeDefinition attribute, final XMLExtendedStreamReader reader) throws XMLStreamException {
        final StringBuilder filter = new StringBuilder();
        parseFilterChildren(filter, false, reader);
        operation.get(attribute.getName()).set(filter.toString());
    }

    private static void parseFilterChildren(final StringBuilder filter, final boolean useDelimiter, final XMLExtendedStreamReader reader) throws XMLStreamException {
        // No attributes
        ParseUtils.requireNoAttributes(reader);
        final char delimiter = ',';

        // Elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ACCEPT: {
                    filter.append(Filters.ACCEPT);
                    requireNoContent(reader);
                    break;
                }
                case ALL: {
                    filter.append(Filters.ALL).append('(');
                    parseFilterChildren(filter, true, reader);
                    // If the last character is a delimiter remove it
                    final int index = filter.length() - 1;
                    if (filter.charAt(index) == delimiter) {
                        filter.setCharAt(index, ')');
                    } else {
                        filter.append(')');
                    }
                    break;
                }
                case ANY: {
                    filter.append(Filters.ANY).append('(');
                    parseFilterChildren(filter, true, reader);
                    // If the last character is a delimiter remove it
                    final int index = filter.length() - 1;
                    if (filter.charAt(index) == delimiter) {
                        filter.setCharAt(index, ')');
                    } else {
                        filter.append(')');
                    }
                    break;
                }
                case CHANGE_LEVEL: {
                    filter.append(Filters.LEVEL_CHANGE)
                            .append('(')
                            .append(readStringAttributeElement(reader, CommonAttributes.NEW_LEVEL.getName()))
                            .append(')');
                    break;
                }
                case DENY: {
                    filter.append(Filters.DENY);
                    requireNoContent(reader);
                    break;
                }
                case LEVEL: {
                    filter.append(Filters.LEVELS)
                            .append('(')
                            .append(readStringAttributeElement(reader, NAME.getName()))
                            .append(')');
                    break;
                }
                case LEVEL_RANGE: {
                    filter.append(Filters.LEVEL_RANGE);
                    final boolean minInclusive = Boolean.parseBoolean(reader.getAttributeValue(null, MIN_INCLUSIVE.getName()));
                    final boolean maxInclusive = Boolean.parseBoolean(reader.getAttributeValue(null, MAX_INCLUSIVE.getName()));
                    if (minInclusive) {
                        filter.append('[');
                    } else {
                        filter.append('(');
                    }
                    filter.append(reader.getAttributeValue(null, MIN_LEVEL.getName())).append(delimiter);
                    filter.append(reader.getAttributeValue(null, MAX_LEVEL.getName()));
                    if (maxInclusive) {
                        filter.append(']');
                    } else {
                        filter.append(')');
                    }
                    requireNoContent(reader);
                    break;
                }
                case MATCH: {
                    filter.append(Filters.MATCH).append("(\"").append(readStringAttributeElement(reader, FILTER_PATTERN.getName())).append("\")");
                    break;
                }
                case NOT: {
                    filter.append(Filters.NOT).append('(');
                    parseFilterChildren(filter, true, reader);
                    // If the last character is a delimiter remove it
                    final int index = filter.length() - 1;
                    if (filter.charAt(index) == delimiter) {
                        filter.setCharAt(index, ')');
                    } else {
                        filter.append(')');
                    }
                    break;
                }
                case REPLACE: {
                    final boolean replaceAll = Boolean.valueOf(reader.getAttributeValue(null, REPLACE_ALL.getName()));
                    if (replaceAll) {
                        filter.append(Filters.SUBSTITUTE_ALL);
                    } else {
                        filter.append(Filters.SUBSTITUTE);
                    }
                    filter.append("(\"")
                            .append(reader.getAttributeValue(null, FILTER_PATTERN.getName()))
                            .append('"')
                            .append(delimiter)
                            .append('"')
                            .append(reader.getAttributeValue(null, REPLACEMENT.getName()))
                            .append("\")");
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
            if (useDelimiter) {
                filter.append(delimiter);
            }

        }
    }
}
