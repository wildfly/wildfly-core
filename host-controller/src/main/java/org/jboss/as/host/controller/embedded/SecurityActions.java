/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.embedded;

import static java.lang.System.getProperty;
import static java.lang.System.getSecurityManager;
import static java.lang.System.setProperty;
import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;

/**
 * Privileged actions used by more than one class in this module.
 *
 * @author Brian Stansberry
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class SecurityActions {
    static String getPropertyPrivileged(final String name, final String def) {
        final SecurityManager sm = getSecurityManager();
        if (sm == null) {
            return getProperty(name, def);
        }
        return doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return getProperty(name, def);
            }
        });
    }

    static String setPropertyPrivileged(final String name, final String value) {
        final SecurityManager sm = getSecurityManager();
        if (sm == null) {
            return setProperty(name, value);
        } else {
            return doPrivileged(new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return setProperty(name, value);
                }
            });
        }
    }

    static ClassLoader getTccl() {
        if (getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        }
        return doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        });
    }

    static void setTccl(final ClassLoader cl) {
        if (getSecurityManager() == null) {
            Thread.currentThread().setContextClassLoader(cl);
        } else {
            doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    Thread.currentThread().setContextClassLoader(cl);
                    return null;
                }
            });
        }
    }
}
