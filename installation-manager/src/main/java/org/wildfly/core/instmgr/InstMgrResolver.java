/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;

/**
 * Management resolver for the installation manager management resources.
 */
class InstMgrResolver {

    static final ResourceDescriptionResolver RESOLVER = InstMgrResolver.getResourceDescriptionResolver();

    public static ResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefixes) {
        StringBuilder sb = new StringBuilder(InstMgrConstants.INSTALLATION_MANAGER);
        if (keyPrefixes != null) {
            for (String current : keyPrefixes) {
                sb.append(".").append(current);
            }
        }

        return new StandardResourceDescriptionResolver(sb.toString(),
                InstMgrResolver.class.getPackage().getName() + ".LocalDescriptions", InstMgrResolver.class.getClassLoader(),
                false, false);
    }

}
