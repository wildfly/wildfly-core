/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import java.util.HashMap;
import java.util.Map;

/**
 * An enumeration of the supported Remoting subsystem namespaces.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum Namespace {
    // must be first
    UNKNOWN(null),

    REMOTING_1_0("urn:jboss:domain:remoting:1.0"),
    REMOTING_1_1("urn:jboss:domain:remoting:1.1"),
    REMOTING_1_2("urn:jboss:domain:remoting:1.2"),
    REMOTING_2_0("urn:jboss:domain:remoting:2.0"),
    REMOTING_3_0("urn:jboss:domain:remoting:3.0"),
    REMOTING_4_0("urn:jboss:domain:remoting:4.0"),
    REMOTING_5_0("urn:jboss:domain:remoting:5.0")
    ;

    /**
     * The current namespace version.
     */
    public static final Namespace CURRENT = REMOTING_5_0;

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
