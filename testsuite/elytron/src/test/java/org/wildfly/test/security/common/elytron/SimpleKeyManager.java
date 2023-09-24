/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.elytron;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import javax.net.ssl.KeyManagerFactory;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;

/**
 * Elytron key-manager configuration implementation.
 *
 * @author Josef Cacek
 */
public class SimpleKeyManager extends AbstractConfigurableElement {

    private final String keyStore;
    private final CredentialReference credentialReference;

    private SimpleKeyManager(Builder builder) {
        super(builder);
        this.keyStore = checkNotNullParamWithNullPointerException("builder.keyStore", builder.keyStore);
        this.credentialReference = defaultIfNull(builder.credentialReference, CredentialReference.EMPTY);
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        // /subsystem=elytron/key-manager=httpsKM:add(key-store=httpsKS,algorithm="SunX509",credential-reference={clear-text=secret})

        cli.sendLine(String.format("/subsystem=elytron/key-manager=%s:add(key-store=\"%s\",algorithm=\"%s\", %s)", name,
                keyStore, KeyManagerFactory.getDefaultAlgorithm(), credentialReference.asString()));
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/key-manager=%s:remove()", name));
    }

    /**
     * Creates builder to build {@link SimpleKeyManager}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link SimpleKeyManager}.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<Builder> {
        private String keyStore;
        private CredentialReference credentialReference;

        private Builder() {
        }

        public Builder withKeyStore(String keyStore) {
            this.keyStore = keyStore;
            return this;
        }

        public Builder withCredentialReference(CredentialReference credentialReference) {
            this.credentialReference = credentialReference;
            return this;
        }

        public SimpleKeyManager build() {
            return new SimpleKeyManager(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
