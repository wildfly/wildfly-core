/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.elytron;


import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;

/**
 * Elytron key-store-realm configuration implementation.
 *
 * @author Ondrej Kotek
 */
public class KeyStoreRealm extends AbstractConfigurableElement {

    private final String keyStore;

    private KeyStoreRealm(Builder builder) {
        super(builder);
        this.keyStore = checkNotNullParamWithNullPointerException("builder.keyStore", builder.keyStore);
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/key-store-realm=%s:add(key-store=\"%s\")", name, keyStore));
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/key-store-realm=%s:remove()", name));
    }

    /**
     * Creates builder to build {@link KeyStoreRealm}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link KeyStoreRealm}.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<Builder> {
        private String keyStore;

        private Builder() {
        }

        public Builder withKeyStore(String keyStore) {
            this.keyStore = keyStore;
            return this;
        }

        public KeyStoreRealm build() {
            return new KeyStoreRealm(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
