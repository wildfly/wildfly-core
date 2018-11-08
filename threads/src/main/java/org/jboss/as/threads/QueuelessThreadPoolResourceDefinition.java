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

import static org.jboss.as.threads.CommonAttributes.BLOCKING_QUEUELESS_THREAD_POOL;
import static org.jboss.as.threads.CommonAttributes.QUEUELESS_THREAD_POOL;

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
 * {@link org.jboss.as.controller.ResourceDefinition} for a queueless thread pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class QueuelessThreadPoolResourceDefinition extends PersistentResourceDefinition {
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
        final RuntimeCapability<Void> capability = ThreadsServices.createCapability(type, ManagedQueuelessExecutorService.class);
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

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(writeHandler.attributes);
    }
}
