/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author Alexey Loubyansky
 */
public class ContentTaskDefinitions {

    private final Map<Location, PatchingTasks.ContentTaskDefinition> definitions = new LinkedHashMap<Location, PatchingTasks.ContentTaskDefinition>();

    Collection<PatchingTasks.ContentTaskDefinition> getTaskDefinitions() {
        return definitions.values();
    }

    public PatchingTasks.ContentTaskDefinition get(Location location) {
        return definitions.get(location);
    }

    void put(Location location, PatchingTasks.ContentTaskDefinition definition) {
        definitions.put(location, definition);
    }

    public int size() {
        return definitions.size();
    }
}