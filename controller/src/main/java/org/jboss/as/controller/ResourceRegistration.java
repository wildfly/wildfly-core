/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Objects;

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
        return of(path, Stability.DEFAULT);
    }

    /**
     * Creates a new resource registration for the specified path and with the specified stability level.
     * @param path a path element
     * @param stability a stability level
     * @return a resource registration
     */
    static ResourceRegistration of(PathElement path, Stability stability) {
        return (path != null) || (stability != DefaultResourceRegistration.ROOT.getStability()) ? new DefaultResourceRegistration(path, stability) : DefaultResourceRegistration.ROOT;
    }

    class DefaultResourceRegistration implements ResourceRegistration {
        static final ResourceRegistration ROOT = new DefaultResourceRegistration(null, Stability.DEFAULT);
        private final PathElement path;
        private final Stability stability;

        DefaultResourceRegistration(PathElement path, Stability stability) {
            this.path = path;
            this.stability = stability;
        }

        @Override
        public PathElement getPathElement() {
            return this.path;
        }

        @Override
        public Stability getStability() {
            return this.stability;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.path);
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof ResourceRegistration)) return false;
            ResourceRegistration registration = (ResourceRegistration) object;
            return Objects.equals(this.getPathElement(), registration.getPathElement()) && Objects.equals(this.getStability(), registration.getStability());
        }

        @Override
        public String toString() {
            return Objects.toString(this.path);
        }
    }
}
