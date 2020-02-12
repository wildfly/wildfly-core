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
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.LOGGING_PROFILE;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.logging.handlers.SocketHandlerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Subsystem parser for 6.0 of the logging subsystem.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingSubsystemParser_6_0 extends LoggingSubsystemParser_5_0 {

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> operations) throws XMLStreamException {
        // No attributes
        ParseUtils.requireNoAttributes(reader);

        // Subsystem add operation
        final ModelNode subsystemAddOp = Util.createAddOperation(SUBSYSTEM_ADDRESS);
        operations.add(subsystemAddOp);

        final List<ModelNode> loggerOperations = new ArrayList<>();
        final List<ModelNode> asyncHandlerOperations = new ArrayList<>();
        final List<ModelNode> handlerOperations = new ArrayList<>();
        final List<ModelNode> formatterOperations = new ArrayList<>();

        // Elements
        final Set<String> loggerNames = new HashSet<>();
        final Set<String> handlerNames = new HashSet<>();
        final Set<String> formatterNames = new HashSet<>();
        boolean rootDefined = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ADD_LOGGING_API_DEPENDENCIES: {
                    final String value = ParseUtils.readStringAttributeElement(reader, Attribute.VALUE.getLocalName());
                    LoggingResourceDefinition.ADD_LOGGING_API_DEPENDENCIES.parseAndSetParameter(value, subsystemAddOp, reader);
                    break;
                }
                case USE_DEPLOYMENT_LOGGING_CONFIG: {
                    final String value = ParseUtils.readStringAttributeElement(reader, Attribute.VALUE.getLocalName());
                    LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG.parseAndSetParameter(value, subsystemAddOp, reader);
                    break;
                }
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
                case PERIODIC_SIZE_ROTATING_FILE_HANDLER: {
                    parsePeriodicSizeRotatingHandlerElement(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
                    break;
                }
                case SIZE_ROTATING_FILE_HANDLER: {
                    parseSizeRotatingHandlerElement(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
                    break;
                }
                case SOCKET_HANDLER: {
                    parseSocketHandlerElement(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
                    break;
                }
                case ASYNC_HANDLER: {
                    parseAsyncHandlerElement(reader, SUBSYSTEM_ADDRESS, asyncHandlerOperations, handlerNames);
                    break;
                }
                case SYSLOG_HANDLER: {
                    parseSyslogHandler(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
                    break;
                }
                case LOGGING_PROFILES: {
                    parseLoggingProfilesElement(reader, operations);
                }
                break;
                case FORMATTER: {
                    parseFormatter(reader, SUBSYSTEM_ADDRESS, formatterOperations, formatterNames);
                    break;
                }
                default: {
                    reader.handleAny(operations);
                    break;
                }
            }
        }
        operations.addAll(formatterOperations);
        operations.addAll(handlerOperations);
        operations.addAll(asyncHandlerOperations);
        operations.addAll(loggerOperations);
    }

    @Override
    void parseLoggingProfileElement(final XMLExtendedStreamReader reader, final List<ModelNode> operations, final Set<String> profileNames) throws XMLStreamException {
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
        if (!profileNames.add(name)) {
            throw duplicateNamedElement(reader, name);
        }
        // Setup the address
        final PathAddress profileAddress = SUBSYSTEM_ADDRESS.append(LOGGING_PROFILE, name);
        operations.add(Util.createAddOperation(profileAddress));

        final List<ModelNode> loggerOperations = new ArrayList<>();
        final List<ModelNode> asyncHandlerOperations = new ArrayList<>();
        final List<ModelNode> handlerOperations = new ArrayList<>();
        final List<ModelNode> formatterOperations = new ArrayList<>();

        final Set<String> loggerNames = new HashSet<>();
        final Set<String> handlerNames = new HashSet<>();
        final Set<String> formatterNames = new HashSet<>();
        boolean gotRoot = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case LOGGER: {
                    parseLoggerElement(reader, profileAddress, loggerOperations, loggerNames);
                    break;
                }
                case ROOT_LOGGER: {
                    if (gotRoot) {
                        throw unexpectedElement(reader);
                    }
                    gotRoot = true;
                    parseRootLoggerElement(reader, profileAddress, loggerOperations);
                    break;
                }
                case CONSOLE_HANDLER: {
                    parseConsoleHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case FILE_HANDLER: {
                    parseFileHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case CUSTOM_HANDLER: {
                    parseCustomHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case PERIODIC_ROTATING_FILE_HANDLER: {
                    parsePeriodicRotatingFileHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case PERIODIC_SIZE_ROTATING_FILE_HANDLER: {
                    parsePeriodicSizeRotatingHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case SIZE_ROTATING_FILE_HANDLER: {
                    parseSizeRotatingHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case SOCKET_HANDLER: {
                    parseSocketHandlerElement(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case ASYNC_HANDLER: {
                    parseAsyncHandlerElement(reader, profileAddress, asyncHandlerOperations, handlerNames);
                    break;
                }
                case SYSLOG_HANDLER: {
                    parseSyslogHandler(reader, profileAddress, handlerOperations, handlerNames);
                    break;
                }
                case FORMATTER: {
                    parseFormatter(reader, profileAddress, formatterOperations, formatterNames);
                    break;
                }
                default: {
                    reader.handleAny(operations);
                    break;
                }
            }
        }
        operations.addAll(formatterOperations);
        operations.addAll(handlerOperations);
        operations.addAll(asyncHandlerOperations);
        operations.addAll(loggerOperations);
    }

    @SuppressWarnings("WeakerAccess")
    void parseSocketHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> handlerNames) throws XMLStreamException {
        final ModelNode operation = Util.createAddOperation();
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.OUTBOUND_SOCKET_BINDING_REF);
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
                case BLOCK_ON_RECONNECT: {
                    SocketHandlerResourceDefinition.BLOCK_ON_RECONNECT.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case ENABLED: {
                    ENABLED.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case OUTBOUND_SOCKET_BINDING_REF: {
                    SocketHandlerResourceDefinition.OUTBOUND_SOCKET_BINDING_REF.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case SSL_CONTEXT: {
                    SocketHandlerResourceDefinition.SSL_CONTEXT.parseAndSetParameter(value, operation, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!handlerNames.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation address
        addOperationAddress(operation, address, SocketHandlerResourceDefinition.NAME, name);

        final EnumSet<Element> requiredElem = EnumSet.of(Element.NAMED_FORMATTER);
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
                case FILTER_SPEC: {
                    SocketHandlerResourceDefinition.FILTER_SPEC.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case NAMED_FORMATTER: {
                    SocketHandlerResourceDefinition.NAMED_FORMATTER.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                    break;
                }
                case PROTOCOL: {
                    SocketHandlerResourceDefinition.PROTOCOL.parseAndSetParameter(readValueAttribute(reader), operation, reader);
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
