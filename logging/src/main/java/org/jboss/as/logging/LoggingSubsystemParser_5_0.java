/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.logging;

import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.logging.formatters.CustomFormatterResourceDefinition;
import org.jboss.as.logging.formatters.JsonFormatterResourceDefinition;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.formatters.StructuredFormatterResourceDefinition;
import org.jboss.as.logging.formatters.XmlFormatterResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Subsystem parser for 4.0 of the logging subsystem.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("WeakerAccess")
class LoggingSubsystemParser_5_0 extends LoggingSubsystemParser_4_0 {

    void parseFormatter(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case PATTERN_FORMATTER: {
                    final ModelNode operation = Util.createAddOperation();
                    // Setup the operation address
                    addOperationAddress(operation, address, PatternFormatterResourceDefinition.NAME, name);
                    parsePatternFormatterElement(reader, operation);
                    operations.add(operation);
                    break;
                }
                case CUSTOM_FORMATTER: {
                    final ModelNode operation = Util.createAddOperation();
                    // Setup the operation address
                    addOperationAddress(operation, address, CustomFormatterResourceDefinition.NAME, name);
                    parseCustomFormatterElement(reader, operation);
                    operations.add(operation);
                    break;
                }
                case JSON_FORMATTER: {
                    final ModelNode operation = Util.createAddOperation();
                    // Setup the operation address
                    addOperationAddress(operation, address, JsonFormatterResourceDefinition.NAME, name);
                    parseStructuredFormatter(reader, operation);
                    operations.add(operation);
                    break;
                }
                case XML_FORMATTER: {
                    final ModelNode operation = Util.createAddOperation();
                    // Setup the operation address
                    addOperationAddress(operation, address, XmlFormatterResourceDefinition.NAME, name);
                    parseStructuredFormatter(reader, operation, XmlFormatterResourceDefinition.NAMESPACE_URI,
                            XmlFormatterResourceDefinition.PRINT_NAMESPACE);
                    operations.add(operation);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    void parseStructuredFormatter(final XMLExtendedStreamReader reader, final ModelNode operation,
                                  final SimpleAttributeDefinition... additionalAttributes) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String attributeName = reader.getAttributeLocalName(i);
            final String value = reader.getAttributeValue(i);
            if (attributeName.equals(StructuredFormatterResourceDefinition.DATE_FORMAT.getXmlName())) {
                StructuredFormatterResourceDefinition.DATE_FORMAT.parseAndSetParameter(value, operation, reader);
            } else if (attributeName.equals(StructuredFormatterResourceDefinition.PRETTY_PRINT.getXmlName())) {
                StructuredFormatterResourceDefinition.PRETTY_PRINT.parseAndSetParameter(value, operation, reader);
            } else if (attributeName.equals(StructuredFormatterResourceDefinition.PRINT_DETAILS.getXmlName())) {
                StructuredFormatterResourceDefinition.PRINT_DETAILS.parseAndSetParameter(value, operation, reader);
            } else if (attributeName.equals(StructuredFormatterResourceDefinition.ZONE_ID.getXmlName())) {
                StructuredFormatterResourceDefinition.ZONE_ID.parseAndSetParameter(value, operation, reader);
            } else {
                boolean invalid = true;
                for (SimpleAttributeDefinition ad : additionalAttributes) {
                    if (attributeName.equals(ad.getXmlName())) {
                        invalid = false;
                        ad.parseAndSetParameter(value, operation, reader);
                        break;
                    }
                }
                if (invalid) {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }


        final Set<String> encountered = new HashSet<>();
        while (reader.nextTag() != END_ELEMENT) {
            final String elementName = reader.getLocalName();
            if (!encountered.add(elementName)) {
                throw unexpectedElement(reader);
            }
            if (elementName.equals(StructuredFormatterResourceDefinition.EXCEPTION_OUTPUT_TYPE.getXmlName())) {
                StructuredFormatterResourceDefinition.EXCEPTION_OUTPUT_TYPE.parseAndSetParameter(readValueAttribute(reader), operation, reader);
            } else if (elementName.equals(StructuredFormatterResourceDefinition.RECORD_DELIMITER.getXmlName())) {
                StructuredFormatterResourceDefinition.RECORD_DELIMITER.parseAndSetParameter(readValueAttribute(reader), operation, reader);
            } else if (elementName.equals(StructuredFormatterResourceDefinition.KEY_OVERRIDES.getXmlName())) {
                StructuredFormatterResourceDefinition.KEY_OVERRIDES.getParser().parseElement(StructuredFormatterResourceDefinition.KEY_OVERRIDES, reader, operation);
            } else if (elementName.equals(StructuredFormatterResourceDefinition.META_DATA.getXmlName())) {
                parsePropertyElement(operation, reader, StructuredFormatterResourceDefinition.META_DATA.getName());
            } else {
                throw unexpectedElement(reader);
            }
        }
    }
}
