/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum Attribute {
    UNKNOWN(null),
    /* Remoting 1.0 attributes, in alpha order */
    AUTHENTICATION_CONTEXT(CommonAttributes.AUTHENTICATION_CONTEXT),
    CONNECTOR_REF(CommonAttributes.CONNECTOR_REF),
    NAME(CommonAttributes.NAME),
    OUTBOUND_SOCKET_BINDING_REF(CommonAttributes.OUTBOUND_SOCKET_BINDING_REF),
    PROTOCOL(CommonAttributes.PROTOCOL),
    SASL_AUTHENTICATION_FACTORY(CommonAttributes.SASL_AUTHENTICATION_FACTORY),
    SASL_PROTOCOL(CommonAttributes.SASL_PROTOCOL),
    SECURITY_REALM(CommonAttributes.SECURITY_REALM),
    SERVER_NAME(CommonAttributes.SERVER_NAME),
    SOCKET_BINDING(CommonAttributes.SOCKET_BINDING),
    SSL_CONTEXT(CommonAttributes.SSL_CONTEXT),
    URI(CommonAttributes.URI),
    USERNAME(CommonAttributes.USERNAME),
    VALUE(CommonAttributes.VALUE),
    WORKER_READ_THREADS("read-threads"),
    WORKER_TASK_CORE_THREADS("task-core-threads"),
    WORKER_TASK_KEEPALIVE("task-keepalive"),
    WORKER_TASK_LIMIT("task-limit"),
    WORKER_TASK_MAX_THREADS("task-max-threads"),
    WORKER_WRITE_THREADS("write-threads")
    ;

    private final String name;

    Attribute(final String name) {
        this.name = name;
    }

    /**
     * Get the local name of this element.
     *
     * @return the local name
     */
    public String getLocalName() {
        return name;
    }

    private static final Map<String, Attribute> MAP;

    static {
        final Map<String, Attribute> map = new HashMap<String, Attribute>();
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

    public String toString() {
        return getLocalName();
    }
}
