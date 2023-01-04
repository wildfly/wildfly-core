/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    private final UnaryOperator<SecurityIdentity> securityIdentityTransformer;
    private SecurityDomain securityDomain;

    VirtualDomainMetaData(Builder builder) {
        this.securityIdentityTransformer = builder.securityIdentityTransformer;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UnaryOperator<SecurityIdentity> securityIdentityTransformer;

        public VirtualDomainMetaData build() {
            return new VirtualDomainMetaData(this);
        }

        public void setSecurityIdentityTransformer(final UnaryOperator<SecurityIdentity> securityIdentityTransformer) {
            Assert.checkNotNullParam("securityIdentityTransformer", securityIdentityTransformer);
            this.securityIdentityTransformer = securityIdentityTransformer;
        }
    }
}
