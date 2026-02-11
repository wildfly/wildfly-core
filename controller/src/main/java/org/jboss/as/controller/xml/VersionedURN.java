/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.List;

import org.jboss.staxmapper.IntVersion;
import org.wildfly.common.iteration.CompositeIterable;

/**
 * A versioned namespace identified by a URN constructed using a version.
 * @author Paul Ferraro
 * @deprecated Use {@link IntVersionSchema#createURN(List, IntVersion)} instead.
 */
@Deprecated(forRemoval = true)
public class VersionedURN<N extends VersionedFeature<IntVersion, N>> extends SimpleVersionedNamespace<IntVersion, N> {
    public static final String JBOSS_IDENTIFIER = IntVersionSchema.JBOSS_IDENTIFIER;
    public static final String WILDFLY_IDENTIFIER = IntVersionSchema.WILDFLY_IDENTIFIER;

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
     * @param identifier a namespace identifier
     * @param version a version
     */
    public VersionedURN(String identifier, IntVersion version) {
        this(List.of(identifier), version);
    }

    /**
     * Constructs a versioned URN-based namespace using the specified URN components and version.
     * @param identifiers a list of URN components
     * @param version a version
     */
    public VersionedURN(List<String> identifiers, IntVersion version) {
        super(String.join(URN_DELIMITER, new CompositeIterable<>(List.of(URN), identifiers, List.of(version.toString(2)))), version);
    }
}
