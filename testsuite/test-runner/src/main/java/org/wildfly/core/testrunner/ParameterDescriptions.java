/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.testrunner;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ParameterDescriptions {

    private final Map<FrameworkMethod, Queue<ParameterDescription>> parameters;

    public ParameterDescriptions() {
        parameters = new HashMap<>();
    }

    synchronized void add(final FrameworkMethod method, final FrameworkField field, final Object value) {
        final Queue<ParameterDescription> descriptions = parameters.computeIfAbsent(method, frameworkMethod -> new LinkedList<>());
        descriptions.add(new ParameterDescription(field, value));
    }

    synchronized ParameterDescription take(final FrameworkMethod method) {
        final Queue<ParameterDescription> descriptions = parameters.get(method);
        try {
            if (descriptions == null || descriptions.isEmpty()) {
                return null;
            }
            return descriptions.poll();
        } finally {
            if (descriptions != null && descriptions.isEmpty()) {
                parameters.remove(method);
            }
        }
    }

    synchronized boolean isEmpty() {
        return parameters.isEmpty();
    }

    synchronized void clear() {
        parameters.clear();
    }
}
