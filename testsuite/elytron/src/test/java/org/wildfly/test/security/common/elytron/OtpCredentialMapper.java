/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;
import static org.wildfly.test.security.common.ModelNodeUtil.setIfNotNull;

import org.jboss.dmr.ModelNode;

/**
 * Represantation of an otp-credential-mapper configuration in ldap-realm/identity-mapping.
 *
 * @author Josef Cacek
 */
public class OtpCredentialMapper implements ModelNodeConvertable {

    private final String algorithmFrom;
    private final String hashFrom;
    private final String seedFrom;
    private final String sequenceFrom;

    private OtpCredentialMapper(Builder builder) {
        this.algorithmFrom = checkNotNullParamWithNullPointerException("builder.algorithmFrom", builder.algorithmFrom);
        this.hashFrom = checkNotNullParamWithNullPointerException("builder.hashFrom", builder.hashFrom);
        this.seedFrom = checkNotNullParamWithNullPointerException("builder.seedFrom", builder.seedFrom);
        this.sequenceFrom = checkNotNullParamWithNullPointerException("builder.sequenceFrom", builder.sequenceFrom);
    }

    @Override
    public ModelNode toModelNode() {
        ModelNode modelNode = new ModelNode();
        setIfNotNull(modelNode, "algorithm-from", algorithmFrom);
        setIfNotNull(modelNode, "hash-from", hashFrom);
        setIfNotNull(modelNode, "seed-from", seedFrom);
        setIfNotNull(modelNode, "sequence-from", sequenceFrom);
        return modelNode;
    }

    /**
     * Creates builder to build {@link OtpCredentialMapper}.
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link OtpCredentialMapper}.
     */
    public static final class Builder {
        private String algorithmFrom;
        private String hashFrom;
        private String seedFrom;
        private String sequenceFrom;

        private Builder() {
        }

        public Builder withAlgorithmFrom(String algorithmFrom) {
            this.algorithmFrom = algorithmFrom;
            return this;
        }

        public Builder withHashFrom(String hashFrom) {
            this.hashFrom = hashFrom;
            return this;
        }

        public Builder withSeedFrom(String seedFrom) {
            this.seedFrom = seedFrom;
            return this;
        }

        public Builder withSequenceFrom(String sequenceFrom) {
            this.sequenceFrom = sequenceFrom;
            return this;
        }

        public OtpCredentialMapper build() {
            return new OtpCredentialMapper(this);
        }
    }
}
