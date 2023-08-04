/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.List;
import java.util.function.Function;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.version.FeatureStream;
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
        return createURN(identifiers, FeatureStream.FEATURE_DEFAULT, version);
    }

    /**
     * Equivalent to {@link #createURN(List, Comparable, Function)} using {@link Object#toString()}.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param stream the target feature stream
     * @param version a version
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends Versioned<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, FeatureStream stream, V version) {
        return createURN(identifiers, stream, version, Object::toString);
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
        return createURN(identifiers, FeatureStream.FEATURE_DEFAULT, version, versionFormatter);
    }

    /**
     * Creates a URN using the specified identifiers, version, and version formatter.
     * @param <V> the version type
     * @param <N> the namespace type
     * @param identifiers a list of namespace identifiers
     * @param stream the target feature stream
     * @param version a version
     * @param versionFormatter a version formatter
     * @return a versioned URN
     */
    static <V extends Comparable<V>, N extends Versioned<V, N>> VersionedNamespace<V, N> createURN(List<String> identifiers, FeatureStream stream, V version, Function<V, String> versionFormatter) {
        return new SimpleVersionedNamespace<>(String.join(":", new CompositeIterable<>(List.of("urn"), identifiers, (stream != FeatureStream.FEATURE_DEFAULT) ? List.of(stream.toString(), versionFormatter.apply(version)) : List.of(versionFormatter.apply(version)))), version, stream);
    }
}
