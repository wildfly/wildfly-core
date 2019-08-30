/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.logging.filters;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.logging.CommonAttributes;

/**
 * <p>
 * This somewhat mimics the relationships normally managed by the capabilities API. A {@code filter-spec} attribute will
 * not quite work correctly with capabilities. The reason being is the {@code filter-spec} is a string attribute which
 * uses an expression format to create filters for handler and loggers. This means the {@code filter-spec} can have a
 * one to many relationship with custom filters.
 * </p>
 *
 * <p>
 * As an example; {@code any(myFilter1, myFilter2, myFilter3)}. All three of these are valid and could be registered
 * against a single {@code filter-spec} associated with either a logger or a handler.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class FilterReferenceRecorder {

    private final Map<String, Set<String>> filterRefs;

    /**
     * Creates a new reference recorder.
     */
    FilterReferenceRecorder() {
        filterRefs = new HashMap<>();
    }

    /**
     * Gets all the references of a filter associated with a logger or handler.
     *
     * @param filterAddress the filters address
     *
     * @return the logger and handler references or an empty set
     */
    Set<String> getFilterReferences(final PathAddress filterAddress) {
        final String key = formatName(filterAddress);
        final Set<String> refs = new LinkedHashSet<>();
        synchronized (filterRefs) {
            if (filterRefs.containsKey(key)) {
                refs.addAll(filterRefs.get(key));
            }
        }
        return refs;
    }

    /**
     * Registers a handler with the filter name.
     *
     * @param filterName       the name of the filter
     * @param referenceAddress the handlers address
     */
    void registerFilter(final String filterName, final PathAddress referenceAddress) {
        final String key = formatName(referenceAddress, filterName);
        synchronized (filterRefs) {
            final Set<String> filters = filterRefs.compute(key, (value, values) -> new LinkedHashSet<>());
            filters.add(referenceAddress.toCLIStyleString());
        }
    }

    /**
     * Unregisters the filter name with a handler.
     *
     * @param filterName       the name of the filter to unregister
     * @param referenceAddress the handlers address
     */
    @SuppressWarnings("DuplicatedCode")
    void unregisterFilter(final String filterName, final PathAddress referenceAddress) {
        final String key = formatName(referenceAddress, filterName);
        synchronized (filterRefs) {
            final Set<String> filters = filterRefs.get(key);
            if (filters != null) {
                filters.remove(referenceAddress.toCLIStyleString());
                if (filters.isEmpty()) {
                    filterRefs.remove(key);
                }
            }
        }
    }

    private static String formatName(final PathAddress address, final String dynamicName) {
        for (PathElement pathElement : address) {
            if (CommonAttributes.LOGGING_PROFILE.equals(pathElement.getKey())) {
                return pathElement.getValue() + "." + dynamicName;
            }
        }
        return dynamicName;
    }

    private static String formatName(final PathAddress address) {
        return formatName(address, address.getLastElement().getValue());
    }
}
