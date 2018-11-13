/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a bounded queue thread pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BoundedQueueThreadPoolResourceDefinition extends PersistentResourceDefinition {
    @Deprecated
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
        final boolean blocking = handoffExecutorResolver == null;
        final String resolverPrefix = blocking ? BLOCKING_BOUNDED_QUEUE_THREAD_POOL : BOUNDED_QUEUE_THREAD_POOL;
        final BoundedQueueThreadPoolAdd addHandler = new BoundedQueueThreadPoolAdd(blocking, threadFactoryResolver,
                handoffExecutorResolver, poolNameBase, ThreadsServices.createCapability(type, ManagedQueueExecutorService.class));
        final OperationStepHandler removeHandler = new BoundedQueueThreadPoolRemove(addHandler);
        return new BoundedQueueThreadPoolResourceDefinition(blocking, registerRuntimeOnly, type, poolNameBase, resolverPrefix, addHandler, removeHandler);
    }

    /**
     * @param blocking
     * @param registerRuntimeOnly
     * @param type
     * @param serviceNameBase
     * @param resolverPrefix
     * @param addHandler
     * @param removeHandler
     * @deprecated This class is not designed for subclassing and having this constructor be accessible is a specific workaround for WFCORE-1623 that may be reverted at any time
     */
    @Deprecated
    protected BoundedQueueThreadPoolResourceDefinition(boolean blocking, boolean registerRuntimeOnly,
                                                     String type, ServiceName serviceNameBase, String resolverPrefix, OperationStepHandler addHandler,
                                                     OperationStepHandler removeHandler) {
        super(new SimpleResourceDefinition.Parameters(PathElement.pathElement(type),
                new ThreadPoolResourceDescriptionResolver(resolverPrefix, ThreadsExtension.RESOURCE_NAME, ThreadsExtension.class.getClassLoader()))
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setCapabilities(ThreadsServices.createCapability(type, ManagedQueueExecutorService.class)));
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.blocking = blocking;
        metricsHandler = new BoundedQueueThreadPoolMetricsHandler(serviceNameBase);
        writeHandler = new BoundedQueueThreadPoolWriteAttributeHandler(blocking, serviceNameBase);
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
