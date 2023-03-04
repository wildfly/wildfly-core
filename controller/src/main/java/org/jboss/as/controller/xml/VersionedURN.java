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

import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.Versioned;
import org.wildfly.common.iteration.CompositeIterable;

/**
 * A versioned namespace identified by a URN constructed using a version.
 * @author Paul Ferraro
 */
public class VersionedURN<N extends Versioned<IntVersion, N>> extends SimpleVersionedNamespace<IntVersion, N> {
    public static final String JBOSS_IDENTIFIER = "jboss";
    public static final String WILDFLY_IDENTIFIER = "wildfly";

    private static final String URN = "urn";
    private static final String URN_DELIMITER = ":";

    /**
     * Constructs a versioned URN-based namespace using the specified namespace identifier, namespace specific string, and version.
     * @param nid a namespace identifier
     * @param nss a namespace specific string
     * @param version a version
     */
    public VersionedURN(String nid, String nss, IntVersion version) {
        this(List.of(nid, nss), version);
    }

    /**
     * Constructs a versioned URN-based namespace using the specified namespace identifier and version.
     * @param nid a namespace identifier
     * @param version a version
     */
    public VersionedURN(String identifier, IntVersion version) {
        this(List.of(identifier), version);
    }

    /**
     * Constructs a versioned URN-based namespace using the specified URN components and version.
     * @param components a list of URN components
     * @param version a version
     */
    public VersionedURN(List<String> identifiers, IntVersion version) {
        super(String.join(URN_DELIMITER, new CompositeIterable<>(List.of(URN), identifiers, List.of(version.toString(2)))), version);
    }
}
