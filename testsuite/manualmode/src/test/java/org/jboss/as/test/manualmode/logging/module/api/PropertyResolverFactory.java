/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module.api;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PropertyResolverFactory {

    private static final PrivilegedAction<PropertyResolver> ACTION = () -> {
        final ServiceLoader<PropertyResolver> loader = ServiceLoader.load(PropertyResolver.class);
        final Iterator<PropertyResolver> iter = loader.iterator();
        return iter.hasNext() ? iter.next() : null;
    };

    public static PropertyResolver newResolver() {
        if (System.getSecurityManager() == null) {
            return ACTION.run();
        }
        return AccessController.doPrivileged(ACTION);
    }
}
