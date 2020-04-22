/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.logging.CommonAttributes.APPEND;
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.CLASS;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.FILE;
import static org.jboss.as.logging.loggers.LoggerAttributes.HANDLERS;
import static org.jboss.as.logging.CommonAttributes.HANDLER_NAME;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.LOGGING_PROFILE;
import static org.jboss.as.logging.CommonAttributes.LOGGING_PROFILES;
import static org.jboss.as.logging.CommonAttributes.MODULE;
import static org.jboss.as.logging.CommonAttributes.NAME;
import static org.jboss.as.logging.CommonAttributes.PROPERTIES;
import static org.jboss.as.logging.handlers.AbstractHandlerDefinition.FORMATTER;
import static org.jboss.as.logging.handlers.AbstractHandlerDefinition.NAMED_FORMATTER;
import static org.jboss.as.logging.handlers.AsyncHandlerResourceDefinition.OVERFLOW_ACTION;
import static org.jboss.as.logging.handlers.AsyncHandlerResourceDefinition.QUEUE_LENGTH;
import static org.jboss.as.logging.handlers.AsyncHandlerResourceDefinition.SUBHANDLERS;
import static org.jboss.as.logging.handlers.ConsoleHandlerResourceDefinition.TARGET;
import static org.jboss.as.logging.handlers.PeriodicHandlerResourceDefinition.SUFFIX;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.ROTATE_ON_BOOT;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.ROTATE_SIZE;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.APP_NAME;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.FACILITY;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.HOSTNAME;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.PORT;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.SERVER_ADDRESS;
import static org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition.SYSLOG_FORMATTER;
import static org.jboss.as.logging.loggers.LoggerResourceDefinition.CATEGORY;
import static org.jboss.as.logging.loggers.LoggerResourceDefinition.USE_PARENT_HANDLERS;
import static org.jboss.as.logging.loggers.RootLoggerResourceDefinition.RESOURCE_NAME;

