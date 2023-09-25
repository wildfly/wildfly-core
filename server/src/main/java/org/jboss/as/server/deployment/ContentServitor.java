/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import org.jboss.as.repository.ContentRepository;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.vfs.VirtualFile;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
class ContentServitor implements Service<VirtualFile> {
    private final InjectedValue<ContentRepository> contentRepositoryInjectedValue = new InjectedValue<ContentRepository>();
    private final byte[] hash;

    ContentServitor(final byte[] hash) {
        assert hash != null : "hash is null";
        this.hash = hash;
    }

    static ServiceController<VirtualFile> addService(final ServiceTarget serviceTarget, final ServiceName serviceName, final byte[] hash) {
        final ContentServitor service = new ContentServitor(hash);
        return serviceTarget.addService(serviceName, service)
            .addDependency(ContentRepository.SERVICE_NAME, ContentRepository.class, service.contentRepositoryInjectedValue)
            .install();
    }

    @Override
    public void start(StartContext startContext) {
        // noop
    }

    @Override
    public void stop(StopContext stopContext) {
        // noop
    }

    @Override
    public VirtualFile getValue() throws IllegalStateException, IllegalArgumentException {
        return contentRepositoryInjectedValue.getValue().getContent(hash);
    }
}
