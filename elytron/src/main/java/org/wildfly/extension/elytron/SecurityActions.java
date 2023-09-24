/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Wrapper around {@link AccessController#doPrivileged(PrivilegedAction)} for the 'org.wildfly.extension.elytron' package.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class SecurityActions {

    static <T> T doPrivileged(final PrivilegedAction<T> action) {
        return WildFlySecurityManager.isChecking() ? AccessController.doPrivileged(action) : action.run();
    }

    static <T> T doPrivileged(final PrivilegedExceptionAction<T> action) throws Exception {
        return WildFlySecurityManager.isChecking() ? AccessController.doPrivileged(action) : action.run();
    }

}
