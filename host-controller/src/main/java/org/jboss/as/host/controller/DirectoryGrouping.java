/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jboss.dmr.ModelNode;

/**
 * The directory grouping types for the domains; {@code tmp}, {@code log} and {@code data} directories.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public enum DirectoryGrouping {


    BY_TYPE("by-type"),
    BY_SERVER("by-server");



    private static final Map<String, DirectoryGrouping> MAP;

    static {
        final Map<String, DirectoryGrouping> map = new HashMap<String, DirectoryGrouping>();
        for (DirectoryGrouping directoryGrouping : values()) {
            map.put(directoryGrouping.localName, directoryGrouping);
        }
        MAP = map;
    }

    public static DirectoryGrouping forName(String localName) {
        if (localName == null) {
            return null;
        }
        final DirectoryGrouping directoryGrouping = MAP.get(localName.toLowerCase());
        return directoryGrouping == null ? DirectoryGrouping.valueOf(localName.toUpperCase(Locale.ENGLISH)) : directoryGrouping;
    }

    private final String localName;

    DirectoryGrouping(final String localName) {
        this.localName = localName;
    }

    @Override
    public String toString() {
        return localName;
    }

    /**
     * Converts the value of the directory grouping to a model node.
     *
     * @return a new model node for the value.
     */
    public ModelNode toModelNode() {
        return new ModelNode().set(toString());
    }

    /**
     * Returns the default directory grouping.
     *
     * @return the default directory grouping.
     */
    public static DirectoryGrouping defaultValue() {
        return BY_SERVER;
    }
}
