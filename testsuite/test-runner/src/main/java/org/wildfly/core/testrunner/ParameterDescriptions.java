/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
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