import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.logging.filters.FilterResourceDefinition;
import org.jboss.as.logging.formatters.CustomFormatterResourceDefinition;
import org.jboss.as.logging.formatters.JsonFormatterResourceDefinition;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.formatters.StructuredFormatterResourceDefinition;
import org.jboss.as.logging.formatters.XmlFormatterResourceDefinition;
import org.jboss.as.logging.handlers.AbstractHandlerDefinition;
import org.jboss.as.logging.handlers.AsyncHandlerResourceDefinition;
import org.jboss.as.logging.handlers.ConsoleHandlerResourceDefinition;
import org.jboss.as.logging.handlers.CustomHandlerResourceDefinition;
import org.jboss.as.logging.handlers.FileHandlerResourceDefinition;
import org.jboss.as.logging.handlers.PeriodicHandlerResourceDefinition;
import org.jboss.as.logging.handlers.PeriodicSizeRotatingHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SocketHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition;
import org.jboss.as.logging.loggers.LoggerAttributes;
import org.jboss.as.logging.loggers.LoggerResourceDefinition;
import org.jboss.as.logging.loggers.RootLoggerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingSubsystemWriter implements XMLStreamConstants, XMLElementWriter<SubsystemMarshallingContext> {

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(Namespace.CURRENT.getUriString(), false);

        ModelNode model = context.getModelNode();

        // Marshall attributes
        for (AttributeDefinition attribute : LoggingResourceDefinition.ATTRIBUTES) {
            attribute.marshallAsElement(model, false, writer);
        }

        writeContent(writer, model);

        if (model.hasDefined(LOGGING_PROFILE)) {
            final List<Property> profiles = model.get(LOGGING_PROFILE).asPropertyList();
            if (!profiles.isEmpty()) {
                writer.writeStartElement(LOGGING_PROFILES);
                for (Property profile : profiles) {
                    final String name = profile.getName();
                    writer.writeStartElement(LOGGING_PROFILE);
                    writer.writeAttribute(Attribute.NAME.getLocalName(), name);
                    writeContent(writer, profile.getValue());
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
    }

    private void writeContent(final XMLExtendedStreamWriter writer, final ModelNode model) throws XMLStreamException {

        if (model.hasDefined(AsyncHandlerResourceDefinition.NAME)) {
            final ModelNode handlers = model.get(AsyncHandlerResourceDefinition.NAME);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeAsynchHandler(writer, handler, name);
                }
            }
        }
        if (model.hasDefined(ConsoleHandlerResourceDefinition.NAME)) {
            final ModelNode handlers = model.get(ConsoleHandlerResourceDefinition.NAME);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeConsoleHandler(writer, handler, name);
                }
            }
        }
        if (model.hasDefined(FileHandlerResourceDefinition.NAME)) {
            final ModelNode handlers = model.get(FileHandlerResourceDefinition.NAME);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeFileHandler(writer, handler, name);
                }
            }
        }
        if (model.hasDefined(CustomHandlerResourceDefinition.NAME)) {
            final ModelNode handlers = model.get(CustomHandlerResourceDefinition.NAME);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeCustomHandler(writer, handler, name);
                }
            }
        }
        if (model.hasDefined(PeriodicHandlerResourceDefinition.NAME)) {
            final ModelNode handlers = model.get(PeriodicHandlerResourceDefinition.NAME);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writePeriodicRotatingFileHandler(writer, handler, name);
                }
            }
        }
        if (model.hasDefined(PeriodicSizeRotatingHandlerResourceDefinition.NAME)) {
            final ModelNode handlers = model.get(PeriodicSizeRotatingHandlerResourceDefinition.NAME);
            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writePeriodicSizeRotatingFileHandler(writer, handler, name);
                }
            }
        }
        if (model.hasDefined(SizeRotatingHandlerResourceDefinition.NAME)) {
            final ModelNode handlers = model.get(SizeRotatingHandlerResourceDefinition.NAME);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeSizeRotatingFileHandler(writer, handler, name);
                }
            }
        }
        if (model.hasDefined(SocketHandlerResourceDefinition.NAME)) {
            final ModelNode handlers = model.get(SocketHandlerResourceDefinition.NAME);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeSocketHandler(writer, handler, name);
                }
            }
        }
        if (model.hasDefined(SyslogHandlerResourceDefinition.NAME)) {
            final ModelNode handlers = model.get(SyslogHandlerResourceDefinition.NAME);

            for (Property handlerProp : handlers.asPropertyList()) {
                final String name = handlerProp.getName();
                final ModelNode handler = handlerProp.getValue();
                if (handler.isDefined()) {
                    writeSyslogHandler(writer, handler, name);
                }
            }
        }
        if (model.hasDefined(LoggerResourceDefinition.NAME)) {
            for (String name : model.get(LoggerResourceDefinition.NAME).keys()) {
                writeLogger(writer, name, model.get(LoggerResourceDefinition.NAME, name));
            }
        }
        if (model.hasDefined(RootLoggerResourceDefinition.NAME)) {
            writeRootLogger(writer, model.get(RootLoggerResourceDefinition.NAME, RESOURCE_NAME));
        }

        writeFormatters(writer, PatternFormatterResourceDefinition.NAME, PatternFormatterResourceDefinition.PATTERN_FORMATTER, model);
        writeFormatters(writer, CustomFormatterResourceDefinition.NAME, CustomFormatterResourceDefinition.CUSTOM_FORMATTER, model);
        writeStructuredFormatters(writer, JsonFormatterResourceDefinition.NAME, model);
        writeStructuredFormatters(writer, XmlFormatterResourceDefinition.NAME, model,
                XmlFormatterResourceDefinition.PRINT_NAMESPACE, XmlFormatterResourceDefinition.NAMESPACE_URI);

        // Write the filters
        if (model.hasDefined(FilterResourceDefinition.NAME)) {
            for (String name : model.get(FilterResourceDefinition.NAME).keys()) {
                writeFilterElement(writer, model.get(FilterResourceDefinition.NAME, name), name);
            }
        }
    }

    private void writeCommonLogger(final XMLExtendedStreamWriter writer, final ModelNode model) throws XMLStreamException {
        LEVEL.marshallAsElement(model, writer);
        LoggerAttributes.FILTER_SPEC.marshallAsElement(model, writer);
        HANDLERS.marshallAsElement(model, writer);
    }

    private void writeCommonHandler(final XMLExtendedStreamWriter writer, final ModelNode model) throws XMLStreamException {
        LEVEL.marshallAsElement(model, writer);
        ENCODING.marshallAsElement(model, writer);
        AbstractHandlerDefinition.FILTER_SPEC.marshallAsElement(model, writer);
        FORMATTER.marshallAsElement(model, writer);
        NAMED_FORMATTER.marshallAsElement(model, writer);
    }

    private void writeConsoleHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name)
            throws XMLStreamException {
        writer.writeStartElement(Element.CONSOLE_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        AUTOFLUSH.marshallAsAttribute(model, writer);
        ENABLED.marshallAsAttribute(model, false, writer);
        writeCommonHandler(writer, model);
        TARGET.marshallAsElement(model, writer);
        writer.writeEndElement();
    }

    private void writeFileHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.FILE_HANDLER.getLocalName());
        writer.writeAttribute(Attribute.NAME.getLocalName(), name);
        AUTOFLUSH.marshallAsAttribute(model, writer);
        ENABLED.marshallAsAttribute(model, false, writer);
        writeCommonHandler(writer, model);
        FILE.marshallAsElement(model, writer);
        APPEND.marshallAsElement(model, writer);

        writer.writeEndElement();
    }

    private void writeCustomHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name)
            throws XMLStreamException {
        writer.writeStartElement(Element.CUSTOM_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        CLASS.marshallAsAttribute(model, writer);
        MODULE.marshallAsAttribute(model, writer);
        ENABLED.marshallAsAttribute(model, false, writer);
        writeCommonHandler(writer, model);
        PROPERTIES.marshallAsElement(model, writer);

        writer.writeEndElement();
    }

    private void writePeriodicRotatingFileHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.PERIODIC_ROTATING_FILE_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        AUTOFLUSH.marshallAsAttribute(model, writer);
        ENABLED.marshallAsAttribute(model, false, writer);
        writeCommonHandler(writer, model);
        FILE.marshallAsElement(model, writer);
        SUFFIX.marshallAsElement(model, writer);
        APPEND.marshallAsElement(model, writer);

        writer.writeEndElement();
    }

    private void writePeriodicSizeRotatingFileHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.PERIODIC_SIZE_ROTATING_FILE_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        AUTOFLUSH.marshallAsAttribute(model, writer);
        ENABLED.marshallAsAttribute(model, false, writer);
        ROTATE_ON_BOOT.marshallAsAttribute(model, false, writer);
        writeCommonHandler(writer, model);
        FILE.marshallAsElement(model, writer);
        ROTATE_SIZE.marshallAsElement(model, writer);
        MAX_BACKUP_INDEX.marshallAsElement(model, writer);
        SUFFIX.marshallAsElement(model, writer);
        APPEND.marshallAsElement(model, writer);

        writer.writeEndElement();
    }

    private void writeSizeRotatingFileHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.SIZE_ROTATING_FILE_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        AUTOFLUSH.marshallAsAttribute(model, writer);
        ENABLED.marshallAsAttribute(model, false, writer);
        ROTATE_ON_BOOT.marshallAsAttribute(model, false, writer);
        writeCommonHandler(writer, model);
        FILE.marshallAsElement(model, writer);
        ROTATE_SIZE.marshallAsElement(model, writer);
        MAX_BACKUP_INDEX.marshallAsElement(model, writer);
        APPEND.marshallAsElement(model, writer);
        SizeRotatingHandlerResourceDefinition.SUFFIX.marshallAsElement(model, writer);

        writer.writeEndElement();
    }

    private void writeSocketHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.SOCKET_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        AUTOFLUSH.marshallAsAttribute(model, writer);
        SocketHandlerResourceDefinition.BLOCK_ON_RECONNECT.marshallAsAttribute(model, false, writer);
        ENABLED.marshallAsAttribute(model, false, writer);
        SocketHandlerResourceDefinition.OUTBOUND_SOCKET_BINDING_REF.marshallAsAttribute(model, writer);
        SocketHandlerResourceDefinition.SSL_CONTEXT.marshallAsAttribute(model, writer);

        ENCODING.marshallAsElement(model, writer);
        AbstractHandlerDefinition.FILTER_SPEC.marshallAsElement(model, writer);
        LEVEL.marshallAsElement(model, writer);
        SocketHandlerResourceDefinition.NAMED_FORMATTER.marshallAsElement(model, writer);
        SocketHandlerResourceDefinition.PROTOCOL.marshallAsElement(model, writer);

        writer.writeEndElement();
    }

    private void writeSyslogHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.SYSLOG_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        ENABLED.marshallAsAttribute(model, false, writer);
        LEVEL.marshallAsElement(model, writer);
        SERVER_ADDRESS.marshallAsElement(model, writer);
        HOSTNAME.marshallAsElement(model, writer);
        PORT.marshallAsElement(model, writer);
        APP_NAME.marshallAsElement(model, writer);

        // Write the formatter elements
        if (model.hasDefined(SYSLOG_FORMATTER.getName()) || model.hasDefined(SyslogHandlerResourceDefinition.NAMED_FORMATTER.getName())) {
            writer.writeStartElement(AbstractHandlerDefinition.FORMATTER.getXmlName());
            SYSLOG_FORMATTER.marshallAsElement(model, writer);
            SyslogHandlerResourceDefinition.NAMED_FORMATTER.marshallAsElement(model, writer);
            writer.writeEndElement();
        }

        FACILITY.marshallAsElement(model, writer);

        writer.writeEndElement();
    }

    private void writeAsynchHandler(final XMLExtendedStreamWriter writer, final ModelNode model, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.ASYNC_HANDLER.getLocalName());
        writer.writeAttribute(HANDLER_NAME.getXmlName(), name);
        ENABLED.marshallAsAttribute(model, false, writer);
        LEVEL.marshallAsElement(model, writer);
        AbstractHandlerDefinition.FILTER_SPEC.marshallAsElement(model, writer);
        FORMATTER.marshallAsElement(model, writer);
        QUEUE_LENGTH.marshallAsElement(model, writer);
        OVERFLOW_ACTION.marshallAsElement(model, writer);
        SUBHANDLERS.marshallAsElement(model, writer);

        writer.writeEndElement();
    }

    private void writeLogger(final XMLExtendedStreamWriter writer, String name, final ModelNode model) throws XMLStreamException {
        writer.writeStartElement(Element.LOGGER.getLocalName());
        writer.writeAttribute(CATEGORY.getXmlName(), name);
        USE_PARENT_HANDLERS.marshallAsAttribute(model, writer);
        writeCommonLogger(writer, model);
        writer.writeEndElement();
    }

    private void writeRootLogger(final XMLExtendedStreamWriter writer, final ModelNode model) throws XMLStreamException {
        writer.writeStartElement(Element.ROOT_LOGGER.getLocalName());
        writeCommonLogger(writer, model);
        writer.writeEndElement();
    }

    private void writeFormatters(final XMLExtendedStreamWriter writer, final String resourceName,
                                 final AttributeDefinition attribute, final ModelNode model) throws XMLStreamException {
        if (model.hasDefined(resourceName)) {
            for (String name : model.get(resourceName).keys()) {
                writer.writeStartElement(Element.FORMATTER.getLocalName());
                writer.writeAttribute(NAME.getXmlName(), name);
                final ModelNode value = model.get(resourceName, name);
                attribute.marshallAsElement(value, writer);
                writer.writeEndElement();
            }
        }
    }

    private void writeStructuredFormatters(final XMLExtendedStreamWriter writer, final String elementName, final ModelNode model,
                                           final SimpleAttributeDefinition... additionalAttributes) throws XMLStreamException {
        if (model.hasDefined(elementName)) {
            for (String name : model.get(elementName).keys()) {
                writer.writeStartElement(Element.FORMATTER.getLocalName());
                writer.writeAttribute(NAME.getXmlName(), name);
                final ModelNode value = model.get(elementName, name);
                writer.writeStartElement(elementName);
                // Write attributes first
                StructuredFormatterResourceDefinition.DATE_FORMAT.marshallAsAttribute(value, writer);
                StructuredFormatterResourceDefinition.PRETTY_PRINT.marshallAsAttribute(value, writer);
                StructuredFormatterResourceDefinition.PRINT_DETAILS.marshallAsAttribute(value, writer);
                StructuredFormatterResourceDefinition.ZONE_ID.marshallAsAttribute(value, writer);
                for (SimpleAttributeDefinition ad : additionalAttributes) {
                    ad.marshallAsAttribute(value, writer);
                }
                // Next write elements
                StructuredFormatterResourceDefinition.EXCEPTION_OUTPUT_TYPE.marshallAsElement(value, writer);
                StructuredFormatterResourceDefinition.RECORD_DELIMITER.marshallAsElement(value, writer);
                StructuredFormatterResourceDefinition.KEY_OVERRIDES.marshallAsElement(value, writer);
                StructuredFormatterResourceDefinition.META_DATA.marshallAsElement(value, writer);

                writer.writeEndElement(); // end elementName
                writer.writeEndElement(); // end formatter
            }
        }
    }

    private void writeFilterElement(final XMLExtendedStreamWriter writer, final ModelNode model, final String name) throws XMLStreamException {
        writer.writeStartElement(Element.FILTER.getLocalName());
        writer.writeAttribute(NAME.getXmlName(), name);
        CLASS.marshallAsAttribute(model, writer);
        MODULE.marshallAsAttribute(model, writer);
        FilterResourceDefinition.CONSTRUCTOR_PROPERTIES.marshallAsElement(model, writer);
        PROPERTIES.marshallAsElement(model, writer);
        writer.writeEndElement();
    }
}
