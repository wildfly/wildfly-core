/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.descriptions;

import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;

/**
 * Model descriptions for deployment resources.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public final class ServerDescriptions {

    public static final String RESOURCE_NAME = ServerDescriptions.class.getPackage().getName() + ".LocalDescriptions";

    public static ResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefix) {
        String prefix = getDotSeparatedPrefix(keyPrefix);
        return new StandardResourceDescriptionResolver(prefix, RESOURCE_NAME, ServerDescriptions.class.getClassLoader(), true, true);
    }

    private static String getDotSeparatedPrefix(final String... keyPrefix) {
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

    public static ResourceDescriptionResolver getResourceDescriptionResolver(final String keyPrefix, final boolean useUnprefixedChildTypes) {
        return new StandardResourceDescriptionResolver(keyPrefix, RESOURCE_NAME, ServerDescriptions.class.getClassLoader(), true, useUnprefixedChildTypes);
    }

    private ServerDescriptions() {
    }
}
