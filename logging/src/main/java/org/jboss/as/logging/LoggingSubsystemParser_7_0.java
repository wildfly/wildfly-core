/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.APP_NAME;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.FACILITY;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.HOSTNAME;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.PORT;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.SERVER_ADDRESS;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.SYSLOG_FORMATTER;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Subsystem parser for 7.0 of the logging subsystem.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingSubsystemParser_7_0 extends LoggingSubsystemParser_6_0 {

    @Override
    void parseSyslogHandler(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
        final ModelNode operation = Util.createAddOperation();
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
                case ENABLED:
                    ENABLED.parseAndSetParameter(value, operation, reader);
                    break;
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

        // Setup the operation address
        addOperationAddress(operation, address, SyslogHandlerResourceDefinition.NAME, name);

        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case APP_NAME: {
                    APP_NAME.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case FACILITY: {
                    FACILITY.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case HOSTNAME: {
                    HOSTNAME.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                    break;
                }
                case FORMATTER: {
                    final EnumSet<Element> requiredFormatter = EnumSet.of(Element.SYSLOG_FORMATTER);
                    while (reader.nextTag() != END_ELEMENT) {
                        switch (Element.forName(reader.getLocalName())) {
                            case SYSLOG_FORMATTER: {
                                requiredFormatter.remove(Element.SYSLOG_FORMATTER);
                                operation.get(SYSLOG_FORMATTER.getName()).set(readStringAttributeElement(reader, Attribute.SYSLOG_TYPE.getLocalName()));
                                break;
                            }
                            case NAMED_FORMATTER: {
                                SyslogHandlerResourceDefinition.NAMED_FORMATTER.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                                break;
                            }
                            default: {
                                throw unexpectedElement(reader);
                            }
                        }
                    }
                    if (!requiredFormatter.isEmpty()) {
                        throw ParseUtils.missingRequiredElement(reader, requiredFormatter);
                    }
                    break;
                }
                case PORT: {
                    PORT.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case SERVER_ADDRESS: {
                    SERVER_ADDRESS.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        operations.add(operation);
    }
}
