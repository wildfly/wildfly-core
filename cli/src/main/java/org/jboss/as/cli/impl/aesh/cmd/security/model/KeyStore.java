/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

/**
 * KeyStore model class.
 *
 * @author jdenise@redhat.com
 */
public class KeyStore {
    private final String name;
    private final String password;
    private final boolean exists;
    private final String alias;

    public KeyStore(String name, String password, String alias, boolean exists) {
        this.name = name;
        this.password = password;
        this.exists = exists;
        this.alias = alias;
    }

    public KeyStore(String name, String password, boolean exists) {
        this(name, password, null, exists);
    }

    public boolean exists() {
        return exists;
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    public String getPassword() {
        return password;
    }
}
