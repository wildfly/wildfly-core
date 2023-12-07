/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.version.Stability;

/**
 * Describes the registration of a resource.
 */
public interface ResourceRegistration extends Feature {
    /**
     * Returns the path element under which this resource is registered with its parent resource, or {@code null} if this is the root resource.
     *
     * @return the path element, or {@code null} if this is the root resource.
     */
    PathElement getPathElement();

    /**
     * Creates a new root resource registration
     * @return a resource registration
     */
    static ResourceRegistration root() {
        return of(null);
    }

    /**
     * Creates a new resource registration for the specified path.
     * @param path a path element
     * @return a resource registration
     */
    static ResourceRegistration of(PathElement path) {
        return new ResourceRegistration() {
            @Override
            public PathElement getPathElement() {
                return path;
            }
        };
    }

    /**
     * Creates a new resource registration for the specified path and with the specified stability level.
     * @param path a path element
     * @param stability a stability level
     * @return a resource registration
     */
    static ResourceRegistration of(PathElement path, Stability stability) {
        return new ResourceRegistration() {
            @Override
            public PathElement getPathElement() {
                return path;
            }

            @Override
            public Stability getStability() {
                return stability;
            }
        };
    }
}
