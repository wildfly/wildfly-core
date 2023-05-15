/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import org.jboss.as.controller.PathElement;

/**
 * Provides a {@link PathElement}.
 * @author Paul Ferraro
 */
public interface PathElementProvider {

    /**
     * Returns the provided registration path.
     * @return a path element
     */
    PathElement getPathElement();

    /**
     * Convenience method returning the key of the provided path.
     * @return the path key
     */
    default String getKey() {
        return this.getPathElement().getKey();
    }

    /**
     * Convenience method returning the value of the provided path.
     * @return the path value
     */
    default String getValue() {
        return this.getPathElement().getValue();
    }

    /**
     * Convenience method indicating whether or not the provided path uses a {@value PathElement#WILDCARD_VALUE} value.
     * @return true, if the provided path uses a {@value PathElement#WILDCARD_VALUE} value, false otherwise.
     */
    default boolean isWildcard() {
        return this.getPathElement().isWildcard();
    }
}
