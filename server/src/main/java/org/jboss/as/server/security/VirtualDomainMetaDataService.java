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

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * A {@link Service} responsible for managing the lifecycle of a single {@link SecurityDomain}.
 *
 * <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class VirtualDomainMetaDataService implements Service<VirtualDomainMetaData> {

    private volatile VirtualDomainMetaData virtualDomainMetaData;

    private final UnaryOperator<SecurityIdentity> identityOperator;
    private final VirtualDomainMetaData.AuthMethod authMethod;

    public VirtualDomainMetaDataService(final UnaryOperator<SecurityIdentity> identityOperator, final VirtualDomainMetaData.AuthMethod authMethod) {
        this.identityOperator = identityOperator;
        this.authMethod = authMethod;
    }

    @Override
    public void start(StartContext context) throws StartException {
        VirtualDomainMetaData.Builder builder = VirtualDomainMetaData.builder();

        builder.setSecurityIdentityTransformer(identityOperator);
        builder.setAuthMethod(authMethod);
        virtualDomainMetaData = builder.build();
    }

    @Override
    public void stop(StopContext context) {
       virtualDomainMetaData = null;
    }

    @Override
    public VirtualDomainMetaData getValue() throws IllegalStateException, IllegalArgumentException {
        return virtualDomainMetaData;
    }

}
