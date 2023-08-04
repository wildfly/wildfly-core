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
        return VersionedNamespace.createURN(identifiers, version, MAJOR_MINOR);
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
