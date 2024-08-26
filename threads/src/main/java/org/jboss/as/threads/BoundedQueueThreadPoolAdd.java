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
import org.jboss.as.threads.ThreadPoolManagementUtils.BoundedThreadPoolParameters;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Adds a bounded queue thread pool.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="alex@jboss.org">Alexey Loubyansky</a>
 */
public class BoundedQueueThreadPoolAdd extends AbstractAddStepHandler {

    static final AttributeDefinition[] BLOCKING_ATTRIBUTES = new AttributeDefinition[] {PoolAttributeDefinitions.KEEPALIVE_TIME,
        PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY,
        PoolAttributeDefinitions.CORE_THREADS, PoolAttributeDefinitions.QUEUE_LENGTH,
        PoolAttributeDefinitions.ALLOW_CORE_TIMEOUT};

    static final AttributeDefinition[] NON_BLOCKING_ATTRIBUTES = new AttributeDefinition[BLOCKING_ATTRIBUTES.length + 1] ;

    static {
        System.arraycopy(BLOCKING_ATTRIBUTES, 0, NON_BLOCKING_ATTRIBUTES, 0, BLOCKING_ATTRIBUTES.length);
        NON_BLOCKING_ATTRIBUTES[NON_BLOCKING_ATTRIBUTES.length - 1] = PoolAttributeDefinitions.HANDOFF_EXECUTOR;
    }

    private final boolean blocking;
    private final ThreadFactoryResolver threadFactoryResolver;
    private final HandoffExecutorResolver handoffExecutorResolver;
    private final ServiceName serviceNameBase;
    private final RuntimeCapability<Void> capability;

    public BoundedQueueThreadPoolAdd(boolean blocking, ThreadFactoryResolver threadFactoryResolver,
                                     HandoffExecutorResolver handoffExecutorResolver, ServiceName serviceNameBase) {
        this(blocking, threadFactoryResolver, handoffExecutorResolver, serviceNameBase, null);
    }

    public BoundedQueueThreadPoolAdd(boolean blocking, ThreadFactoryResolver threadFactoryResolver,
                                     HandoffExecutorResolver handoffExecutorResolver, ServiceName serviceNameBase,
                                     RuntimeCapability<Void> capability) {
        this.blocking = blocking;
        this.threadFactoryResolver = threadFactoryResolver;
        this.handoffExecutorResolver = handoffExecutorResolver;
        this.serviceNameBase = serviceNameBase;
        this.capability= capability;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {

        final BoundedThreadPoolParameters params = ThreadPoolManagementUtils.parseBoundedThreadPoolParameters(context, operation, model, blocking);
        final BoundedQueueThreadPoolService service = new BoundedQueueThreadPoolService(
                params.getCoreThreads(),
                params.getMaxThreads(),
                params.getQueueLength(),
                blocking,
                params.getKeepAliveTime(),
                params.isAllowCoreTimeout());

        ThreadPoolManagementUtils.installThreadPoolService(service, params.getName(), capability, context.getCurrentAddress(),
                serviceNameBase, params.getThreadFactory(), threadFactoryResolver, service.getThreadFactoryInjector(),
                params.getHandoffExecutor(), handoffExecutorResolver, blocking ?  null : service.getHandoffExecutorInjector(),
                context.getServiceTarget());
    }

    boolean isBlocking() {
        return blocking;
    }

    ServiceName getServiceNameBase() {
        return serviceNameBase;
    }

    ThreadFactoryResolver getThreadFactoryResolver() {
        return threadFactoryResolver;
    }

    HandoffExecutorResolver getHandoffExecutorResolver() {
        return handoffExecutorResolver;
    }
}
