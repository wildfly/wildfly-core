/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.util.List;

/**
 * A configuration for an existing key-store
 *
 * @author jdenise@redhat.com
 */
public class KeyStoreConfiguration extends AbstractKeyStoreConfiguration {

    private final String trustStore;

    public KeyStoreConfiguration(String trustStore, List<String> roles) {
        super(roles);
        this.trustStore = trustStore;
    }

    public String getTrustStore() {
        return trustStore;
    }

    @Override
    public String getRealmName() {
        return null;
    }
}
