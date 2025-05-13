/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import static org.jboss.as.threads.CommonAttributes.BLOCKING_QUEUELESS_THREAD_POOL;
import static org.jboss.as.threads.CommonAttributes.QUEUELESS_THREAD_POOL;

import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.msc.service.ServiceName;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a queueless thread pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class QueuelessThreadPoolResourceDefinition extends SimpleResourceDefinition {
    private final QueuelessThreadPoolWriteAttributeHandler writeHandler;
    private final QueuelessThreadPoolMetricsHandler metricsHandler;
    private final boolean blocking;
    private final boolean registerRuntimeOnly;


    public static QueuelessThreadPoolResourceDefinition create(boolean blocking, boolean registerRuntimeOnly) {
        if (blocking) {
            return create(BLOCKING_QUEUELESS_THREAD_POOL, ThreadsServices.getThreadFactoryResolver(BLOCKING_QUEUELESS_THREAD_POOL),
                    null, ThreadsServices.EXECUTOR, registerRuntimeOnly);
        } else {
            return create(QUEUELESS_THREAD_POOL, ThreadsServices.getThreadFactoryResolver(QUEUELESS_THREAD_POOL),
                    ThreadsServices.getHandoffExecutorResolver(QUEUELESS_THREAD_POOL),
                    ThreadsServices.EXECUTOR, registerRuntimeOnly);
        }
    }

    public static QueuelessThreadPoolResourceDefinition create(boolean blocking, String type, boolean registerRuntimeOnly) {
        if (blocking) {
            return create(type, ThreadsServices.getThreadFactoryResolver(type),
                    null, ThreadsServices.EXECUTOR, registerRuntimeOnly);

        } else {
            return create(type, ThreadsServices.getThreadFactoryResolver(type),
                    ThreadsServices.getHandoffExecutorResolver(type), ThreadsServices.EXECUTOR, registerRuntimeOnly);
        }
    }

    public static QueuelessThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                               HandoffExecutorResolver handoffExecutorResolver,
                                                               ServiceName serviceNameBase, boolean registerRuntimeOnly) {
        final boolean blocking = handoffExecutorResolver == null;
        final String resolverPrefix = blocking ? BLOCKING_QUEUELESS_THREAD_POOL : QUEUELESS_THREAD_POOL;
        final RuntimeCapability<Void> capability = ThreadsServices.createCapability(type, ManagedQueueExecutorService.class);
        final QueuelessThreadPoolAdd addHandler = new QueuelessThreadPoolAdd(blocking, threadFactoryResolver, handoffExecutorResolver, serviceNameBase, capability);
        final OperationStepHandler removeHandler = new QueuelessThreadPoolRemove(addHandler);
        return new QueuelessThreadPoolResourceDefinition(blocking, registerRuntimeOnly, capability, type, serviceNameBase, resolverPrefix, addHandler, removeHandler);
    }


    private QueuelessThreadPoolResourceDefinition(boolean blocking, boolean registerRuntimeOnly, RuntimeCapability<Void> capability,
                                                  String type, ServiceName serviceNameBase, String resolverPrefix, OperationStepHandler addHandler,
                                                  OperationStepHandler removeHandler) {
        super(new SimpleResourceDefinition.Parameters(PathElement.pathElement(type),
                new ThreadPoolResourceDescriptionResolver(resolverPrefix, ThreadsExtension.RESOURCE_NAME, ThreadsExtension.class.getClassLoader()))
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setCapabilities(capability));
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.blocking = blocking;
        writeHandler = new QueuelessThreadPoolWriteAttributeHandler(blocking, capability, serviceNameBase);
        metricsHandler = new QueuelessThreadPoolMetricsHandler(capability, serviceNameBase);
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(PoolAttributeDefinitions.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        writeHandler.registerAttributes(resourceRegistration);
        if (registerRuntimeOnly) {
            metricsHandler.registerAttributes(resourceRegistration);
        }
    }

    public boolean isBlocking() {
        return blocking;
    }
}
