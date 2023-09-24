/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import org.jboss.staxmapper.Versioned;

/**
 * A versioned schema, whose namespace is a versioned namespace.
 * @author Paul Ferraro
 */
public interface VersionedSchema<V extends Comparable<V>, S extends VersionedSchema<V, S>> extends Versioned<V, S>, Schema {

    /**
     * Returns the versioned namespace of this attribute/element.
     * @return the versioned namespace of this attribute/element.
     */
    @Override
    VersionedNamespace<V, S> getNamespace();

    @Override
    default V getVersion() {
        return this.getNamespace().getVersion();
    }
}
