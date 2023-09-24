/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.util.List;

/**
 * A configuration for an existing key-store-realm
 *
 * @author jdenise@redhat.com
 */
public class ExistingKeyStoreConfiguration extends AbstractKeyStoreConfiguration {

    private final String realmName;

    public ExistingKeyStoreConfiguration(String realmName, List<String> roles) {
        super(roles);
        this.realmName = realmName;
    }

    @Override
    public String getRealmName() {
        return realmName;
    }
}
