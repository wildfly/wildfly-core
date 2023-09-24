/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

/**
 * KeyManager model class.
 *
 * @author jdenise@redhat.com
 */
public class KeyManager {
    private final String name;
    private final KeyStore keyStore;
    private final boolean exists;

    public KeyManager(String name, KeyStore keyStore, boolean exists) {
        this.name = name;
        this.keyStore = keyStore;
        this.exists = exists;
    }

    public boolean exists() {
        return exists;
    }

    public String getName() {
        return name;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }
}
