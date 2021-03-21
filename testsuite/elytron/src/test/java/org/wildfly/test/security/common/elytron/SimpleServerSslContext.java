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

import java.util.StringJoiner;

import org.apache.commons.lang3.StringUtils;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;

/**
 * Elytron server-ssl-context configuration implementation.
 *
 * @author Josef Cacek
 */
public class SimpleServerSslContext extends AbstractConfigurableElement {

    private final String keyManager;
    private final String trustManager;
    private final String securityDomain;
    private final String[] protocols;
    private final boolean needClientAuth;
    private final Boolean authenticationOptional;
    private final String providers;
    private final String cipherSuiteNames;

    private SimpleServerSslContext(Builder builder) {
        super(builder);
        this.keyManager = builder.keyManager;
        this.trustManager = builder.trustManager;
        this.securityDomain = builder.securityDomain;
        this.protocols = builder.protocols;
        this.needClientAuth = builder.needClientAuth;
        this.authenticationOptional = builder.authenticationOptional;
        this.providers = builder.providers;
        this.cipherSuiteNames = builder.cipherSuiteNames;
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        // /subsystem=elytron/server-ssl-context=twoWaySSC:add(key-manager=twoWayKM,protocols=["TLSv1.2"],
        // trust-manager=twoWayTM,security-domain=test,need-client-auth=true)
        StringBuilder sb = new StringBuilder("/subsystem=elytron/server-ssl-context=").append(name).append(":add(");
        if (StringUtils.isNotBlank(keyManager)) {
            sb.append("key-manager=\"").append(keyManager).append("\", ");
        }
        if (protocols != null) {
            StringJoiner joiner = new StringJoiner(", ");
            for (String s : protocols) {
                String s1 = "\"" + s + "\"";
                joiner.add(s1);
            }
            sb.append("protocols=[")
                    .append(joiner.toString()).append("], ");
        }
        if (StringUtils.isNotBlank(trustManager)) {
            sb.append("trust-manager=\"").append(trustManager).append("\", ");
        }
        if (StringUtils.isNotBlank(securityDomain)) {
            sb.append("security-domain=\"").append(securityDomain).append("\", ");
        }
        if (authenticationOptional != null) {
            sb.append("authentication-optional=").append(authenticationOptional).append(", ");
        }
        if (StringUtils.isNotBlank(providers)) {
            sb.append("providers=\"").append(providers).append("\", ");
        }
        if (StringUtils.isNotBlank(cipherSuiteNames)) {
            sb.append("cipher-suite-names=\"").append(cipherSuiteNames).append("\", ");
        }
        sb.append("need-client-auth=").append(needClientAuth).append(")");
        cli.sendLine(sb.toString());
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/server-ssl-context=%s:remove()", name));
    }

    /**
     * Creates builder to build {@link SimpleServerSslContext}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link SimpleServerSslContext}.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<Builder> {
        private String keyManager;
        private String trustManager;
        private String securityDomain;
        private String[] protocols;
        private boolean needClientAuth;
        private Boolean authenticationOptional;
        private String providers;
        private String cipherSuiteNames;

        private Builder() {
        }

        public Builder withKeyManagers(String keyManagers) {
            this.keyManager = keyManagers;
            return this;
        }

        public Builder withTrustManagers(String trustManagers) {
            this.trustManager = trustManagers;
            return this;
        }

        public Builder withSecurityDomain(String securityDomain) {
            this.securityDomain = securityDomain;
            return this;
        }

        public Builder withProtocols(String... protocols) {
            this.protocols = protocols;
            return this;
        }

        public Builder withNeedClientAuth(boolean needClientAuth) {
            this.needClientAuth = needClientAuth;
            return this;
        }

        public Builder withAuthenticationOptional(boolean authenticationOptional) {
            this.authenticationOptional = authenticationOptional;
            return this;
        }

        public Builder withProviders(String providers) {
            this.providers = providers;
            return this;
        }

        public Builder withCipherSuiteNames(String cipherSuiteNames) {
            this.cipherSuiteNames = cipherSuiteNames;
            return this;
        }

        public SimpleServerSslContext build() {
            return new SimpleServerSslContext(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
