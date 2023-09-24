/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import org.jboss.staxmapper.Versioned;

/**
 * Simple {@link VersionedNamespace} implementation.
 * @author Paul Ferraro
 * @param <V> the namespace version
 * @param <N> the namespace type
 */
public class SimpleVersionedNamespace<V extends Comparable<V>, N extends Versioned<V, N>> extends SimpleNamespace implements VersionedNamespace<V, N> {

    private final V version;

    public SimpleVersionedNamespace(String uri, V version) {
        super(uri);
        this.version = version;
    }

    @Override
    public V getVersion() {
        return this.version;
    }
}
