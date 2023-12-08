/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import org.jboss.as.controller.Feature;
import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.Versioned;

/**
 * A versioned schema, whose namespace is a versioned namespace.
 * @author Paul Ferraro
 */
public interface VersionedSchema<V extends Comparable<V>, S extends VersionedSchema<V, S>> extends Versioned<V, S>, Schema, Feature, FeatureRegistry {

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

    @Override
    default Stability getStability() {
        return this.getNamespace().getStability();
    }
}
