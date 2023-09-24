/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import static org.jboss.as.threads.CommonAttributes.BLOCKING_BOUNDED_QUEUE_THREAD_POOL;
import static org.jboss.as.threads.CommonAttributes.BOUNDED_QUEUE_THREAD_POOL;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.msc.service.ServiceName;

import java.util.Arrays;
import java.util.Collection;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a bounded queue thread pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BoundedQueueThreadPoolResourceDefinition extends PersistentResourceDefinition {
    public static final BoundedQueueThreadPoolResourceDefinition BLOCKING = create(true, false);
    public static final BoundedQueueThreadPoolResourceDefinition NON_BLOCKING = create(false, false);
    private final BoundedQueueThreadPoolMetricsHandler metricsHandler;
    private final BoundedQueueThreadPoolWriteAttributeHandler writeHandler;
    private final boolean blocking;
    private final boolean registerRuntimeOnly;

    public static BoundedQueueThreadPoolResourceDefinition create(boolean blocking, boolean registerRuntimeOnly) {
        if (blocking) {
            return create(BLOCKING_BOUNDED_QUEUE_THREAD_POOL,
                    ThreadsServices.getThreadFactoryResolver(BLOCKING_BOUNDED_QUEUE_THREAD_POOL),
                    null, ThreadsServices.EXECUTOR, registerRuntimeOnly);
        } else {
            return create(CommonAttributes.BOUNDED_QUEUE_THREAD_POOL,
                    ThreadsServices.getThreadFactoryResolver(BOUNDED_QUEUE_THREAD_POOL),
                    ThreadsServices.getHandoffExecutorResolver(BOUNDED_QUEUE_THREAD_POOL),
                    ThreadsServices.EXECUTOR, registerRuntimeOnly);
        }
    }

    public static BoundedQueueThreadPoolResourceDefinition create(boolean blocking, String type, boolean registerRuntimeOnly) {
        if (blocking) {
            return create(type, ThreadsServices.getThreadFactoryResolver(type), null, ThreadsServices.EXECUTOR, registerRuntimeOnly);
        } else {
            return create(type, ThreadsServices.getThreadFactoryResolver(type), ThreadsServices.getHandoffExecutorResolver(type),
                    ThreadsServices.EXECUTOR, registerRuntimeOnly);
        }
    }

    public static BoundedQueueThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                                  HandoffExecutorResolver handoffExecutorResolver,
                                                                  ServiceName poolNameBase, boolean registerRuntimeOnly) {
        return create(PathElement.pathElement(type), threadFactoryResolver, handoffExecutorResolver,
                ThreadsServices.createCapability(type, ManagedQueueExecutorService.class), poolNameBase,
                registerRuntimeOnly);
    }

    public static BoundedQueueThreadPoolResourceDefinition create(PathElement path, ThreadFactoryResolver threadFactoryResolver,
                                                                  HandoffExecutorResolver handoffExecutorResolver,
                                                                  RuntimeCapability<Void> capability,
                                                                  ServiceName poolNameBase, boolean registerRuntimeOnly) {
        final boolean blocking = handoffExecutorResolver == null;
        final String resolverPrefix = blocking ? BLOCKING_BOUNDED_QUEUE_THREAD_POOL : BOUNDED_QUEUE_THREAD_POOL;
        final BoundedQueueThreadPoolAdd addHandler = new BoundedQueueThreadPoolAdd(blocking, threadFactoryResolver,
                handoffExecutorResolver, poolNameBase, capability);
        final OperationStepHandler removeHandler = new BoundedQueueThreadPoolRemove(addHandler);
        return new BoundedQueueThreadPoolResourceDefinition(blocking, registerRuntimeOnly, path, capability,
                poolNameBase, resolverPrefix, addHandler, removeHandler);
    }
    /**
     * @deprecated This class is not designed for subclassing and having this constructor be accessible is a specific workaround for WFCORE-1623 that may be reverted at any time
     */
    @Deprecated
    protected BoundedQueueThreadPoolResourceDefinition(boolean blocking, boolean registerRuntimeOnly,
                                                     String type, ServiceName serviceNameBase, String resolverPrefix, OperationStepHandler addHandler,
                                                     OperationStepHandler removeHandler) {
        this(blocking, registerRuntimeOnly, PathElement.pathElement(type), ThreadsServices.createCapability(type, ManagedQueueExecutorService.class),
                serviceNameBase, resolverPrefix, addHandler, removeHandler);
    }

    private BoundedQueueThreadPoolResourceDefinition(boolean blocking, boolean registerRuntimeOnly, PathElement path,
                                                       RuntimeCapability<Void> capability, ServiceName serviceNameBase,
                                                       String resolverPrefix, OperationStepHandler addHandler,
                                                       OperationStepHandler removeHandler) {
        super(new SimpleResourceDefinition.Parameters(path, new ThreadPoolResourceDescriptionResolver(resolverPrefix,
                                                      ThreadsExtension.RESOURCE_NAME, ThreadsExtension.class.getClassLoader()))
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setCapabilities(capability));
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.blocking = blocking;
        metricsHandler = new BoundedQueueThreadPoolMetricsHandler(capability, serviceNameBase);
        writeHandler = new BoundedQueueThreadPoolWriteAttributeHandler(blocking, capability, serviceNameBase);
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(PoolAttributeDefinitions.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        writeHandler.registerAttributes(resourceRegistration);
        if (registerRuntimeOnly) {
            metricsHandler.registerAttributes(resourceRegistration);
        }
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(writeHandler.attributes);
    }

    public boolean isBlocking() {
        return blocking;
    }

}
