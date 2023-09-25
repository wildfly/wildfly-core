/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.elytron;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import javax.net.ssl.KeyManagerFactory;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;

/**
 * Elytron trust-managers configuration implementation.
 *
 * @author Josef Cacek
 */
public class SimpleTrustManager extends AbstractConfigurableElement {

    private final String keyStore;

    private SimpleTrustManager(Builder builder) {
        super(builder);
        this.keyStore = checkNotNullParamWithNullPointerException("builder.keyStore", builder.keyStore);
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        // /subsystem=elytron/trust-manager=twoWayTM:add(key-store=twoWayTS,algorithm="SunX509")

        cli.sendLine(String.format("/subsystem=elytron/trust-manager=%s:add(key-store=\"%s\",algorithm=\"%s\")", name,
                keyStore, KeyManagerFactory.getDefaultAlgorithm()));
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/trust-manager=%s:remove()", name));
    }

    /**
     * Creates builder to build {@link SimpleTrustManager}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link SimpleTrustManager}.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<Builder> {
        private String keyStore;

        private Builder() {
        }

        public Builder withKeyStore(String keyStore) {
            this.keyStore = keyStore;
            return this;
        }

        public SimpleTrustManager build() {
            return new SimpleTrustManager(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
