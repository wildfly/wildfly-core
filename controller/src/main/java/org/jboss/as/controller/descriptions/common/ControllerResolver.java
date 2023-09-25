/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.descriptions.common;

import org.jboss.as.controller.descriptions.DeprecatedResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public final class ControllerResolver {
    public static final String RESOURCE_NAME = ControllerResolver.class.getPackage().getName() + ".LocalDescriptions";


    public static ResourceDescriptionResolver getResolver(final String... keyPrefix) {
        return getResolver(false, keyPrefix);
    }
    @SuppressWarnings("deprecation")
    public static ResourceDescriptionResolver getDeprecatedResolver(final String deprecatedParent, final String... keyPrefix) {
        String prefix = getPrefix(keyPrefix);
        //noinspection deprecation
        return new DeprecatedResourceDescriptionResolver(deprecatedParent, prefix, RESOURCE_NAME, ControllerResolver.class.getClassLoader(), true, false);
    }

    public static ResourceDescriptionResolver getResolver(boolean useUnprefixedChildTypes, final String... keyPrefix) {
        String prefix = getPrefix(keyPrefix);
        return new StandardResourceDescriptionResolver(prefix, RESOURCE_NAME, ControllerResolver.class.getClassLoader(), true, useUnprefixedChildTypes);
    }

    private static String getPrefix(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder();
        for (String kp : keyPrefix) {
            if (prefix.length() > 0) {
                prefix.append('.').append(kp);
            } else {
                prefix.append(kp);
            }
        }
        return prefix.toString();
    }
}
