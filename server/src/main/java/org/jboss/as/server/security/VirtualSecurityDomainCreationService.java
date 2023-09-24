/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.security;

import java.util.function.Consumer;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.server.SecurityDomain;

/**
 * Core {@link Service} handling virtual security domain creation.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class VirtualSecurityDomainCreationService implements Service<Void> {

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

    public void createVirtualSecurityDomain(final VirtualDomainMetaData virtualDomainMetaData, final ServiceName virtualDomainName, final ServiceTarget serviceTarget) {
        SecurityDomain.Builder virtualDomainBuilder = createVirtualSecurityDomainBuilder();
        VirtualDomainUtil.configureVirtualDomain(virtualDomainMetaData, virtualDomainBuilder);
        SecurityDomain virtualDomain = virtualDomainBuilder.build();
        virtualDomainMetaData.setSecurityDomain(virtualDomain);

        ServiceBuilder<?> serviceBuilder = serviceTarget.addService(virtualDomainName);
        Consumer<SecurityDomain> securityDomainConsumer = serviceBuilder.provides(new ServiceName[]{virtualDomainName});
        serviceBuilder.setInstance(org.jboss.msc.Service.newInstance(securityDomainConsumer, virtualDomain));
        serviceBuilder.setInitialMode(ServiceController.Mode.ON_DEMAND);
        serviceBuilder.install();
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

    public SecurityDomain.Builder createVirtualSecurityDomainBuilder() {
        return SecurityDomain.builder();
    }

}
