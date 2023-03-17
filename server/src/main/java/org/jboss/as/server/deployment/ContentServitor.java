/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
