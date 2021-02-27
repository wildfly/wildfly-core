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

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;
import static org.wildfly.test.security.common.ModelNodeUtil.setIfNotNull;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;

/**
 * Elytron sasl-authentication-factory configuration.
 *
 * @author Josef Cacek
 */
public class SimpleSaslAuthenticationFactory extends AbstractConfigurableElement {

    private final List<MechanismConfiguration> mechanismConfigurations;
    private final String saslServerFactory;
    private final String securityDomain;


    private SimpleSaslAuthenticationFactory(Builder builder) {
        super(builder);
        this.mechanismConfigurations = new ArrayList<>(checkNotNullParamWithNullPointerException("builder.mechanismConfigurations", builder.mechanismConfigurations));
        this.saslServerFactory = checkNotNullParamWithNullPointerException("builder.saslServerFactory", builder.saslServerFactory);
        this.securityDomain = checkNotNullParamWithNullPointerException("builder.securityDomain", builder.securityDomain);
    }


    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode op = Util.createAddOperation(
                PathAddress.pathAddress().append("subsystem", "elytron").append("sasl-authentication-factory", name));
        setIfNotNull(op, "sasl-server-factory", saslServerFactory);
        setIfNotNull(op, "security-domain", securityDomain);
        if (!mechanismConfigurations.isEmpty()) {
            ModelNode confs = op.get("mechanism-configurations");
            for (MechanismConfiguration conf : mechanismConfigurations) {
                confs.add(conf.toModelNode());
            }
        }
        CoreUtils.applyUpdate(op, client);
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        CoreUtils.applyUpdate(Util.createRemoveOperation(
                PathAddress.pathAddress().append("subsystem", "elytron").append("sasl-authentication-factory", name)),
                client);
    }


    /**
     * Creates builder to build {@link SimpleSaslAuthenticationFactory}.
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link SimpleSaslAuthenticationFactory}.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<Builder> {
        private List<MechanismConfiguration> mechanismConfigurations = new ArrayList<>();
        private String saslServerFactory;
        private String securityDomain;

        private Builder() {
        }

        public Builder addMechanismConfiguration(MechanismConfiguration mechanismConfiguration) {
            this.mechanismConfigurations.add(mechanismConfiguration);
            return this;
        }

        public Builder withSaslServerFactory(String saslServerFactory) {
            this.saslServerFactory = saslServerFactory;
            return this;
        }

        public Builder withSecurityDomain(String securityDomain) {
            this.securityDomain = securityDomain;
            return this;
        }

        public SimpleSaslAuthenticationFactory build() {
            return new SimpleSaslAuthenticationFactory(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
