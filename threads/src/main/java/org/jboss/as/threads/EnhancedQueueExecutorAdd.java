/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.threads.ThreadPoolManagementUtils.EnhancedQueueThreadPoolParameters;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Adds an {@code org.jboss.threads.EnhancedQueueExecutor}.
 */
class EnhancedQueueExecutorAdd extends AbstractAddStepHandler {

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{PoolAttributeDefinitions.KEEPALIVE_TIME,
            PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.CORE_THREADS, PoolAttributeDefinitions.THREAD_FACTORY};

    static final AttributeDefinition[] RW_ATTRIBUTES = new AttributeDefinition[]{PoolAttributeDefinitions.KEEPALIVE_TIME,
            PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.CORE_THREADS};

    private final ThreadFactoryResolver threadFactoryResolver;
    private final ServiceName serviceNameBase;
    private final RuntimeCapability<Void> capability;
    private final boolean allowCoreThreadTimeout;

    EnhancedQueueExecutorAdd(ThreadFactoryResolver threadFactoryResolver, ServiceName serviceNameBase) {
        this(threadFactoryResolver, serviceNameBase, null, false);
    }

    EnhancedQueueExecutorAdd(ThreadFactoryResolver threadFactoryResolver, ServiceName serviceNameBase, RuntimeCapability<Void> capability, boolean allowCoreThreadTimeout) {
        super(ATTRIBUTES);
        this.threadFactoryResolver = threadFactoryResolver;
        this.serviceNameBase = serviceNameBase;
        this.capability = capability;
        this.allowCoreThreadTimeout = allowCoreThreadTimeout;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        final EnhancedQueueThreadPoolParameters params = ThreadPoolManagementUtils.parseEnhancedQueueThreadPoolParameters(context, operation, model);

        final EnhancedQueueExecutorService service = new EnhancedQueueExecutorService(allowCoreThreadTimeout, params.getMaxThreads(), params.getCoreThreads(), params.getKeepAliveTime());

        ThreadPoolManagementUtils.installThreadPoolService(service, params.getName(), capability, context.getCurrentAddress(),
                serviceNameBase, params.getThreadFactory(), threadFactoryResolver, service.getThreadFactoryInjector(),
                null, null, null, context.getServiceTarget());
    }

    ServiceName getServiceNameBase() {
        return serviceNameBase;
    }

    ThreadFactoryResolver getThreadFactoryResolver() {
        return threadFactoryResolver;
    }

    RuntimeCapability<Void> getCapability() {
        return capability;
    }
}
