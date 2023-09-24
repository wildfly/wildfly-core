/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public enum Namespace {
    // must be first
    UNKNOWN(null),

    LOGGING_1_0("urn:jboss:domain:logging:1.0"),

    LOGGING_1_1("urn:jboss:domain:logging:1.1"),

    LOGGING_1_2("urn:jboss:domain:logging:1.2"),

    LOGGING_1_3("urn:jboss:domain:logging:1.3"),

    LOGGING_1_4("urn:jboss:domain:logging:1.4"),

    LOGGING_1_5("urn:jboss:domain:logging:1.5"),

    LOGGING_2_0("urn:jboss:domain:logging:2.0"),

    LOGGING_3_0("urn:jboss:domain:logging:3.0"),

    LOGGING_4_0("urn:jboss:domain:logging:4.0"),

    LOGGING_5_0("urn:jboss:domain:logging:5.0"),

    LOGGING_6_0("urn:jboss:domain:logging:6.0"),

    LOGGING_7_0("urn:jboss:domain:logging:7.0"),

    LOGGING_8_0("urn:jboss:domain:logging:8.0"),
    ;

    /**
     * The current namespace version.
     */
    public static final Namespace CURRENT = LOGGING_8_0;

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
    private static final List<Namespace> READABLE;

    static {
        final Map<String, Namespace> map = new HashMap<String, Namespace>();
        final List<Namespace> readable = new ArrayList<Namespace>();
        for (Namespace namespace : values()) {
            final String name = namespace.getUriString();
            if (name != null) {
                map.put(name, namespace);
                readable.add(namespace);
            }
        }
        MAP = map;
        READABLE = Collections.unmodifiableList(readable);
    }

    public static List<Namespace> readable() {
        return READABLE;
    }

    public static Namespace forUri(String uri) {
        final Namespace element = MAP.get(uri);
        return element == null ? UNKNOWN : element;
    }
}
