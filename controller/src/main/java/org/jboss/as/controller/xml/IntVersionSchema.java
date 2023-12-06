/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.List;
import java.util.function.Function;

import org.jboss.as.version.Stability;
import org.jboss.staxmapper.IntVersion;

/**
 * A schema versioned via an {@link IntVersion}.
 * @author Paul Ferraro
 */
public interface IntVersionSchema<S extends IntVersionSchema<S>> extends VersionedSchema<IntVersion, S> {

    String JBOSS_IDENTIFIER = "jboss";
    String WILDFLY_IDENTIFIER = "wildfly";

    Function<IntVersion, String> MAJOR = new IntVersionFormatter(1);
    Function<IntVersion, String> MAJOR_MINOR = new IntVersionFormatter(2);
    Function<IntVersion, String> MAJOR_MINOR_MICRO = new IntVersionFormatter(3);

    /**
     * Convenience method that generates a URN for this schema using the specified namespace identifiers and version, formatted as "{@link IntVersion#major() major}.{@link IntVersion#minor() minor}".
     * @param <S> the schema type
     * @param identifiers a list of namespace identifiers
     * @param version a schema version
     * @return a URN
     */
    static <S extends IntVersionSchema<S>> VersionedNamespace<IntVersion, S> createURN(List<String> identifiers, IntVersion version) {
        return createURN(identifiers, Stability.DEFAULT, version);
    }

    /**
     * Convenience method that generates a URN for this schema using the specified namespace identifiers, stability, and version, formatted as "{@link IntVersion#major() major}.{@link IntVersion#minor() minor}".
     * @param <S> the schema type
     * @param identifiers a list of namespace identifiers
     * @param stability the stability of this schema version variant
     * @param version a schema version
     * @return a URN
     */
    static <S extends IntVersionSchema<S>> VersionedNamespace<IntVersion, S> createURN(List<String> identifiers, Stability stability, IntVersion version) {
        return VersionedNamespace.createURN(identifiers, stability, version, MAJOR_MINOR);
    }

    static class IntVersionFormatter implements Function<IntVersion, String> {
        private final int segments;

        IntVersionFormatter(int segments) {
            this.segments = segments;
        }

        @Override
        public String apply(IntVersion version) {
            return version.toString(this.segments);
        }
    }
}
