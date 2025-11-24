/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management._private;

import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public final class DomainManagementResolver {
    public static final String RESOURCE_NAME = DomainManagementResolver.class.getPackage().getName() + ".LocalDescriptions";


    public static ResourceDescriptionResolver getResolver(final String... keyPrefix) {
        return getResolver(false, keyPrefix);
    }

    public static ResourceDescriptionResolver getResolver(boolean useUnprefixedChildTypes, final String... keyPrefix) {
        String prefix = getPrefix(keyPrefix);
        return new StandardResourceDescriptionResolver(prefix, RESOURCE_NAME, DomainManagementResolver.class.getClassLoader(), true, useUnprefixedChildTypes);
    }

    private static String getPrefix(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder();
        for (String kp : keyPrefix) {
            if (!prefix.isEmpty()) {
                prefix.append('.').append(kp);
            } else {
                prefix.append(kp);
            }
        }
        return prefix.toString();
    }
}
