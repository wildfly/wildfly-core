/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.function.Supplier;

import org.jboss.as.controller.PathElement;

/**
 * Provides a {@link PathElement}.
 * @author Paul Ferraro
 * @deprecated To be removed without replacement
 */
@Deprecated(forRemoval = true, since = "26.0.0")
public interface PathElementProvider extends Supplier<PathElement> {

    /**
     * Convenience method returning the key of the provided path.
     * @return the path key
     */
    default String getKey() {
        return this.get().getKey();
    }

    /**
     * Convenience method returning the value of the provided path.
     * @return the path value
     */
    default String getValue() {
        return this.get().getValue();
    }

    /**
     * Convenience method indicating whether or not the provided path uses a {@value PathElement#WILDCARD_VALUE} value.
     * @return true, if the provided path uses a {@value PathElement#WILDCARD_VALUE} value, false otherwise.
     */
    default boolean isWildcard() {
        return this.get().isWildcard();
    }
}
