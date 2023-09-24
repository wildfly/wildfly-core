/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.impl;

import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;

/**
 * An enumeration of the supported namespaces for CLI configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum Namespace {

    // must be first
    UNKNOWN(null), NONE(null),

    // predefined standard
    XML_SCHEMA_INSTANCE("http://www.w3.org/2001/XMLSchema-instance"),

    CLI_1_0("urn:jboss:cli:1.0"),

    CLI_1_1("urn:jboss:cli:1.1"),

    CLI_1_2("urn:jboss:cli:1.2"),

    CLI_1_3("urn:jboss:cli:1.3"),

    CLI_2_0("urn:jboss:cli:2.0"),

    CLI_3_0("urn:jboss:cli:3.0"),

    CLI_3_1("urn:jboss:cli:3.1"),

    CLI_3_2("urn:jboss:cli:3.2"),

    CLI_3_3("urn:jboss:cli:3.3"),

    CLI_3_4("urn:jboss:cli:3.4"),

    CLI_4_0("urn:jboss:cli:4.0");

    /**
     * The current namespace version.
     */
    public static final Namespace CURRENT = CLI_4_0;

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
            if (name != null)
                map.put(name, namespace);
        }
        MAP = map;
    }

    public static Namespace forUri(String uri) {
        // FIXME when STXM-8 is done, remove the null check
        if (uri == null || XMLConstants.NULL_NS_URI.equals(uri))
            return NONE;
        final Namespace element = MAP.get(uri);
        return element == null ? UNKNOWN : element;
    }

    public static Namespace[] cliValues() {
        Namespace[] temp = values();
        // The 3 is for the 3 namespaces excluded below.
        Namespace[] response = new Namespace[temp.length - 3];
        int nextPos = 0;
        for (Namespace current : temp) {
            if (current != UNKNOWN && current != NONE && current != XML_SCHEMA_INSTANCE) {
                response[nextPos++] = current;
            }
        }

        return response;
    }

}
