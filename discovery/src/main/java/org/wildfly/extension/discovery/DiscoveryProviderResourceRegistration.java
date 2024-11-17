/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;

/**
 * Enumeration of a discovery provider resource registrations.
 */
public enum DiscoveryProviderResourceRegistration implements ResourceRegistration {
    AGGREGATE("aggregate-provider"),
    STATIC("static-provider"),
    ;
    private final PathElement path;

    DiscoveryProviderResourceRegistration(String name) {
        this.path = PathElement.pathElement(name);
    }

    @Override
    public PathElement getPathElement() {
        return this.path;
    }
}
