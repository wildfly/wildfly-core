/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;
import static org.wildfly.test.security.common.ModelNodeUtil.setIfNotNull;

import org.jboss.dmr.ModelNode;

/**
 * Represantation of a user-password-mapper configuration in ldap-realm/identity-mapping.
 *
 * @author Josef Cacek
 */
public class UserPasswordMapper implements ModelNodeConvertable {

    private final String from;
    private final Boolean writable;
    private final Boolean verifiable;

    private UserPasswordMapper(Builder builder) {
        this.from = checkNotNullParamWithNullPointerException("builder.from", builder.from);
        this.writable = builder.writable;
        this.verifiable = builder.verifiable;
    }

    @Override
    public ModelNode toModelNode() {
        ModelNode modelNode = new ModelNode();
        setIfNotNull(modelNode, "from", from);
        setIfNotNull(modelNode, "writable", writable);
        setIfNotNull(modelNode, "verifiable", verifiable);
        return modelNode;
    }

    /**
     * Creates builder to build {@link UserPasswordMapper}.
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link UserPasswordMapper}.
     */
    public static final class Builder {
        private String from;
        private Boolean writable;
        private Boolean verifiable;

        private Builder() {
        }

        public Builder withFrom(String from) {
            this.from = from;
            return this;
        }

        public Builder withWritable(Boolean writable) {
            this.writable = writable;
            return this;
        }

        public Builder withVerifiable(Boolean verifiable) {
            this.verifiable = verifiable;
            return this;
        }

        public UserPasswordMapper build() {
            return new UserPasswordMapper(this);
        }
    }
}
