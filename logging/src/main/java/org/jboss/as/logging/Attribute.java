/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.handlers.AsyncHandlerResourceDefinition;
import org.jboss.as.logging.handlers.ConsoleHandlerResourceDefinition;
import org.jboss.as.logging.handlers.PeriodicHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SocketHandlerResourceDefinition;
import org.jboss.as.logging.loggers.LoggerResourceDefinition;

/**
 *
 */
public enum Attribute {

    UNKNOWN((String) null),

    APPEND(CommonAttributes.APPEND),
    AUTOFLUSH(CommonAttributes.AUTOFLUSH),
    BLOCK_ON_RECONNECT(SocketHandlerResourceDefinition.BLOCK_ON_RECONNECT),
    CATEGORY(LoggerResourceDefinition.CATEGORY),
    CLASS(CommonAttributes.CLASS),
    COLOR_MAP(PatternFormatterResourceDefinition.COLOR_MAP),
    ENABLED(CommonAttributes.ENABLED),
    MIN_INCLUSIVE(CommonAttributes.MIN_INCLUSIVE),
    MIN_LEVEL(CommonAttributes.MIN_LEVEL),
    MAX_BACKUP_INDEX(SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX),
    MAX_INCLUSIVE(CommonAttributes.MAX_INCLUSIVE),
    MAX_LEVEL(CommonAttributes.MAX_LEVEL),
    MODULE(CommonAttributes.MODULE),
    NAME("name"),
    NEW_LEVEL(CommonAttributes.NEW_LEVEL),
    OUTBOUND_SOCKET_BINDING_REF(SocketHandlerResourceDefinition.OUTBOUND_SOCKET_BINDING_REF),
    OVERFLOW_ACTION(AsyncHandlerResourceDefinition.OVERFLOW_ACTION),
    PATH(PathResourceDefinition.PATH),
    PATTERN(PatternFormatterResourceDefinition.PATTERN),
    QUEUE_LENGTH(AsyncHandlerResourceDefinition.QUEUE_LENGTH),
    RELATIVE_TO(PathResourceDefinition.RELATIVE_TO),
    REPLACEMENT(CommonAttributes.REPLACEMENT),
    REPLACE_ALL(CommonAttributes.REPLACE_ALL),
    ROTATE_ON_BOOT(SizeRotatingHandlerResourceDefinition.ROTATE_ON_BOOT),
    ROTATE_SIZE(SizeRotatingHandlerResourceDefinition.ROTATE_SIZE),
    SSL_CONTEXT(SocketHandlerResourceDefinition.SSL_CONTEXT),
    SUFFIX(PeriodicHandlerResourceDefinition.SUFFIX),
    SYSLOG_TYPE("syslog-type"),
    TARGET(ConsoleHandlerResourceDefinition.TARGET),
    USE_PARENT_HANDLERS(LoggerResourceDefinition.USE_PARENT_HANDLERS),
    VALUE("value"),;

    private final String name;
    private final AttributeDefinition definition;

    Attribute(final AttributeDefinition definition) {
        if (definition == null) {
            this.name = null;
        } else {
            this.name = definition.getXmlName();
        }
        this.definition = definition;
    }

    Attribute(final String name) {
        this.name = name;
        this.definition = null;
    }

    /**
     * Get the local name of this attribute.
     *
     * @return the local name
     */
    public String getLocalName() {
        return name;
    }

    public AttributeDefinition getDefinition() {
        return definition;
    }

    private static final Map<String, Attribute> MAP;

    static {
        final Map<String, Attribute> map = new HashMap<>();
        for (Attribute element : values()) {
            final String name = element.getLocalName();
            if (name != null) map.put(name, element);
        }
        MAP = map;
    }

    public static Attribute forName(String localName) {
        final Attribute element = MAP.get(localName);
        return element == null ? UNKNOWN : element;
    }

    public static Map<String, Attribute> getMap() {
        return MAP;
    }
}
