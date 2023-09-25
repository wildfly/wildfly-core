/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum Attribute {
    UNKNOWN(null),
    /* Threads 1.0 attributes, in alpha order */
    ALLOW_CORE_TIMEOUT(CommonAttributes.ALLOW_CORE_TIMEOUT),
    BLOCKING(CommonAttributes.BLOCKING),
    CORE_THREADS(CommonAttributes.CORE_THREADS),
    COUNT(CommonAttributes.COUNT),
    GROUP_NAME(CommonAttributes.GROUP_NAME),
    MAX_THREADS(CommonAttributes.MAX_THREADS),
    NAME(CommonAttributes.NAME),
    PER_CPU(CommonAttributes.PER_CPU),
    PRIORITY(CommonAttributes.PRIORITY),
    QUEUE_LENGTH(CommonAttributes.QUEUE_LENGTH),
    THREAD_NAME_PATTERN(CommonAttributes.THREAD_NAME_PATTERN),
    TIME(CommonAttributes.TIME),
    UNIT(CommonAttributes.UNIT),
    VALUE(CommonAttributes.VALUE),
    ;
    private final String name;

    Attribute(final String name) {
        this.name = name;
    }

    /**
     * Get the local name of this attribute.
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
