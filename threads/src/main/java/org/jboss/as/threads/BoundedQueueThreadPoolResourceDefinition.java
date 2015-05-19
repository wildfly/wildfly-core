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

import java.util.Arrays;
import java.util.Collection;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.msc.service.ServiceName;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a bounded queue thread pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BoundedQueueThreadPoolResourceDefinition extends PersistentResourceDefinition {
    public static final BoundedQueueThreadPoolResourceDefinition BLOCKING = create(true);
    public static final BoundedQueueThreadPoolResourceDefinition NON_BLOCKING = create(false);
    private final BoundedQueueThreadPoolMetricsHandler metricsHandler;
    private final BoundedQueueThreadPoolWriteAttributeHandler writeHandler;
    private final boolean blocking;

    @Deprecated
    public static BoundedQueueThreadPoolResourceDefinition create(boolean blocking, boolean registerRuntimeOnly) {
        return create(blocking);
    }

    @Deprecated
    public static BoundedQueueThreadPoolResourceDefinition create(boolean blocking, String type, boolean registerRuntimeOnly) {
        return create(blocking, type);
    }

    @Deprecated
    public static BoundedQueueThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                                  HandoffExecutorResolver handoffExecutorResolver,
                                                                  ServiceName poolNameBase, boolean registerRuntimeOnly) {
        return create(type, threadFactoryResolver, handoffExecutorResolver, poolNameBase);
    }

    public static BoundedQueueThreadPoolResourceDefinition create(boolean blocking) {
        if (blocking) {
            return create(CommonAttributes.BLOCKING_BOUNDED_QUEUE_THREAD_POOL, ThreadsServices.STANDARD_THREAD_FACTORY_RESOLVER,
                    null, ThreadsServices.EXECUTOR);
        } else {
            return create(CommonAttributes.BOUNDED_QUEUE_THREAD_POOL, ThreadsServices.STANDARD_THREAD_FACTORY_RESOLVER,
                    ThreadsServices.STANDARD_HANDOFF_EXECUTOR_RESOLVER, ThreadsServices.EXECUTOR);
        }
    }

    public static BoundedQueueThreadPoolResourceDefinition create(boolean blocking, String type) {
        if (blocking) {
            return create(type, ThreadsServices.STANDARD_THREAD_FACTORY_RESOLVER, null, ThreadsServices.EXECUTOR);
        } else {
            return create(type, ThreadsServices.STANDARD_THREAD_FACTORY_RESOLVER, ThreadsServices.STANDARD_HANDOFF_EXECUTOR_RESOLVER,
                    ThreadsServices.EXECUTOR);
        }
    }

    public static BoundedQueueThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                                  HandoffExecutorResolver handoffExecutorResolver,
                                                                  ServiceName poolNameBase) {
        final boolean blocking = handoffExecutorResolver == null;
        final String resolverPrefix = blocking ? CommonAttributes.BLOCKING_BOUNDED_QUEUE_THREAD_POOL : CommonAttributes.BOUNDED_QUEUE_THREAD_POOL;
        final BoundedQueueThreadPoolAdd addHandler = new BoundedQueueThreadPoolAdd(blocking, threadFactoryResolver, handoffExecutorResolver, poolNameBase);
        final OperationStepHandler removeHandler = new BoundedQueueThreadPoolRemove(addHandler);
        return new BoundedQueueThreadPoolResourceDefinition(blocking, type, poolNameBase, resolverPrefix, addHandler, removeHandler);
    }

    private BoundedQueueThreadPoolResourceDefinition(boolean blocking, String type, ServiceName serviceNameBase, String resolverPrefix, OperationStepHandler addHandler,
                                                     OperationStepHandler removeHandler) {
        super(PathElement.pathElement(type),
                new ThreadPoolResourceDescriptionResolver(resolverPrefix, ThreadsExtension.RESOURCE_NAME, ThreadsExtension.class.getClassLoader()),
                addHandler, removeHandler);
        metricsHandler = new BoundedQueueThreadPoolMetricsHandler(serviceNameBase);
        writeHandler = new BoundedQueueThreadPoolWriteAttributeHandler(blocking, serviceNameBase);
        this.blocking = blocking;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(PoolAttributeDefinitions.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        writeHandler.registerAttributes(resourceRegistration);
        metricsHandler.registerAttributes(resourceRegistration);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(writeHandler.attributes);
    }

    public boolean isBlocking() {
        return blocking;
    }
}
