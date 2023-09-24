/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;

/**
 * An enumeration of the supported domain model namespaces.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum Namespace {

    // must be first
    UNKNOWN(null), NONE(null),

    // predefined standard
    XML_SCHEMA_INSTANCE("http://www.w3.org/2001/XMLSchema-instance"),

    // domain versions, in numerical order
    DOMAIN_1_0(1, "urn:jboss:domain:1.0"),

    DOMAIN_1_1(1, "urn:jboss:domain:1.1"),

    DOMAIN_1_2(1, "urn:jboss:domain:1.2"),

    DOMAIN_1_3(1, "urn:jboss:domain:1.3"),

    DOMAIN_1_4(1, "urn:jboss:domain:1.4"),

    DOMAIN_1_5(1, "urn:jboss:domain:1.5"),

    DOMAIN_1_6(1, "urn:jboss:domain:1.6"),

    DOMAIN_1_7(1, "urn:jboss:domain:1.7"),

    DOMAIN_1_8(1, "urn:jboss:domain:1.8"),

    DOMAIN_2_0(2, "urn:jboss:domain:2.0"),

    DOMAIN_2_1(2, "urn:jboss:domain:2.1"),

    DOMAIN_2_2(2, "urn:jboss:domain:2.2"),

    DOMAIN_3_0(3, "urn:jboss:domain:3.0"),

    DOMAIN_4_0(4, "urn:jboss:domain:4.0"),

    DOMAIN_4_1(4, "urn:jboss:domain:4.1"),

    DOMAIN_4_2(4, "urn:jboss:domain:4.2"),

    // WF 11, EAP 7.1
    DOMAIN_5_0(5, "urn:jboss:domain:5.0"),

    // WF 12
    DOMAIN_6_0(6, "urn:jboss:domain:6.0"),

    // WF 13
    DOMAIN_7_0(7, "urn:jboss:domain:7.0"),

    // WF 14
    DOMAIN_8_0(8, "urn:jboss:domain:8.0"),

    // WF 15
    DOMAIN_9_0(9, "urn:jboss:domain:9.0"),

    // WF 16 / WF 17 / WF 18
    DOMAIN_10_0(10, "urn:jboss:domain:10.0"),

    // EAP 7.3
    DOMAIN_11_0(11, "urn:jboss:domain:11.0"),

    // WF 19
    DOMAIN_12_0(12, "urn:jboss:domain:12.0"),

    // WF 20
    DOMAIN_13_0(13, "urn:jboss:domain:13.0"),

    // WF 21
    DOMAIN_14_0(14, "urn:jboss:domain:14.0"),

    // WF 22
    DOMAIN_15_0(15, "urn:jboss:domain:15.0"),

    // WF 23
    DOMAIN_16_0(16, "urn:jboss:domain:16.0"),

    // WF 24
    DOMAIN_17_0(17, "urn:jboss:domain:17.0"),

    // WF 25
    DOMAIN_18_0(18, "urn:jboss:domain:18.0"),

    // WF 26
    DOMAIN_19_0(19, "urn:jboss:domain:19.0"),

    DOMAIN_20_0(20, "urn:jboss:domain:20.0");

    /**
     * The current namespace version.
     */
    public static final Namespace CURRENT = DOMAIN_20_0;

    public static final Namespace[] ALL_NAMESPACES = domainValues();

    private final int majorVersion;
    private final String name;

    Namespace(final String name) {
        this(-1, name);
    }

    Namespace(final int majorVersion, final String name) {
        this.majorVersion = majorVersion;
        this.name = name;
    }

    /**
     * Get the major version represented by this namespace.
     *
     * @return The major version.
     */
    public int getMajorVersion() {
        return majorVersion;
    }

    /**
     * Get the URI of this namespace.
     *
     * @return the URI
     */
    public String getUriString() {
        return name;
    }

    /**
     * Set of all namespaces, excluding the special {@link #UNKNOWN} value.
     */
    public static final EnumSet<Namespace> STANDARD_NAMESPACES = EnumSet.complementOf(EnumSet.of(UNKNOWN, XML_SCHEMA_INSTANCE));

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

    public static Namespace[] domainValues() {
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
