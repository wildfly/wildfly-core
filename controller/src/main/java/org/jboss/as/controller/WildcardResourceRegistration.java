/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.version.Stability;
import org.wildfly.common.Assert;

/**
 * A wildcard resource registration.
 * @author Paul Ferraro
 */
public interface WildcardResourceRegistration extends ResourceRegistration {

    /**
     * Returns the path key of this resource registration.
     * @return the path key of this resource registration.
     */
    String getPathKey();

    @Override
    default PathElement getPathElement() {
        return PathElement.pathElement(this.getPathKey());
    }

    default PathElement pathElement(String value) {
        return PathElement.pathElement(this.getPathKey(), value);
    }

    /**
     * Creates a new wildcard resource registration for the specified path.
     * @param path a path element
     * @return a resource registration
     */
    static WildcardResourceRegistration of(PathElement path) {
        return of(path, Stability.DEFAULT);
    }

    /**
     * Creates a new wildcard resource registration for the specified path and stability.
     * @param path a path element
     * @param stability a stability level
     * @return a resource registration
     */
    static WildcardResourceRegistration of(PathElement path, Stability stability) {
        return new DefaultWildcardResourceRegistration(path, stability);
    }

    class DefaultWildcardResourceRegistration extends DefaultResourceRegistration implements WildcardResourceRegistration {

        DefaultWildcardResourceRegistration(PathElement path, Stability stability) {
            super(path, stability);
            Assert.assertTrue(path.isWildcard());
        }

        @Override
        public String getPathKey() {
            return this.getPathElement().getKey();
        }
    }
}
