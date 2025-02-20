/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.WildcardResourceRegistration;

/**
 * Enumeration of a discovery provider resource registrations.
 */
public enum DiscoveryProviderResourceRegistration implements WildcardResourceRegistration {
    AGGREGATE("aggregate-provider"),
    STATIC("static-provider"),
    ;
    private final String key;

    DiscoveryProviderResourceRegistration(String key) {
        this.key = key;
    }

    @Override
    public String getPathKey() {
        return this.key;
    }
}
