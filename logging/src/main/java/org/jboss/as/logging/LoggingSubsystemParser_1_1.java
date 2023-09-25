/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.logging.CommonAttributes.CLASS;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.MODULE;
import static org.jboss.as.logging.handlers.CustomHandlerResourceDefinition.NAME;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.logging.handlers.CustomHandlerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingSubsystemParser_1_1 extends LoggingSubsystemParser_1_0 {

    LoggingSubsystemParser_1_1() {
        //
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> operations) throws XMLStreamException {
        // No attributes
        ParseUtils.requireNoAttributes(reader);

        // Subsystem add operation
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDRESS));

        final List<ModelNode> loggerOperations = new ArrayList<>();
        final List<ModelNode> asyncHandlerOperations = new ArrayList<>();
        final List<ModelNode> handlerOperations = new ArrayList<>();

        // Elements
        final Set<String> loggerNames = new HashSet<>();
        final Set<String> handlerNames = new HashSet<>();
        boolean rootDefined = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case LOGGER: {
                    parseLoggerElement(reader, SUBSYSTEM_ADDRESS, loggerOperations, loggerNames);
                    break;
                }
                case ROOT_LOGGER: {
                    if (rootDefined) {
                        throw unexpectedElement(reader);
                    }
                    rootDefined = true;
                    parseRootLoggerElement(reader, SUBSYSTEM_ADDRESS, loggerOperations);
                    break;
                }
                case CONSOLE_HANDLER: {
                    parseConsoleHandlerElement(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
                    break;
                }
                case FILE_HANDLER: {
                    parseFileHandlerElement(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
                    break;
                }
                case CUSTOM_HANDLER: {
                    parseCustomHandlerElement(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
                    break;
                }
                case PERIODIC_ROTATING_FILE_HANDLER: {
                    parsePeriodicRotatingFileHandlerElement(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
                    break;
                }
                case SIZE_ROTATING_FILE_HANDLER: {
                    parseSizeRotatingHandlerElement(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
                    break;
                }
                case ASYNC_HANDLER: {
                    parseAsyncHandlerElement(reader, SUBSYSTEM_ADDRESS, asyncHandlerOperations, handlerNames);
                    break;
                }
                default: {
                    reader.handleAny(operations);
                    break;
                }
            }
        }
        operations.addAll(handlerOperations);
        operations.addAll(asyncHandlerOperations);
        operations.addAll(loggerOperations);
    }

    void parseCustomHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
        final ModelNode operation = Util.createAddOperation();
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.CLASS, Attribute.MODULE);
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
                case CLASS: {
                    CLASS.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case MODULE: {
                    MODULE.parseAndSetParameter(value, operation, reader);
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
        // Setup the operation address
        addOperationAddress(operation, address, NAME, name);


        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                    break;
                }
                case ENCODING: {
                    ENCODING.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case FILTER: {
                    parseFilter(operation, CustomHandlerResourceDefinition.FILTER_SPEC, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, operation);
                    break;
                }
                case PROPERTIES: {
                    parsePropertyElement(operation, reader);
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
