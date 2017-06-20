/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.test.security.common.elytron;

import java.util.Objects;

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
        this.keyStore = Objects.requireNonNull(builder.keyStore, "Key-store name has to be provided");
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
