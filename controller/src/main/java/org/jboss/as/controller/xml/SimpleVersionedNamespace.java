/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import org.jboss.as.version.Stability;

/**
 * Simple {@link VersionedNamespace} implementation.
 * @author Paul Ferraro
 * @param <V> the namespace version
 * @param <N> the namespace type
 */
public class SimpleVersionedNamespace<V extends Comparable<V>, N extends VersionedFeature<V, N>> extends SimpleNamespace implements VersionedNamespace<V, N> {

    private final V version;
    private final Stability stability;

    public SimpleVersionedNamespace(String uri, V version) {
        this(uri, version, Stability.DEFAULT);
    }

    public SimpleVersionedNamespace(String uri, V version, Stability stabilty) {
        super(uri);
        this.version = version;
        this.stability = stabilty;
    }

    @Override
    public V getVersion() {
        return this.version;
    }

    @Override
    public Stability getStability() {
        return this.stability;
    }
}
