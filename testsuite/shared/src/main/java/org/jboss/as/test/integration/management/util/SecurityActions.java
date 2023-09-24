/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.util;

import java.security.AccessController;
import java.security.PrivilegedAction;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Package privileged actions
 *
 * @author Scott.Stark@jboss.org
 * @author Alexey Loubyansky
 */
class SecurityActions {
    private interface TCLAction {
        class UTIL {
            static TCLAction getTCLAction() {
                return WildFlySecurityManager.isChecking() ? PRIVILEGED : NON_PRIVILEGED;
            }

            public static String getSystemProperty(String name) {
                return getTCLAction().getSystemProperty(name);
            }

            public static void setSystemProperty(String name, String value) {
                getTCLAction().setSystemProperty(name, value);
            }
        }

        TCLAction NON_PRIVILEGED = new TCLAction() {

            @Override
            public String getSystemProperty(String name) {
                return System.getProperty(name);
            }

            @Override
            public void setSystemProperty(String name, String value) {
                System.setProperty(name, value);
            }
        };

        TCLAction PRIVILEGED = new TCLAction() {

            @Override
            public String getSystemProperty(final String name) {
                return (String) AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    public Object run() {
                        return System.getProperty(name);
                    }
                });
            }

            @Override
            public void setSystemProperty(final String name, final String value) {
                AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    public Object run() {
                        System.setProperty(name, value);
                        return null;
                    }
                });
            }
        };

        String getSystemProperty(String name);

        void setSystemProperty(String name, String value);
    }

    protected static String getSystemProperty(String name) {
        return TCLAction.UTIL.getSystemProperty(name);
    }

    protected static void setSystemProperty(String name, String value) {
        TCLAction.UTIL.setSystemProperty(name, value);
    }
}
