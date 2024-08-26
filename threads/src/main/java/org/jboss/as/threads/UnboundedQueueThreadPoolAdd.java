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
        PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY};

    private final ThreadFactoryResolver threadFactoryResolver;
    private final ServiceName serviceNameBase;
    private final RuntimeCapability<Void> capability;
    private final boolean allowCoreThreadTimeout;

    public UnboundedQueueThreadPoolAdd(ThreadFactoryResolver threadFactoryResolver, ServiceName serviceNameBase) {
        this(threadFactoryResolver, serviceNameBase, null, false);
    }

    public UnboundedQueueThreadPoolAdd(ThreadFactoryResolver threadFactoryResolver, ServiceName serviceNameBase, RuntimeCapability<Void> capability, boolean allowCoreThreadTimeout) {
        this.threadFactoryResolver = threadFactoryResolver;
        this.serviceNameBase = serviceNameBase;
        this.capability = capability;
        this.allowCoreThreadTimeout = allowCoreThreadTimeout;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {

        final BaseThreadPoolParameters params = ThreadPoolManagementUtils.parseUnboundedQueueThreadPoolParameters(context, operation, model);

        final UnboundedQueueThreadPoolService service = new UnboundedQueueThreadPoolService(allowCoreThreadTimeout, params.getMaxThreads(), params.getKeepAliveTime());

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
