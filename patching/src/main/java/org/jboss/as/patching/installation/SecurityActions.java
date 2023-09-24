/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import static java.lang.System.getProperty;
import static java.lang.System.getSecurityManager;
import static java.lang.System.getenv;
import static java.security.AccessController.doPrivileged;

import org.wildfly.security.manager.action.ReadEnvironmentPropertyAction;
import org.wildfly.security.manager.action.ReadPropertyAction;

/**
 * @author Emanuel Muckenhuber
 */
class SecurityActions {

    private SecurityActions() {
        //
    }

    static String getEnv(final String key) {
        return getSecurityManager() == null ? getenv(key) : doPrivileged(new ReadEnvironmentPropertyAction(key));
    }

    static String getSystemProperty(final String key) {
        return getSecurityManager() == null ? getProperty(key) : doPrivileged(new ReadPropertyAction(key));
    }

    static String getSystemProperty(final String key, final String defVal) {
        return getSecurityManager() == null ? getProperty(key, defVal) : doPrivileged(new ReadPropertyAction(key, defVal));
    }

}
