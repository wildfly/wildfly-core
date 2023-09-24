/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jmx;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Emanuel Muckenhuber
 */
enum Namespace {
    // must be first
    UNKNOWN(null),
    JMX_1_0("urn:jboss:domain:jmx:1.0"),
    JMX_1_1("urn:jboss:domain:jmx:1.1"),
    JMX_1_2("urn:jboss:domain:jmx:1.2"),
    JMX_1_3("urn:jboss:domain:jmx:1.3")
    ;

    /**
     * The current namespace version.
     */
    public static final Namespace CURRENT = JMX_1_3;

    private final String name;

    Namespace(final String name) {
        this.name = name;
    }

    /**
     * Get the URI of this namespace.
     *
     * @return the URI
     */
    public String getUriString() {
        return name;
    }

    private static final Map<String, Namespace> MAP;

    static {
        final Map<String, Namespace> map = new HashMap<String, Namespace>();
        for (Namespace namespace : values()) {
            final String name = namespace.getUriString();
            if (name != null) map.put(name, namespace);
        }
        MAP = map;
    }

    public static Namespace forUri(String uri) {
        final Namespace element = MAP.get(uri);
        return element == null ? UNKNOWN : element;
    }
}
