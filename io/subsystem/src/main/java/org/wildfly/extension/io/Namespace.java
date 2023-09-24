/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.io;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
enum Namespace {

    // must be first
    UNKNOWN(null),

    IO_1_0("urn:jboss:domain:io:1.0"),
    IO_1_1("urn:jboss:domain:io:1.1"),
    IO_2_0("urn:jboss:domain:io:2.0"),
    IO_3_0("urn:jboss:domain:io:3.0");

    /**
     * The current namespace version.
     */
    public static final Namespace CURRENT = IO_3_0;

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
