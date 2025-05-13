/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.util.HashMap;
import java.util.Map;

/**
 * The namespaces supported by the threads extension.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum Namespace {
    // must be first
    UNKNOWN(null),

    THREADS_1_0("urn:jboss:domain:threads:1.0"),
    THREADS_1_1("urn:jboss:domain:threads:1.1"),
    THREADS_2_0("urn:jboss:domain:threads:2.0"),
    ;

    /**
     * The current namespace version.
     */
    public static final Namespace CURRENT = THREADS_2_0;

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
        final Map<String, Namespace> map = new HashMap<>();
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
