/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import java.security.AccessController;
import java.security.PrivilegedAction;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Security actions to perform possibly privileged operations.  No methods in
 * this class are to be made public under any circumstances!
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class SecurityActions {

    static ClassLoader setThreadContextClassLoader(Class cl) {
        if (! WildFlySecurityManager.isChecking()) {
            return SetThreadContextClassLoaderAction.NON_PRIVILEGED.setThreadContextClassLoader(cl);
        } else {
            return SetThreadContextClassLoaderAction.PRIVILEGED.setThreadContextClassLoader(cl);
        }
    }

    static void setThreadContextClassLoader(ClassLoader cl) {
        if (! WildFlySecurityManager.isChecking()) {
            SetThreadContextClassLoaderAction.NON_PRIVILEGED.setThreadContextClassLoader(cl);
        } else {
            SetThreadContextClassLoaderAction.PRIVILEGED.setThreadContextClassLoader(cl);
        }
    }

    private interface SetThreadContextClassLoaderAction {

        ClassLoader setThreadContextClassLoader(Class cl);

        void setThreadContextClassLoader(ClassLoader cl);

        SetThreadContextClassLoaderAction NON_PRIVILEGED = new SetThreadContextClassLoaderAction() {
            @Override
            public ClassLoader setThreadContextClassLoader(Class cl) {
                ClassLoader old = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(cl.getClassLoader());
                return old;
            }
            @Override
            public void setThreadContextClassLoader(ClassLoader cl) {
                Thread.currentThread().setContextClassLoader(cl);
            }
        };

        SetThreadContextClassLoaderAction PRIVILEGED = new SetThreadContextClassLoaderAction() {

            @Override
            public ClassLoader setThreadContextClassLoader(final Class cl) {
                return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                    public ClassLoader run() {
                        ClassLoader old = Thread.currentThread().getContextClassLoader();
                        Thread.currentThread().setContextClassLoader(cl.getClassLoader());
                        return old;
                    }
                });
            }

            @Override
            public void setThreadContextClassLoader(final ClassLoader cl) {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        Thread.currentThread().setContextClassLoader(cl);
                        return null;
                    }
                });
            }
        };
    }
}
