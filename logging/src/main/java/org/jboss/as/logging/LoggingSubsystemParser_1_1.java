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

import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH;
import static org.jboss.as.controller.services.path.PathResourceDefinition.RELATIVE_TO;
import static org.jboss.as.logging.AbstractHandlerDefinition.FORMATTER;
import static org.jboss.as.logging.AsyncHandlerResourceDefinition.ASYNC_HANDLER;
import static org.jboss.as.logging.AsyncHandlerResourceDefinition.OVERFLOW_ACTION;
import static org.jboss.as.logging.AsyncHandlerResourceDefinition.QUEUE_LENGTH;
import static org.jboss.as.logging.AsyncHandlerResourceDefinition.SUBHANDLERS;
import static org.jboss.as.logging.CommonAttributes.APPEND;
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.CLASS;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.FILE;
import static org.jboss.as.logging.CommonAttributes.HANDLERS;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.MODULE;
import static org.jboss.as.logging.ConsoleHandlerResourceDefinition.CONSOLE_HANDLER;
import static org.jboss.as.logging.ConsoleHandlerResourceDefinition.TARGET;
import static org.jboss.as.logging.CustomHandlerResourceDefinition.CUSTOM_HANDLER;
import static org.jboss.as.logging.FileHandlerResourceDefinition.FILE_HANDLER;
import static org.jboss.as.logging.LoggerResourceDefinition.LOGGER;
import static org.jboss.as.logging.LoggerResourceDefinition.USE_PARENT_HANDLERS;
import static org.jboss.as.logging.PeriodicHandlerResourceDefinition.PERIODIC_ROTATING_FILE_HANDLER;
import static org.jboss.as.logging.PeriodicHandlerResourceDefinition.SUFFIX;
import static org.jboss.as.logging.RootLoggerResourceDefinition.ROOT_LOGGER_ATTRIBUTE_NAME;
import static org.jboss.as.logging.RootLoggerResourceDefinition.ROOT_LOGGER_PATH_NAME;
import static org.jboss.as.logging.SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX;
import static org.jboss.as.logging.SizeRotatingHandlerResourceDefinition.ROTATE_SIZE;
import static org.jboss.as.logging.SizeRotatingHandlerResourceDefinition.SIZE_ROTATING_FILE_HANDLER;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingSubsystemParser_1_1 extends LoggingSubsystemParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>> {

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


    private static void parseLoggerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
        final ModelNode operation = Util.createAddOperation();
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.CATEGORY);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case CATEGORY: {
                    name = value;
                    break;
                }
                case USE_PARENT_HANDLERS: {
                    USE_PARENT_HANDLERS.parseAndSetParameter(value, operation, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        assert name != null;
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation address
        addOperationAddress(operation, address, LOGGER, name);

        // Element
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw duplicateNamedElement(reader, reader.getLocalName());
            }
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                    break;
                }
                case HANDLERS: {
                    parseHandlersElement(operation.get(HANDLERS.getName()), reader);
                    break;
                }
                case FILTER: {
                    parseFilter(operation, reader);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
        operations.add(operation);
    }

    private static void parseAsyncHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
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
        addOperationAddress(operation, address, ASYNC_HANDLER, name);

        // Elements
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
                case SUBHANDLERS: {
                    parseHandlersElement(operation.get(SUBHANDLERS.getName()), reader);
                    break;
                }
                case FILTER: {
                    parseFilter(operation, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, operation);
                    break;
                }
                case QUEUE_LENGTH: {
                    QUEUE_LENGTH.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case OVERFLOW_ACTION: {
                    OVERFLOW_ACTION.parseAndSetParameter(readValueAttribute(reader).toUpperCase(Locale.US), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        operations.add(operation);
    }

    private static void parseRootLoggerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations) throws XMLStreamException {
        // No attributes
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }

        final ModelNode operation = Util.createAddOperation(address.append(ROOT_LOGGER_PATH_NAME, ROOT_LOGGER_ATTRIBUTE_NAME));
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (encountered.contains(element)) {
                throw duplicateNamedElement(reader, reader.getLocalName());
            }
            encountered.add(element);
            switch (element) {
                case FILTER: {
                    parseFilter(operation, reader);
                    break;
                }
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                    break;
                }
                case HANDLERS: {
                    parseHandlersElement(operation.get(HANDLERS.getName()), reader);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
        operations.add(operation);
    }

    private static void parseConsoleHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
        final ModelNode operation = Util.createAddOperation();
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
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, operation, reader);
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

        // Set-up the operation address
        addOperationAddress(operation, address, CONSOLE_HANDLER, name);

        // Elements
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
                    parseFilter(operation, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, operation);
                    break;
                }
                case TARGET: {
                    final String target = readNameAttribute(reader);
                    TARGET.parseAndSetParameter(target, operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        operations.add(operation);
    }

    private static void parseFileHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
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
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, operation, reader);
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
        addOperationAddress(operation, address, FILE_HANDLER, name);

        // Elements
        final EnumSet<Element> requiredElem = EnumSet.of(Element.FILE);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            requiredElem.remove(element);
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
                    parseFilter(operation, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, operation);
                    break;
                }
                case FILE: {
                    parseFileElement(operation.get(FILE.getName()), reader);
                    break;
                }
                case APPEND: {
                    APPEND.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        if (!requiredElem.isEmpty()) {
            throw missingRequired(reader, requiredElem);
        }
        operations.add(operation);
    }

    private static void parseCustomHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
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
        addOperationAddress(operation, address, CUSTOM_HANDLER, name);


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
                    parseFilter(operation, reader);
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

    private static void parsePeriodicRotatingFileHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
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
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, operation, reader);
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
        addOperationAddress(operation, address, PERIODIC_ROTATING_FILE_HANDLER, name);

        final EnumSet<Element> requiredElem = EnumSet.of(Element.FILE, Element.SUFFIX);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            requiredElem.remove(element);
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
                    parseFilter(operation, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, operation);
                    break;
                }
                case FILE: {
                    parseFileElement(operation.get(FILE.getName()), reader);
                    break;
                }
                case APPEND: {
                    APPEND.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case SUFFIX: {
                    SUFFIX.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        if (!requiredElem.isEmpty()) {
            throw missingRequired(reader, requiredElem);
        }
        operations.add(operation);
    }

    private static void parseSizeRotatingHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
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
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, operation, reader);
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
        addOperationAddress(operation, address, SIZE_ROTATING_FILE_HANDLER, name);

        final EnumSet<Element> requiredElem = EnumSet.of(Element.FILE);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            requiredElem.remove(element);
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
                    parseFilter(operation, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, operation);
                    break;
                }
                case FILE: {
                    parseFileElement(operation.get(FILE.getName()), reader);
                    break;
                }
                case APPEND: {
                    APPEND.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case ROTATE_SIZE: {
                    ROTATE_SIZE.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case MAX_BACKUP_INDEX: {
                    MAX_BACKUP_INDEX.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        operations.add(operation);
    }

    private static void parseFileElement(final ModelNode operation, final XMLExtendedStreamReader reader) throws XMLStreamException {
        final EnumSet<Attribute> required = EnumSet.of(Attribute.PATH);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case PATH: {
                    PATH.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case RELATIVE_TO: {
                    RELATIVE_TO.parseAndSetParameter(value, operation, reader);
                    break;
                }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }
        requireNoContent(reader);
    }

    private static void parseHandlerFormatterElement(final XMLExtendedStreamReader reader, final ModelNode operation) throws XMLStreamException {
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }
        boolean formatterDefined = false;
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case PATTERN_FORMATTER: {
                    if (formatterDefined) {
                        throw unexpectedElement(reader);
                    }
                    requireSingleAttribute(reader, PatternFormatterResourceDefinition.PATTERN.getName());
                    formatterDefined = true;
                    FORMATTER.parseAndSetParameter(readStringAttributeElement(reader, PatternFormatterResourceDefinition.PATTERN.getName()), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private static void parseHandlersElement(final ModelNode operation, final XMLExtendedStreamReader reader) throws XMLStreamException {
        // No attributes
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }

        // Elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case HANDLER: {
                    operation.add(readNameAttribute(reader));
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }
}
