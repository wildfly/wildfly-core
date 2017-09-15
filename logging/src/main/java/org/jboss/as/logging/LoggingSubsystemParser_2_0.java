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

import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingSubsystemParser_2_0 extends LoggingSubsystemParser_1_4 {

    LoggingSubsystemParser_2_0() {
        //
    }

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
                case SIZE_ROTATING_FILE_HANDLER: {
                    parseSizeRotatingHandlerElement(reader, SUBSYSTEM_ADDRESS, handlerOperations, handlerNames);
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
                    break;
                }
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
}
