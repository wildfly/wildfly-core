/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import static java.lang.Runtime.getRuntime;
import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.login.Configuration;

import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.security.manager.action.AddShutdownHookAction;

/**
 * Package privileged actions
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Scott.Stark@jboss.org
 * @author Alexey Loubyansky
 */
class SecurityActions {
    static void addShutdownHook(Thread hook) {
        if (! WildFlySecurityManager.isChecking()) {
            getRuntime().addShutdownHook(hook);
        } else {
            doPrivileged(new AddShutdownHookAction(hook));
        }
    }

    static Configuration getGlobalJaasConfiguration() throws SecurityException {
        if (WildFlySecurityManager.isChecking() == false) {
            return internalGetGlobalJaasConfiguration();
        } else {

            try {
                return doPrivileged(new PrivilegedExceptionAction<Configuration>() {

                    @Override
                    public Configuration run() throws Exception {
                        return internalGetGlobalJaasConfiguration();
                    }

                });
            } catch (PrivilegedActionException e) {
                throw (SecurityException) e.getCause();
            }

        }
    }

    private static Configuration internalGetGlobalJaasConfiguration() throws SecurityException {
        return Configuration.getConfiguration();
    }

    static void setGlobalJaasConfiguration(final Configuration configuration) throws SecurityException {
        if (WildFlySecurityManager.isChecking() == false) {
            internalSetGlobalJaasConfiguration(configuration);
        } else {

            try {
                doPrivileged(new PrivilegedExceptionAction<Void>() {

                    @Override
                    public Void run() throws Exception {
                        internalSetGlobalJaasConfiguration(configuration);

                        return null;
                    }

                });
            } catch (PrivilegedActionException e) {
                throw (SecurityException) e.getCause();
            }

        }
    }

    private static void internalSetGlobalJaasConfiguration(final Configuration configuration) throws SecurityException {
        Configuration.setConfiguration(configuration);
    }
}
