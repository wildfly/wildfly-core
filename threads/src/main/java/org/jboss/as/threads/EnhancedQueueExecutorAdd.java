/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
