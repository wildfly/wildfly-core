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

import static org.wildfly.test.security.common.ModelNodeUtil.setIfNotNull;

import java.util.ArrayList;
import java.util.List;

import org.jboss.dmr.ModelNode;

/**
 * Object which holds single instance from mechanism-configurations list.
 *
 * @author Josef Cacek
 */
public class MechanismConfiguration extends AbstractMechanismConfiguration {

    private final String mechanismName;
    private final String hostName;
    private final String protocol;
    private final List<MechanismRealmConfiguration> mechanismRealmConfigurations;
    private final String credentialSecurityFactory;

    private MechanismConfiguration(Builder builder) {
        super(builder);
        this.mechanismName = builder.mechanismName;
        this.hostName = builder.hostName;
        this.protocol = builder.protocol;
        this.credentialSecurityFactory = builder.credentialSecurityFactory;
        this.mechanismRealmConfigurations = new ArrayList<>(builder.mechanismRealmConfigurations);
    }

    public String getMechanismName() {
        return mechanismName;
    }

    public String getHostName() {
        return hostName;
    }

    public String getProtocol() {
        return protocol;
    }

    public List<MechanismRealmConfiguration> getMechanismRealmConfigurations() {
        return mechanismRealmConfigurations;
    }

    /**
     * Creates builder to build {@link MechanismConfiguration}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ModelNode toModelNode() {
        ModelNode node = super.toModelNode();
        setIfNotNull(node, "mechanism-name", mechanismName);
        setIfNotNull(node, "host-name", hostName);
        setIfNotNull(node, "protocol", protocol);
        setIfNotNull(node, "credential-security-factory", credentialSecurityFactory);
        if (!mechanismRealmConfigurations.isEmpty()) {
            ModelNode confs = node.get("mechanism-realm-configurations");
            for (MechanismRealmConfiguration conf:mechanismRealmConfigurations) {
                confs.add(conf.toModelNode());
            }
        }
        return node;
    }

    /**
     * Builder to build {@link MechanismConfiguration}.
     */
    public static final class Builder extends AbstractMechanismConfiguration.Builder<Builder> {
        private String mechanismName;
        private String hostName;
        private String protocol;
        private String credentialSecurityFactory;
        private List<MechanismRealmConfiguration> mechanismRealmConfigurations = new ArrayList<>();

        private Builder() {
        }

        public Builder withMechanismName(String mechanismName) {
            this.mechanismName = mechanismName;
            return this;
        }

        public Builder withHostName(String hostName) {
            this.hostName = hostName;
            return this;
        }

        public Builder withProtocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder withCredentialSecurityFactory(String credentialSecurityFactory) {
            this.credentialSecurityFactory = credentialSecurityFactory;
            return this;
        }

        public Builder addMechanismRealmConfiguration(MechanismRealmConfiguration mechanismRealmConfiguration) {
            this.mechanismRealmConfigurations.add(mechanismRealmConfiguration);
            return this;
        }

        public MechanismConfiguration build() {
            return new MechanismConfiguration(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
