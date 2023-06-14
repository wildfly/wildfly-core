/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.controller.xml;

import java.util.List;
import java.util.function.Function;

import org.jboss.staxmapper.Namespace;
import org.jboss.staxmapper.Versioned;
import org.wildfly.common.iteration.CompositeIterable;

/**
 * A versioned namespace.
 * @author Paul Ferraro
 */
public interface VersionedNamespace<V extends Comparable<V>, N extends Versioned<V, N>> extends Versioned<V, N>, Namespace {

    /**
     * Equivalent to {@link #createURN(List, Comparable, Function)} using {@link Object#toString()}.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param version a version
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends Versioned<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, V version) {
        return createURN(identifiers, version, Object::toString);
    }

    /**
     * Creates a URN using the specified identifiers, version, and version formatter.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param version a version
     * @param versionFormatter a version formatter
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends Versioned<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, V version, Function<V, String> versionFormatter) {
        return new SimpleVersionedNamespace<>(String.join(":", new CompositeIterable<>(List.of("urn"), identifiers, List.of(versionFormatter.apply(version)))), version);
    }
}
