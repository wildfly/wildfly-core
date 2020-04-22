/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.logging.filters.FilterResourceDefinition;
import org.jboss.as.logging.formatters.CustomFormatterResourceDefinition;
import org.jboss.as.logging.formatters.JsonFormatterResourceDefinition;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
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

/**
 *
 */
enum Element {

    UNKNOWN((String) null),

    ACCEPT(CommonAttributes.ACCEPT),
    ADD_LOGGING_API_DEPENDENCIES(LoggingResourceDefinition.ADD_LOGGING_API_DEPENDENCIES),
    ALL(CommonAttributes.ALL),
    ANY(CommonAttributes.ANY),
    APP_NAME(SyslogHandlerResourceDefinition.APP_NAME),
    APPEND(CommonAttributes.APPEND),
    ASYNC_HANDLER(AsyncHandlerResourceDefinition.NAME),
    CHANGE_LEVEL(CommonAttributes.CHANGE_LEVEL),
    CONSOLE_HANDLER(ConsoleHandlerResourceDefinition.NAME),
    CONSTRUCTOR_PROPERTIES(FilterResourceDefinition.CONSTRUCTOR_PROPERTIES),
    CUSTOM_FORMATTER(CustomFormatterResourceDefinition.CUSTOM_FORMATTER),
    CUSTOM_HANDLER(CustomHandlerResourceDefinition.NAME),
    DENY(CommonAttributes.DENY),
    ENCODING(CommonAttributes.ENCODING),
    FACILITY(SyslogHandlerResourceDefinition.FACILITY),
    FILE(CommonAttributes.FILE),
    FILE_HANDLER(FileHandlerResourceDefinition.NAME),
    FILTER(CommonAttributes.FILTER),
    FILTER_SPEC("filter-spec"),
    FORMATTER(AbstractHandlerDefinition.FORMATTER),
    HANDLERS(LoggerAttributes.HANDLERS),
    HOSTNAME(SyslogHandlerResourceDefinition.HOSTNAME),
    JSON_FORMATTER(JsonFormatterResourceDefinition.NAME),
    LEVEL(CommonAttributes.LEVEL),
    LEVEL_RANGE(CommonAttributes.LEVEL_RANGE_LEGACY),
    LOGGER(LoggerResourceDefinition.NAME),
    LOGGING_PROFILE(CommonAttributes.LOGGING_PROFILE),
    LOGGING_PROFILES(CommonAttributes.LOGGING_PROFILES),
    MATCH(CommonAttributes.MATCH),
    MAX_BACKUP_INDEX(SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX),
    NAMED_FORMATTER(AbstractHandlerDefinition.NAMED_FORMATTER),
    NOT(CommonAttributes.NOT),
    OVERFLOW_ACTION(AsyncHandlerResourceDefinition.OVERFLOW_ACTION),
    PATTERN_FORMATTER(PatternFormatterResourceDefinition.PATTERN_FORMATTER),
    PERIODIC_ROTATING_FILE_HANDLER(PeriodicHandlerResourceDefinition.NAME),
    PERIODIC_SIZE_ROTATING_FILE_HANDLER(PeriodicSizeRotatingHandlerResourceDefinition.NAME),
    PORT(SyslogHandlerResourceDefinition.PORT),
    PROPERTIES(CommonAttributes.PROPERTIES),
    PROPERTY("property"),
    PROTOCOL(SocketHandlerResourceDefinition.PROTOCOL),
    QUEUE_LENGTH(AsyncHandlerResourceDefinition.QUEUE_LENGTH),
    REPLACE(CommonAttributes.REPLACE),
    ROOT_LOGGER(RootLoggerResourceDefinition.NAME),
    ROTATE_SIZE(SizeRotatingHandlerResourceDefinition.ROTATE_SIZE),
    SERVER_ADDRESS(SyslogHandlerResourceDefinition.SERVER_ADDRESS),
    SIZE_ROTATING_FILE_HANDLER(SizeRotatingHandlerResourceDefinition.NAME),
    SUBHANDLERS(AsyncHandlerResourceDefinition.SUBHANDLERS),
    SUFFIX(PeriodicHandlerResourceDefinition.SUFFIX),
    SOCKET_HANDLER(SocketHandlerResourceDefinition.NAME),
    SYSLOG_FORMATTER(SyslogHandlerResourceDefinition.SYSLOG_FORMATTER),
    SYSLOG_HANDLER(SyslogHandlerResourceDefinition.NAME),
    TARGET(ConsoleHandlerResourceDefinition.TARGET),
    USE_DEPLOYMENT_LOGGING_CONFIG(LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG),
    XML_FORMATTER(XmlFormatterResourceDefinition.NAME),;

    private final String name;
    private final AttributeDefinition definition;

    Element(final String name) {
        this.name = name;
        this.definition = null;
    }

    Element(final AttributeDefinition definition) {
        this.name = definition.getXmlName();
        this.definition = definition;
    }

    /**
     * Get the local name of this element.
     *
     * @return the local name
     */
    public String getLocalName() {
        return name;
    }

    public AttributeDefinition getDefinition() {
        return definition;
    }

    private static final Map<String, Element> MAP;

    static {
        final Map<String, Element> map = new HashMap<>();
        for (Element element : values()) {
            final String name = element.getLocalName();
            if (name != null) map.put(name, element);
        }
        MAP = map;
    }

    public static Element forName(String localName) {
        final Element element = MAP.get(localName);
        return element == null ? UNKNOWN : element;
    }
}
