/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.resources;

import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;

/**
 * Model description for the domain root.
 *
 * @author Brian Stansberry
 */
public class DomainResolver {

    private static final String RESOURCE_NAME = DomainResolver.class.getPackage().getName() + ".LocalDescriptions";

    public static ResourceDescriptionResolver getResolver(final String keyPrefix) {
        return getResolver(keyPrefix, true);
    }

    public static ResourceDescriptionResolver getResolver(final String keyPrefix, final boolean useUnprefixedChildTypes) {
        return new StandardResourceDescriptionResolver(keyPrefix, RESOURCE_NAME, DomainResolver.class.getClassLoader(), true, useUnprefixedChildTypes);
    }

}
