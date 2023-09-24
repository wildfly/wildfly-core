/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import java.util.HashMap;
import java.util.Map;

/**
 * @author John Bailey
 */
public enum Namespace {
UNKNOWN(null),

    DEPLOYMENT_SCANNER_1_0("urn:jboss:domain:deployment-scanner:1.0"),
    DEPLOYMENT_SCANNER_1_1("urn:jboss:domain:deployment-scanner:1.1"),
    DEPLOYMENT_SCANNER_2_0("urn:jboss:domain:deployment-scanner:2.0"),
    ;

    /**
     * The current namespace version.
     */
    public static final Namespace CURRENT = DEPLOYMENT_SCANNER_2_0;

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
