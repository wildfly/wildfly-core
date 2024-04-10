/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.List;
import java.util.function.Function;

import org.jboss.as.version.Stability;
import org.jboss.staxmapper.Namespace;
import org.wildfly.common.iteration.CompositeIterable;

/**
 * A versioned namespace.
 * @author Paul Ferraro
 */
public interface VersionedNamespace<V extends Comparable<V>, N extends VersionedFeature<V, N>> extends VersionedFeature<V, N>, Namespace {

    /**
     * Equivalent to {@link #createURN(List, Comparable, Function)} using {@link Object#toString()}.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param version a version
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends VersionedFeature<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, V version) {
        return createURN(identifiers, Stability.DEFAULT, version);
    }

    /**
     * Equivalent to {@link #createURN(List, Stability, Comparable, Function)} using {@link Object#toString()}.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param stability the stability of this namespace version variant
     * @param version a version
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends VersionedFeature<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, Stability stability, V version) {
        return createURN(identifiers, stability, version, Object::toString);
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
    static <V extends Comparable<V>, N extends VersionedFeature<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, V version, Function<V, String> versionFormatter) {
        return createURN(identifiers, Stability.DEFAULT, version, versionFormatter);
    }

    /**
     * Creates a URN using the specified identifiers, stability, version, and version formatter.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param stability the stabilty of this namespace version variant
     * @param version a version
     * @param versionFormatter a version formatter
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends VersionedFeature<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, Stability stability, V version, Function<V, String> versionFormatter) {
        return new SimpleVersionedNamespace<>(String.join(":", new CompositeIterable<>(List.of("urn"), identifiers, !Stability.DEFAULT.enables(stability) ? List.of(stability.toString(), versionFormatter.apply(version)) : List.of(versionFormatter.apply(version)))), version, stability);
    }
}
