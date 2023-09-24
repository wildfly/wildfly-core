/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum representing the namespaces defined for the security manager subsystem.
 *
 * @author <a href="sguilhen@jboss.com">Stefan Guilhen</a>
 */
enum Namespace {

    UNKNOWN(null),

    SECURITY_MANAGER_1_0("urn:jboss:domain:security-manager:1.0");

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
            if (name != null) { map.put(name, namespace); }
        }
        MAP = map;
    }

    public static Namespace forUri(String uri) {
        final Namespace element = MAP.get(uri);
        return element == null ? UNKNOWN : element;
    }

}
