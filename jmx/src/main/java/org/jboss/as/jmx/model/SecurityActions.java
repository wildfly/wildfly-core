/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

import org.wildfly.security.manager.action.GetClassLoaderAction;
import org.wildfly.security.manager.WildFlySecurityManager;

import static java.security.AccessController.doPrivileged;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class SecurityActions {
    static ClassLoader getClassLoader(final Class<?> clazz) {
        return ! WildFlySecurityManager.isChecking() ? clazz.getClassLoader() : doPrivileged(new GetClassLoaderAction(clazz));
    }
}
