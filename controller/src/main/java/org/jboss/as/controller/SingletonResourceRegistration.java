/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.version.Stability;
import org.wildfly.common.Assert;

/**
 * A singleton (i.e. non-wildcard) resource registration.
 * @author Paul Ferraro
 */
public interface SingletonResourceRegistration extends ResourceRegistration {

    /**
     * Creates a new wildcard resource registration for the specified path.
     * @param path a path element
     * @return a resource registration
     */
    static SingletonResourceRegistration of(PathElement path) {
        return of(path, Stability.DEFAULT);
    }

    /**
     * Creates a new resource registration for the specified path and with the specified stability level.
     * @param path a path element
     * @param stability a stability level
     * @return a resource registration
     */
    static SingletonResourceRegistration of(PathElement path, Stability stability) {
        return new DefaultSingletonResourceRegistration(path, stability);
    }

    class DefaultSingletonResourceRegistration extends DefaultResourceRegistration implements SingletonResourceRegistration {

        DefaultSingletonResourceRegistration(PathElement path, Stability stability) {
            super(path, stability);
            Assert.assertFalse(path.isWildcard());
        }
    }
}
