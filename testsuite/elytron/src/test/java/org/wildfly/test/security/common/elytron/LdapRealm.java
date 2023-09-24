/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;
import static org.wildfly.test.security.common.ModelNodeUtil.setIfNotNull;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;

/**
 * Elytron 'ldap-realm' configuration.
 *
 * @author Josef Cacek
 */
public class LdapRealm extends AbstractConfigurableElement {

    private final Boolean allowBlankPassword;
    private final String dirContext;
    private final Boolean directVerification;
    private final IdentityMapping identityMapping;

    private LdapRealm(Builder builder) {
        super(builder);
        this.allowBlankPassword = builder.allowBlankPassword;
        this.dirContext = checkNotNullParamWithNullPointerException("builder.dirContext", builder.dirContext);
        this.directVerification = builder.directVerification;
        this.identityMapping = checkNotNullParamWithNullPointerException("builder.identityMapping", builder.identityMapping);
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode op = Util
                .createAddOperation(PathAddress.pathAddress().append("subsystem", "elytron").append("ldap-realm", name));
        op.get("dir-context").set(dirContext);
        setIfNotNull(op, "allow-blank-password", allowBlankPassword);
        setIfNotNull(op, "direct-verification", directVerification);
        setIfNotNull(op, "identity-mapping", identityMapping);
        CoreUtils.applyUpdate(op, client);
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        CoreUtils.applyUpdate(
                Util.createRemoveOperation(PathAddress.pathAddress().append("subsystem", "elytron").append("ldap-realm", name)),
                client);
    }

    /**
     * Creates builder to build {@link LdapRealm}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link LdapRealm}.
     */
    public static final class Builder extends AbstractConfigurableElement.Builder<Builder> {
        private Boolean allowBlankPassword;
        private String dirContext;
        private Boolean directVerification;
        private IdentityMapping identityMapping;

        private Builder() {
        }

        public Builder withAllowBlankPassword(Boolean allowBlankPassword) {
            this.allowBlankPassword = allowBlankPassword;
            return this;
        }

        public Builder withDirContext(String dirContext) {
            this.dirContext = dirContext;
            return this;
        }

        public Builder withDirectVerification(Boolean directVerification) {
            this.directVerification = directVerification;
            return this;
        }

        public Builder withIdentityMapping(IdentityMapping identityMapping) {
            this.identityMapping = identityMapping;
            return this;
        }

        public LdapRealm build() {
            return new LdapRealm(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
