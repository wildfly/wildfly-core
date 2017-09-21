/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.msc.service.ServiceName;

/**
 * Provides a factory for creating {@link ServiceName} instances from dot-separated strings while trying to
 * mitigate the memory overhead of having multiple copies of elements of the name that have the same
 * {@link ServiceName#getSimpleName() simple name}.
 *
 * @author Brian Stansberry
 */
public final class ServiceNameFactory {

    private static final Map<ServiceName, ServiceName> cache = new ConcurrentHashMap<>();

    /**
     * Parses a string into a {@link ServiceName} using the same algorithm as {@link ServiceName#parse(String)}
     * but also attempts to ensure that once parsing occurs if any other name that is
     * {@link ServiceName#equals(ServiceName) equal to} the parsed name or one of its
     * {@link ServiceName#getParent() ancestors} has been parsed previously that that previously parsed name
     * is used.
     *
     * @param toParse the string form of a service name. Cannot be {@code null}
     * @return a {@code ServiceName} instance
     */
    public static ServiceName parseServiceName(String toParse) {
        ServiceName original = ServiceName.parse(toParse);

        // Try to use cached elements of the ServiceName chain

        // Cost of a duplicate ServiceName instance
        // 1) int for hashCode
        // 2) pointer to simple name
        // 3) pointer to canonical name
        // 4) pointer to parent
        // 5) the simple name (cost depends on string length but at least 2 pointers plus the char[] and the object)
        // 6) Possibly a long string for canonicalName

        // Cost of a ConcurrentHashMap Node where key == value
        // 1) int for hash code
        // 2) pointer to key
        // 3) pointer to value
        // 4) pointer to next
        // 5) ~ 1 pointer to the Node itself in the table (some table elements have > 1 Node but some are empty

        // Based on this, if there's roughly a > 50% chance of a name being duplicated, it's worthwhile
        // to intern it. As a heuristic for whether there is a > 50% chance, we'll intern all names
        // of 4 elements or less and for larger names, all but the last element

        int length = original.length();
        ServiceName[] ancestry = new ServiceName[length];
        ServiceName sn = original;
        for (int i = length - 1; i >= 0   ; i--) {
            ancestry[i] = sn;
            sn = sn.getParent();
        }

        int max = length > 4 ? length - 1 : length;
        for (int i = 0; i < max; i++) {
            ServiceName interned = cache.putIfAbsent(ancestry[i], ancestry[i]);
            if (interned != null && ancestry[i] != interned) {
                // Replace this one in the ancestry with the interned one
                ServiceName parent = ancestry[i] = interned;
                // Replace all descendants
                boolean checkCache = true;
                for (int j = i+1; j < length; j++) {
                    parent = parent.append(ancestry[j].getSimpleName());
                    if (checkCache && j < max) {
                        ServiceName cached = cache.get(parent);
                        if (cached != null) {
                            // Use what we already have
                            parent = cached;
                            // We don't need to recheck in the outer loop.
                            i = j;
                        } else {
                            // Assume we'll miss the rest of the way
                            checkCache = false;
                        }
                    }
                    ancestry[j] = parent;
                }
            }
        }
        return ancestry[length - 1];
    }

    static void clearCache() {
        cache.clear();
    }
}
