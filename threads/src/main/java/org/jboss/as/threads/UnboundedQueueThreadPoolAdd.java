/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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
package org.jboss.as.threads;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.threads.ThreadPoolManagementUtils.BaseThreadPoolParameters;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Adds an unbounded queue thread pool.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="alex@jboss.org">Alexey Loubyansky</a>
 */
public class UnboundedQueueThreadPoolAdd extends AbstractAddStepHandler {

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {PoolAttributeDefinitions.KEEPALIVE_TIME,
         PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.CORE_THREADS,
         PoolAttributeDefinitions.THREAD_FACTORY};

    static final AttributeDefinition[] RW_ATTRIBUTES = new AttributeDefinition[] {PoolAttributeDefinitions.KEEPALIVE_TIME,
        PoolAttributeDefinitions.MAX_THREADS};

    private final ThreadFactoryResolver threadFactoryResolver;
    private final ServiceName serviceNameBase;

    public UnboundedQueueThreadPoolAdd(ThreadFactoryResolver threadFactoryResolver, ServiceName serviceNameBase) {
        super(ATTRIBUTES);
        this.threadFactoryResolver = threadFactoryResolver;
        this.serviceNameBase = serviceNameBase;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {

        final BaseThreadPoolParameters params = ThreadPoolManagementUtils.parseUnboundedQueueThreadPoolParameters(context, operation, model);

        final UnboundedQueueThreadPoolService service = new UnboundedQueueThreadPoolService(params.getCoreThreads(), params.getMaxThreads(), params.getKeepAliveTime());

        ThreadPoolManagementUtils.installThreadPoolService(service, params.getName(), serviceNameBase,
                params.getThreadFactory(), threadFactoryResolver, service.getThreadFactoryInjector(),
                context.getServiceTarget());
    }

    ServiceName getServiceNameBase() {
        return serviceNameBase;
    }

    ThreadFactoryResolver getThreadFactoryResolver() {
        return threadFactoryResolver;
    }
}
