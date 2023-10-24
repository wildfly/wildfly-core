/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.List;
import java.util.function.Function;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.version.Quality;
import org.jboss.staxmapper.Namespace;
import org.jboss.staxmapper.Versioned;
import org.wildfly.common.iteration.CompositeIterable;

/**
 * A versioned namespace.
 * @author Paul Ferraro
 */
public interface VersionedNamespace<V extends Comparable<V>, N extends Versioned<V, N>> extends Versioned<V, N>, Namespace, FeatureRegistry {

    /**
     * Equivalent to {@link #createURN(List, Comparable, Function)} using {@link Object#toString()}.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param version a version
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends Versioned<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, V version) {
        return createURN(identifiers, Quality.DEFAULT, version);
    }

    /**
     * Equivalent to {@link #createURN(List, Comparable, Function)} using {@link Object#toString()}.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param quality the quality of this namespace version variant
     * @param version a version
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends Versioned<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, Quality quality, V version) {
        return createURN(identifiers, quality, version, Object::toString);
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
        return createURN(identifiers, Quality.DEFAULT, version, versionFormatter);
    }

    /**
     * Creates a URN using the specified identifiers, version, and version formatter.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param quality the quality of this namespace version variant
     * @param version a version
     * @param versionFormatter a version formatter
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends Versioned<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, Quality quality, V version, Function<V, String> versionFormatter) {
        return new SimpleVersionedNamespace<>(String.join(":", new CompositeIterable<>(List.of("urn"), identifiers, (quality != Quality.DEFAULT) ? List.of(quality.toString(), versionFormatter.apply(version)) : List.of(versionFormatter.apply(version)))), version, quality);
    }
}
