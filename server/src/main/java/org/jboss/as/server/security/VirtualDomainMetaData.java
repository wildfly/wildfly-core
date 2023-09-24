/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.security;

import java.util.function.UnaryOperator;

import org.wildfly.common.Assert;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Metadata to be used when creating a virtual security domain.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class VirtualDomainMetaData {

    public enum AuthMethod {
        OIDC("OIDC"),
        MP_JWT("MP-JWT");

        private final String name;

        AuthMethod(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public static AuthMethod forName(final String name) {
            switch (name) {
                case "OIDC": return OIDC;
                case "MP-JWT": return MP_JWT;
                default: return null;
            }
        }
    }

    private final UnaryOperator<SecurityIdentity> securityIdentityTransformer;
    private final AuthMethod authMethod;
    private SecurityDomain securityDomain;

    VirtualDomainMetaData(Builder builder) {
        this.securityIdentityTransformer = builder.securityIdentityTransformer;
        this.authMethod = builder.authMethod;
    }

    public synchronized SecurityDomain getSecurityDomain() {
        return securityDomain;
    }

    public synchronized void setSecurityDomain(SecurityDomain securityDomain) {
        this.securityDomain = securityDomain;
    }

    public UnaryOperator<SecurityIdentity> getSecurityIdentityTransformer() {
        return securityIdentityTransformer;
    }

    public AuthMethod getAuthMethod() {
        return authMethod;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UnaryOperator<SecurityIdentity> securityIdentityTransformer;
        private AuthMethod authMethod;

        public VirtualDomainMetaData build() {
            return new VirtualDomainMetaData(this);
        }

        public void setSecurityIdentityTransformer(final UnaryOperator<SecurityIdentity> securityIdentityTransformer) {
            Assert.checkNotNullParam("securityIdentityTransformer", securityIdentityTransformer);
            this.securityIdentityTransformer = securityIdentityTransformer;
        }

        public void setAuthMethod(final AuthMethod authMethod) {
            Assert.checkNotNullParam("authMethod", authMethod);
            this.authMethod = authMethod;
        }
    }
}
