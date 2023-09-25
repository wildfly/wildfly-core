/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import org.wildfly.security.manager.action.GetSystemPropertiesAction;
import org.wildfly.security.manager.WildFlySecurityManager;

import static java.security.AccessController.doPrivileged;

/**
 * Package privileged actions
 *
 * @author Alexey Loubyansky
 */
class SecurityActions {
    static String getProperty(String name) {
        if (! WildFlySecurityManager.isChecking()) {
            return System.getProperty(name);
        } else {
            return doPrivileged(GetSystemPropertiesAction.getInstance()).getProperty(name);
        }
    }
}
